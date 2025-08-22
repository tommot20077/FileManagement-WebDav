package dowob.xyz.filemanagementwebdav.service;

import dowob.xyz.filemanagementwebdav.component.path.WebDavPathConverter;
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
    private final WebDavPathConverter pathConverter;
    
    /**
     * 處理檔案操作請求
     * 將 WebDAV 路徑轉換為內部路徑後處理
     */
    public ProcessingResponse processFile(ProcessingRequest request) {
        Operation operation = request.getOperation();
        String originalPath = request.getPath();
        
        // 從上下文獲取用戶 ID
        String userId = getUserIdString();
        if (userId == null) {
            log.error("No authenticated user in context for request");
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("No authenticated user")
                .build();
        }
        
        // 轉換路徑：/dav/path -> /userId/path
        String internalPath = originalPath;
        if (pathConverter.isWebDavPath(originalPath)) {
            internalPath = pathConverter.toInternalPath(originalPath, userId);
            log.debug("Converted path: {} -> {}", originalPath, internalPath);
            
            // 設置內部路徑到請求中
            request.setPath(internalPath);
        }
        
        // 如果有目標路徑也需要轉換（MOVE、COPY 操作）
        String originalTargetPath = request.getTargetPath();
        if (originalTargetPath != null && pathConverter.isWebDavPath(originalTargetPath)) {
            String internalTargetPath = pathConverter.toInternalPath(originalTargetPath, userId);
            request.setTargetPath(internalTargetPath);
            log.debug("Converted target path: {} -> {}", originalTargetPath, internalTargetPath);
        }
        
        log.debug("Processing {} operation for internal path: {}", operation, internalPath);
        
        // 呼叫 gRPC 服務（使用內部路徑）
        ProcessingResponse response = grpcClientService.processFile(request);
        
        // 根據操作類型更新路徑映射
        if (response.isSuccess()) {
            Long userIdLong = getUserIdFromContext();
            
            switch (operation) {
                case MKCOL, PUT -> {
                    // 新建檔案或資料夾，註冊路徑映射
                    if (response.getMetadata() != null) {
                        pathMappingService.registerPath(
                            originalPath, 
                            response.getMetadata().getId(), 
                            response.getMetadata(), 
                            userIdLong
                        );
                        log.debug("Registered new path mapping: {} -> {}", 
                            originalPath, response.getMetadata().getId());
                    }
                }
                case DELETE -> {
                    // 刪除檔案，移除路徑映射
                    pathMappingService.removePath(originalPath);
                    log.debug("Removed path mapping for: {}", originalPath);
                }
                case MOVE -> {
                    // 移動檔案，更新路徑映射
                    String targetPath = originalTargetPath != null ? originalTargetPath : request.getTargetPath();
                    if (targetPath != null && response.getMetadata() != null) {
                        pathMappingService.updatePath(
                            originalPath, 
                            targetPath, 
                            response.getMetadata().getId()
                        );
                        log.debug("Updated path mapping: {} -> {}", originalPath, targetPath);
                    }
                }
                case COPY -> {
                    // 複製檔案，註冊新路徑映射
                    String targetPath = originalTargetPath != null ? originalTargetPath : request.getTargetPath();
                    if (targetPath != null && response.getMetadata() != null) {
                        pathMappingService.registerPath(
                            targetPath,
                            response.getMetadata().getId(),
                            response.getMetadata(),
                            userIdLong
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
        ProcessingRequest request = ProcessingRequest.builder()
            .path(pathStr)
            .operation(Operation.LIST)
            .build();
        
        // 路徑已經是內部路徑（從 WebDavResourceFactory 轉換過）
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
    
    /**
     * 從請求上下文獲取用戶 ID 字符串
     */
    private String getUserIdString() {
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        if (context != null && context.isAuthenticated()) {
            return context.getUserId();
        }
        return null;
    }
}
