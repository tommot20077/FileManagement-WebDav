package dowob.xyz.filemanagementwebdav.service;

import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import dowob.xyz.filemanagementwebdav.customerenum.Operation;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.ProcessingRequest;
import dowob.xyz.filemanagementwebdav.data.ProcessingResponse;
import io.milton.common.Path;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 檔案處理服務，整合路徑映射和 gRPC 通訊
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName FileProcessingService
 * @create 2025/6/9
 * @Version 1.0
 **/
@Service
@RequiredArgsConstructor
public class FileProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(FileProcessingService.class);
    
    private final GrpcClientService grpcClientService;
    private final PathMappingService pathMappingService;
    
    /**
     * 處理檔案操作請求
     * 路徑映射已在 GrpcClientService 中處理
     */
    public ProcessingResponse processFile(ProcessingRequest request) {
        Operation operation = request.getOperation();
        String path = request.getPath();
        
        log.debug("Processing {} operation for path: {}", operation, path);
        
        // 呼叫 gRPC 服務（路徑映射在 GrpcClientService 中處理）
        ProcessingResponse response = grpcClientService.processFile(request);
        
        // 根據操作類型更新路徑映射
        if (response.isSuccess()) {
            Long userId = getUserIdFromContext();
            
            switch (operation) {
                case MKCOL, PUT -> {
                    // 新建檔案或資料夾，註冊路徑映射
                    if (response.getMetadata() != null) {
                        pathMappingService.registerPath(
                            path, 
                            response.getMetadata().getId(), 
                            response.getMetadata(), 
                            userId
                        );
                        log.debug("Registered new path mapping: {} -> {}", 
                            path, response.getMetadata().getId());
                    }
                }
                case DELETE -> {
                    // 刪除檔案，移除路徑映射
                    pathMappingService.removePath(path);
                    log.debug("Removed path mapping for: {}", path);
                }
                case MOVE -> {
                    // 移動檔案，更新路徑映射
                    String targetPath = request.getTargetPath();
                    if (targetPath != null && response.getMetadata() != null) {
                        pathMappingService.updatePath(
                            path, 
                            targetPath, 
                            response.getMetadata().getId()
                        );
                        log.debug("Updated path mapping: {} -> {}", path, targetPath);
                    }
                }
                case COPY -> {
                    // 複製檔案，註冊新路徑映射
                    String targetPath = request.getTargetPath();
                    if (targetPath != null && response.getMetadata() != null) {
                        pathMappingService.registerPath(
                            targetPath,
                            response.getMetadata().getId(),
                            response.getMetadata(),
                            userId
                        );
                        log.debug("Registered copy path mapping: {}", targetPath);
                    }
                }
                default -> {
                    // LIST, READ 等操作不需要更新映射
                }
            }
        }
        
        return response;
    }

    /**
     * 獲取檔案元資料
     */
    public FileMetadata getFileMetadata(Path path) {
        String pathStr = path.toString();
        
        // 先嘗試從快取獲取
        Long fileId = pathMappingService.resolvePathToId(pathStr);
        if (fileId != null) {
            log.trace("Found cached file ID for path: {} -> {}", pathStr, fileId);
        }
        
        // 從 gRPC 服務獲取元資料
        // getFileMetadata 方法已經不存在，需要使用 processFile 
        ProcessingRequest request = ProcessingRequest.builder()
            .path(pathStr)
            .operation(Operation.LIST)
            .build();
        ProcessingResponse response = grpcClientService.processFile(request);
        FileMetadata metadata = response.isSuccess() ? response.getMetadata() : null;
        
        // 更新快取
        if (metadata != null && fileId == null) {
            Long userId = getUserIdFromContext();
            pathMappingService.registerPath(pathStr, metadata.getId(), metadata, userId);
        }
        
        return metadata;
    }
    
    /**
     * 從請求上下文獲取用戶 ID
     */
    private Long getUserIdFromContext() {
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        if (context != null && context.isAuthenticated() && context.getUserId() != null) {
            try {
                return Long.parseLong(context.getUserId());
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID format: {}", context.getUserId());
            }
        }
        return null;
    }
}
