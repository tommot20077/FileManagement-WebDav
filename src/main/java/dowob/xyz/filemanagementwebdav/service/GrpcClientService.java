package dowob.xyz.filemanagementwebdav.service;

import com.google.protobuf.ByteString;
import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import dowob.xyz.filemanagementwebdav.config.properties.WebDavUploadProperties;
import dowob.xyz.filemanagementwebdav.customerenum.Operation;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.ProcessingRequest;
import dowob.xyz.filemanagementwebdav.data.ProcessingResponse;
import xyz.dowob.filemanagement.grpc.*;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.milton.common.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 客戶端服務
 * 負責與主服務通信，並處理 WebDAV 特定的邏輯轉換
 * 
 * @author yuan
 * @version 2.0
 */
@Service
public class GrpcClientService {
    private static final Logger log = LoggerFactory.getLogger(GrpcClientService.class);
    
    private final FileProcessingServiceGrpc.FileProcessingServiceBlockingStub blockingStub;
    private final FileProcessingServiceGrpc.FileProcessingServiceStub asyncStub;
    private final ManagedChannel channel;
    private final PathMappingService pathMappingService;
    private final WebDavUploadProperties uploadProperties;
    private final WebDavResourceMonitorService resourceMonitor;
    
    // Note: Constants removed - now using uploadProperties for configuration
    
    // gRPC Metadata Keys
    private static final Metadata.Key<String> CLIENT_IP_KEY = 
        Metadata.Key.of("client-ip", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_AGENT_KEY = 
        Metadata.Key.of("user-agent", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID_KEY = 
        Metadata.Key.of("request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_ID_KEY = 
        Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER);
    
    @Autowired
    public GrpcClientService(ManagedChannel channel, PathMappingService pathMappingService, 
                           WebDavUploadProperties uploadProperties, 
                           WebDavResourceMonitorService resourceMonitor) {
        this.channel = channel;
        this.blockingStub = FileProcessingServiceGrpc.newBlockingStub(channel);
        this.asyncStub = FileProcessingServiceGrpc.newStub(channel);
        this.pathMappingService = pathMappingService;
        this.uploadProperties = uploadProperties;
        this.resourceMonitor = resourceMonitor;
        
        log.info("GrpcClientService initialized with upload config: {}", uploadProperties.getConfigSummary());
    }
    
    /**
     * 處理檔案操作請求
     * 
     * @param request WebDAV 請求
     * @return 處理結果
     */
    public ProcessingResponse processFile(ProcessingRequest request) {
        try {
            // 從請求上下文獲取認證信息
            RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
            if (context == null || !context.isAuthenticated()) {
                return ProcessingResponse.builder()
                    .success(false)
                    .errorMessage("Unauthorized")
                    .build();
            }
            
            Long userId = getUserIdFromContext();
            String token = context.getAuthToken();
            
            // 將 WebDAV 路徑轉換為檔案 ID
            String webdavPath = request.getPath();
            Long fileId = pathMappingService.resolvePathToId(webdavPath);
            
            // 根據操作類型分發處理
            return switch (request.getOperation()) {
                case READ -> handleRead(fileId, userId, token);
                case PUT -> handleUpload(request, webdavPath, userId, token);
                case DELETE -> handleDelete(fileId, userId, token);
                case MKCOL -> handleCreateFolder(webdavPath, userId, token);
                case LIST -> handleListFolder(fileId, userId, token);
                case MOVE -> handleMove(fileId, request.getTargetPath(), userId, token);
                case COPY -> handleCopy(fileId, request.getTargetPath(), userId, token);
                case LOCK -> handleLock(fileId, userId, token);
                case UNLOCK -> handleUnlock(fileId, request.getLockToken(), userId, token);
                default -> ProcessingResponse.builder()
                    .success(false)
                    .errorMessage("Unsupported operation: " + request.getOperation())
                    .build();
            };
            
        } catch (Exception e) {
            log.error("Error processing file request", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Internal error: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 處理檔案上傳（支援大檔案 streaming 和秒傳）
     */
    private ProcessingResponse handleUpload(ProcessingRequest request, String webdavPath, 
                                           Long userId, String token) {
        try {
            // 解析路徑以獲取父資料夾 ID 和檔名
            Path path = Path.path(webdavPath);
            String filename = path.getName();
            String parentPath = path.getParent() != null ? path.getParent().toString() : "/";
            Long parentFolderId = pathMappingService.resolvePathToId(parentPath);
            
            if (parentFolderId == null) {
                parentFolderId = 0L; // 根目錄
            }
            
            // 檢查檔案大小，決定上傳方式
            if (request.getContentLength() != null) {
                if (uploadProperties.shouldUseSimpleUpload(request.getContentLength())) {
                    // 使用簡單上傳
                    return handleSimpleUpload(request, filename, parentFolderId, userId, token);
                } else {
                    log.debug("File size {} exceeds simple upload limit ({}), using streaming upload", 
                        request.getContentLength(), uploadProperties.getMaxSimpleUploadSize());
                }
            }
            
            // 大檔案使用 streaming 上傳
            return handleStreamingUpload(request, filename, parentFolderId, userId, token);
            
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Upload failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 簡單上傳（小檔案）
     */
    private ProcessingResponse handleSimpleUpload(ProcessingRequest request, String filename,
                                                 Long parentFolderId, Long userId, String token) 
                                                 throws IOException, NoSuchAlgorithmException, InterruptedException {
        // 雙重檢查檔案大小以防記憶體問題
        if (request.getContentLength() != null && !uploadProperties.shouldUseSimpleUpload(request.getContentLength())) {
            log.warn("File size {} exceeds simple upload limit ({}), switching to streaming upload", 
                request.getContentLength(), uploadProperties.getMaxSimpleUploadSize());
            return handleStreamingUpload(request, filename, parentFolderId, userId, token);
        }
        
        // 讀取檔案內容 - 使用 try-with-resources 確保 InputStream 正確關閉
        byte[] content;
        try (InputStream dataStream = request.getDataStream()) {
            content = dataStream.readAllBytes();
        }
        
        // 計算 MD5
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(content);
        String md5Hex = bytesToHex(md5.digest());
        
        // 調用 gRPC 簡單上傳
        UploadFileRequest uploadRequest = UploadFileRequest.newBuilder()
            .setUserId(userId)
            .setUserToken(token)
            .setFilename(filename)
            .setParentFolderId(parentFolderId)
            .setMd5(md5Hex)
            .setContent(ByteString.copyFrom(content))
            .setMimeType(request.getContentType() != null ? request.getContentType() : "application/octet-stream")
            .build();
        
        UploadFileResponse response = getContextualBlockingStub()
            .withDeadlineAfter(uploadProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
            .uploadFileSimple(uploadRequest);
        
        if (response.getSuccess()) {
            // 註冊路徑映射
            FileMetadata metadata = FileMetadata.builder()
                .id(String.valueOf(response.getFileId()))
                .name(filename)
                .path(request.getPath())
                .parentId(parentFolderId)
                .isDirectory(false)
                .build();
            
            pathMappingService.registerPath(request.getPath(), response.getFileId(), metadata, userId);
            
            return ProcessingResponse.builder()
                .success(true)
                .metadata(metadata)
                .message(response.getIsInstantUpload() ? "秒傳成功" : "上傳成功")
                .build();
        }
        
        return ProcessingResponse.builder()
            .success(false)
            .errorMessage(response.getErrorMessage())
            .build();
    }
    
    /**
     * Streaming 上傳（大檔案）
     */
    private ProcessingResponse handleStreamingUpload(ProcessingRequest request, String filename,
                                                    Long parentFolderId, Long userId, String token) 
                                                    throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<UploadProgress> finalProgress = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicLong uploadedBytes = new AtomicLong(0);
        
        // 創建 MD5 計算器
        MessageDigest md5Digest;
        try {
            md5Digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
        
        // 創建上傳請求流
        StreamObserver<FileUploadChunk> requestObserver = getContextualAsyncStub()
            .uploadFile(new StreamObserver<UploadProgress>() {
                @Override
                public void onNext(UploadProgress progress) {
                    log.debug("Upload progress: {}% - {}", progress.getPercentage(), progress.getMessage());
                    if (progress.getIsCompleted()) {
                        finalProgress.set(progress);
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    log.error("Upload error", t);
                    errorRef.set(t);
                    // gRPC 會自動清理請求觀察者，無需手動處理
                    latch.countDown();
                }
                
                @Override
                public void onCompleted() {
                    log.debug("Upload completed");
                    latch.countDown();
                }
            });
        
        try {
            // 發送元資料
            FileUploadMetadata metadata = FileUploadMetadata.newBuilder()
                .setUserId(userId)
                .setUserToken(token)
                .setFilename(filename)
                .setParentFolderId(parentFolderId)
                .setTotalSize(request.getContentLength() != null ? request.getContentLength() : 0)
                .setMimeType(request.getContentType() != null ? request.getContentType() : "application/octet-stream")
                .build();
            
            requestObserver.onNext(FileUploadChunk.newBuilder()
                .setMetadata(metadata)
                .build());
            
            // 分塊發送檔案內容 - 使用 try-with-resources 確保 InputStream 正確關閉
            try (InputStream dataStream = request.getDataStream()) {
                byte[] buffer = new byte[uploadProperties.getStreamBufferSize()];
                int bytesRead;
                long totalChunks = 0;
                
                while ((bytesRead = dataStream.read(buffer)) != -1) {
                    byte[] chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                    
                    // 更新 MD5（如果啟用校驗）
                    if (uploadProperties.getEnableMd5Verification()) {
                        md5Digest.update(chunk);
                    }
                    
                    // 發送分塊
                    requestObserver.onNext(FileUploadChunk.newBuilder()
                        .setChunkData(ByteString.copyFrom(chunk))
                        .build());
                    
                    uploadedBytes.addAndGet(bytesRead);
                    totalChunks++;
                    
                    // 限制發送速率，避免壓垮服務器
                    if (totalChunks % uploadProperties.getProgressReportInterval() == 0) {
                        Thread.sleep(10);
                        log.debug("Uploaded {} chunks, {} bytes", totalChunks, uploadedBytes.get());
                    }
                }
                
                // 發送校驗資訊（如果啟用 MD5 校驗）
                if (uploadProperties.getEnableMd5Verification()) {
                    String md5Hex = bytesToHex(md5Digest.digest());
                    requestObserver.onNext(FileUploadChunk.newBuilder()
                        .setChecksum(UploadChecksum.newBuilder()
                            .setMd5(md5Hex)
                            .setTotalChunks(totalChunks)
                            .build())
                        .build());
                }
            } // InputStream 會在此自動關閉
            
            requestObserver.onCompleted();
            
            // 等待上傳完成
            if (!latch.await(uploadProperties.getTimeoutSeconds() * 2, TimeUnit.SECONDS)) {
                throw new RuntimeException("Upload timeout after " + (uploadProperties.getTimeoutSeconds() * 2) + " seconds");
            }
            
            if (errorRef.get() != null) {
                throw new RuntimeException("Upload failed", errorRef.get());
            }
            
            UploadProgress progress = finalProgress.get();
            if (progress != null && progress.getIsCompleted()) {
                // 註冊路徑映射
                FileMetadata fileMetadata = FileMetadata.builder()
                    .id(String.valueOf(progress.getFileId()))
                    .name(filename)
                    .path(request.getPath())
                    .parentId(parentFolderId)
                    .isDirectory(false)
                    .size(uploadedBytes.get())
                    .build();
                
                pathMappingService.registerPath(request.getPath(), progress.getFileId(), fileMetadata, userId);
                
                return ProcessingResponse.builder()
                    .success(true)
                    .metadata(fileMetadata)
                    .message(progress.getIsInstantUpload() ? "秒傳成功" : "上傳成功")
                    .build();
            }
            
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Upload incomplete")
                .build();
            
        } finally {
            request.getDataStream().close();
        }
    }
    
    /**
     * 處理檔案讀取
     */
    private ProcessingResponse handleRead(Long fileId, Long userId, String token) {
        if (fileId == null) {
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("File not found")
                .build();
        }
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // 調用 gRPC streaming 下載
            GetFileRequest request = GetFileRequest.newBuilder()
                .setFileId(fileId)
                .setUserId(userId)
                .setUserToken(token)
                .build();
            
            getContextualBlockingStub()
                .getFileContent(request)
                .forEachRemaining(response -> {
                    if (response.getErrorMessage().isEmpty()) {
                        try {
                            outputStream.write(response.getContent().toByteArray());
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to write content", e);
                        }
                    }
                });
            
            return ProcessingResponse.builder()
                .success(true)
                .dataStream(new ByteArrayInputStream(outputStream.toByteArray()))
                .build();
            
        } catch (Exception e) {
            log.error("Failed to read file", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Failed to read file: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 處理檔案刪除
     */
    private ProcessingResponse handleDelete(Long fileId, Long userId, String token) {
        if (fileId == null) {
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("File not found")
                .build();
        }
        
        try {
            DeleteFileRequest request = DeleteFileRequest.newBuilder()
                .setFileId(fileId)
                .setUserId(userId)
                .setUserToken(token)
                .build();
            
            DeleteFileResponse response = getContextualBlockingStub()
                .withDeadlineAfter(uploadProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .deleteFile(request);
            
            if (response.getSuccess()) {
                // 移除路徑映射
                String path = pathMappingService.resolveIdToPath(fileId);
                if (path != null) {
                    pathMappingService.removePath(path);
                }
            }
            
            return ProcessingResponse.builder()
                .success(response.getSuccess())
                .errorMessage(!response.getErrorMessage().isEmpty() ? response.getErrorMessage() : null)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to delete file", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Failed to delete file: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 處理創建資料夾
     */
    private ProcessingResponse handleCreateFolder(String webdavPath, Long userId, String token) {
        try {
            Path path = Path.path(webdavPath);
            String folderName = path.getName();
            String parentPath = path.getParent() != null ? path.getParent().toString() : "/";
            Long parentId = pathMappingService.resolvePathToId(parentPath);
            
            if (parentId == null) {
                parentId = 0L; // 根目錄
            }
            
            CreateFolderRequest request = CreateFolderRequest.newBuilder()
                .setParentId(parentId)
                .setFolderName(folderName)
                .setUserId(userId)
                .setUserToken(token)
                .build();
            
            CreateFolderResponse response = getContextualBlockingStub()
                .withDeadlineAfter(uploadProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .createFolder(request);
            
            if (response.getSuccess()) {
                // 註冊路徑映射
                FileMetadata metadata = FileMetadata.builder()
                    .id(String.valueOf(response.getFolderId()))
                    .name(folderName)
                    .path(webdavPath)
                    .parentId(parentId)
                    .isDirectory(true)
                    .build();
                
                pathMappingService.registerPath(webdavPath, response.getFolderId(), metadata, userId);
                
                return ProcessingResponse.builder()
                    .success(true)
                    .metadata(metadata)
                    .build();
            }
            
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage(response.getErrorMessage())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to create folder", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Failed to create folder: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 處理列出資料夾內容
     */
    private ProcessingResponse handleListFolder(Long folderId, Long userId, String token) {
        if (folderId == null) {
            folderId = 0L; // 根目錄
        }
        
        try {
            ListFolderRequest request = ListFolderRequest.newBuilder()
                .setFolderId(folderId)
                .setUserId(userId)
                .setUserToken(token)
                .build();
            
            ListFolderResponse response = getContextualBlockingStub()
                .withDeadlineAfter(uploadProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .listFolder(request);
            
            if (response.getSuccess()) {
                List<FileMetadata> children = new ArrayList<>();
                for (FileInfo info : response.getFilesList()) {
                    children.add(convertToFileMetadata(info));
                }
                
                // 處理重複檔名
                children = pathMappingService.processFilesInFolder(folderId, children, userId);
                
                return ProcessingResponse.builder()
                    .success(true)
                    .children(children)
                    .build();
            }
            
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage(response.getErrorMessage())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to list folder", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Failed to list folder: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 處理移動檔案
     */
    private ProcessingResponse handleMove(Long fileId, String targetPath, Long userId, String token) {
        // TODO: 實現移動邏輯
        return ProcessingResponse.builder()
            .success(false)
            .errorMessage("Move operation not yet implemented")
            .build();
    }
    
    /**
     * 處理複製檔案
     */
    private ProcessingResponse handleCopy(Long fileId, String targetPath, Long userId, String token) {
        // TODO: 實現複製邏輯
        return ProcessingResponse.builder()
            .success(false)
            .errorMessage("Copy operation not yet implemented")
            .build();
    }
    
    /**
     * 處理鎖定檔案
     */
    private ProcessingResponse handleLock(Long fileId, Long userId, String token) {
        if (fileId == null) {
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("File not found")
                .build();
        }
        
        try {
            LockFileRequest request = LockFileRequest.newBuilder()
                .setFileId(fileId)
                .setUserId(userId)
                .setUserToken(token)
                .setTimeoutSeconds(600) // 10 分鐘
                .build();
            
            LockFileResponse response = getContextualBlockingStub()
                .withDeadlineAfter(uploadProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .lockFile(request);
            
            return ProcessingResponse.builder()
                .success(response.getSuccess())
                .lockToken(response.getLockToken())
                .errorMessage(!response.getErrorMessage().isEmpty() ? response.getErrorMessage() : null)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to lock file", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Failed to lock file: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 處理解鎖檔案
     */
    private ProcessingResponse handleUnlock(Long fileId, String lockToken, Long userId, String token) {
        if (fileId == null) {
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("File not found")
                .build();
        }
        
        try {
            UnlockFileRequest request = UnlockFileRequest.newBuilder()
                .setFileId(fileId)
                .setLockToken(lockToken != null ? lockToken : "")
                .setUserId(userId)
                .setUserToken(token)
                .build();
            
            UnlockFileResponse response = getContextualBlockingStub()
                .withDeadlineAfter(uploadProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .unlockFile(request);
            
            return ProcessingResponse.builder()
                .success(response.getSuccess())
                .errorMessage(!response.getErrorMessage().isEmpty() ? response.getErrorMessage() : null)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to unlock file", e);
            return ProcessingResponse.builder()
                .success(false)
                .errorMessage("Failed to unlock file: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 執行用戶身份驗證
     */
    public AuthenticationResponse authenticate(String username, String password) {
        try {
            AuthenticationRequest request = AuthenticationRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();
            
            return getContextualBlockingStub()
                .withDeadlineAfter(uploadProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .authenticateUser(request);
            
        } catch (StatusRuntimeException e) {
            log.error("Failed to authenticate user: {}", username, e);
            return AuthenticationResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("Authentication failed: " + e.getStatus().getDescription())
                .build();
        }
    }
    
    // ==================== 輔助方法 ====================
    
    /**
     * 創建包含請求上下文信息的 gRPC Metadata
     */
    private Metadata createContextMetadata() {
        Metadata metadata = new Metadata();
        
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        if (context != null) {
            if (context.getClientIp() != null) {
                metadata.put(CLIENT_IP_KEY, context.getClientIp());
            }
            if (context.getUserAgent() != null) {
                metadata.put(USER_AGENT_KEY, context.getUserAgent());
            }
            if (context.getRequestId() != null) {
                metadata.put(REQUEST_ID_KEY, context.getRequestId());
            }
            if (context.isAuthenticated() && context.getUserId() != null) {
                metadata.put(USER_ID_KEY, context.getUserId());
            }
        }
        
        return metadata;
    }
    
    /**
     * 創建帶有上下文信息的 blocking stub
     */
    private FileProcessingServiceGrpc.FileProcessingServiceBlockingStub getContextualBlockingStub() {
        Metadata metadata = createContextMetadata();
        Channel interceptedChannel = ClientInterceptors.intercept(channel, 
            MetadataUtils.newAttachHeadersInterceptor(metadata));
        return FileProcessingServiceGrpc.newBlockingStub(interceptedChannel);
    }
    
    /**
     * 創建帶有上下文信息的 async stub
     */
    private FileProcessingServiceGrpc.FileProcessingServiceStub getContextualAsyncStub() {
        Metadata metadata = createContextMetadata();
        Channel interceptedChannel = ClientInterceptors.intercept(channel, 
            MetadataUtils.newAttachHeadersInterceptor(metadata));
        return FileProcessingServiceGrpc.newStub(interceptedChannel);
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
     * 轉換 FileInfo 為 FileMetadata
     */
    private FileMetadata convertToFileMetadata(FileInfo info) {
        return FileMetadata.builder()
            .id(String.valueOf(info.getId()))
            .name(info.getName())
            .size(info.getSize())
            .isDirectory(info.getIsDirectory())
            .contentType(info.getMimeType())
            .parentId(info.getParentId())
            .createTimestamp(info.getCreatedTime())
            .modifiedTimestamp(info.getModifiedTime())
            .createDate(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(info.getCreatedTime()), 
                ZoneId.systemDefault()))
            .modifiedDate(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(info.getModifiedTime()), 
                ZoneId.systemDefault()))
            .locked(info.getLocked())
            .lockToken(info.getLockToken())
            .build();
    }
    
    /**
     * 將 byte 陣列轉換為 hex 字串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}