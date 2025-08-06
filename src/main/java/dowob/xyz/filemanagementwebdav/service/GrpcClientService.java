package dowob.xyz.filemanagementwebdav.service;

import dowob.xyz.filemanagementwebdav.component.factory.mapper.WebDavToGrpcMapper;
import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import dowob.xyz.filemanagementwebdav.customerenum.Operation;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.ProcessingRequest;
import dowob.xyz.filemanagementwebdav.data.ProcessingResponse;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingProto;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingServiceGrpc;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName GrpcClientService
 * @create 2025/6/9
 * @Version 1.0
 **/
@Service
public class GrpcClientService {
    private final dowob.xyz.filemanagementwebdav.grpc.FileProcessingServiceGrpc.FileProcessingServiceBlockingStub blockingStub;

    private final dowob.xyz.filemanagementwebdav.grpc.FileProcessingServiceGrpc.FileProcessingServiceStub asyncStub;

    private final WebDavToGrpcMapper mapper;

    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks

    private static final int TIMEOUT_SECONDS = 30;

    private static final Logger log = LoggerFactory.getLogger(GrpcClientService.class);
    
    // gRPC Metadata Keys
    private static final Metadata.Key<String> CLIENT_IP_KEY = 
        Metadata.Key.of("client-ip", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_AGENT_KEY = 
        Metadata.Key.of("user-agent", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID_KEY = 
        Metadata.Key.of("request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_ID_KEY = 
        Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER);


    private final ManagedChannel channel;
    
    private final PathMappingService pathMappingService;
    
    @Autowired
    public GrpcClientService(ManagedChannel channel, WebDavToGrpcMapper mapper, 
                           PathMappingService pathMappingService) {
        this.channel = channel;
        this.blockingStub = dowob.xyz.filemanagementwebdav.grpc.FileProcessingServiceGrpc.newBlockingStub(channel);
        this.asyncStub = dowob.xyz.filemanagementwebdav.grpc.FileProcessingServiceGrpc.newStub(channel);
        this.mapper = mapper;
        this.pathMappingService = pathMappingService;
    }
    
    /**
     * 創建包含請求上下文信息的 gRPC Metadata
     * 
     * @return 包含上下文信息的 Metadata
     */
    private Metadata createContextMetadata() {
        Metadata metadata = new Metadata();
        
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        if (context != null) {
            // 添加客戶端 IP
            if (context.getClientIp() != null) {
                metadata.put(CLIENT_IP_KEY, context.getClientIp());
            }
            
            // 添加 User-Agent
            if (context.getUserAgent() != null) {
                metadata.put(USER_AGENT_KEY, context.getUserAgent());
            }
            
            // 添加請求 ID
            if (context.getRequestId() != null) {
                metadata.put(REQUEST_ID_KEY, context.getRequestId());
            }
            
            // 添加用戶 ID（如果已認證）
            if (context.isAuthenticated() && context.getUserId() != null) {
                metadata.put(USER_ID_KEY, context.getUserId());
            }
        }
        
        return metadata;
    }
    
    /**
     * 創建帶有上下文信息的 blocking stub
     * 
     * @return 帶有上下文的 blocking stub
     */
    private FileProcessingServiceGrpc.FileProcessingServiceBlockingStub getContextualBlockingStub() {
        Metadata metadata = createContextMetadata();
        Channel interceptedChannel = ClientInterceptors.intercept(channel, 
            MetadataUtils.newAttachHeadersInterceptor(metadata));
        return FileProcessingServiceGrpc.newBlockingStub(interceptedChannel);
    }
    
    /**
     * 創建帶有上下文信息的 async stub
     * 
     * @return 帶有上下文的 async stub
     */
    private FileProcessingServiceGrpc.FileProcessingServiceStub getContextualAsyncStub() {
        Metadata metadata = createContextMetadata();
        Channel interceptedChannel = ClientInterceptors.intercept(channel, 
            MetadataUtils.newAttachHeadersInterceptor(metadata));
        return FileProcessingServiceGrpc.newStub(interceptedChannel);
    }


    public ProcessingResponse processFile(ProcessingRequest request) {
        try {
            // 轉換 WebDAV 路徑為檔案 ID
            String originalPath = request.getPath();
            Long fileId = pathMappingService.resolvePathToId(originalPath);
            
            if (fileId == null && !request.getOperation().equals(Operation.MKCOL) 
                    && !request.getOperation().equals(Operation.PUT)) {
                // 如果不是創建操作且找不到路徑，返回 404
                return ProcessingResponse.builder()
                        .success(false)
                        .errorMessage("File not found: " + originalPath)
                        .build();
            }
            
            // 更新請求路徑為檔案 ID（主服務使用 ID）
            if (fileId != null) {
                request.setPath(fileId.toString());
            }
            
            // For large file uploads, use streaming
            if (request
                    .getOperation()
                    .equals(Operation.PUT) && request.getDataStream() != null && request.getContentLength() != null && request.getContentLength() > CHUNK_SIZE) {
                return uploadLargeFile(request);
            }

            // For downloads, use streaming
            if (request.getOperation().equals(Operation.READ) && request.getRange() == null) {
                return downloadFile(request);
            }

            // For other operations, use unary RPC
            FileProcessingProto.FileProcessingRequest grpcRequest = mapper.toGrpcRequest(request);
            FileProcessingProto.FileProcessingResponse grpcResponse = getContextualBlockingStub()
                    .withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .processFile(grpcRequest);

            ProcessingResponse response = mapper.fromGrpcResponse(grpcResponse);
            
            // 如果是 LIST 操作，處理重複檔名
            if (request.getOperation().equals(Operation.LIST) && response.isSuccess() 
                    && response.getChildren() != null) {
                Long userId = getUserIdFromContext();
                response.setChildren(pathMappingService.processFilesInFolder(
                        fileId, response.getChildren(), userId));
            }
            
            // 恢復原始路徑
            request.setPath(originalPath);
            
            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed: {}", e.getStatus(), e);
            return ProcessingResponse.builder().success(false).errorMessage(e.getStatus().getDescription()).build();
        } catch (Exception e) {
            log.error("Unexpected error during gRPC call", e);
            return ProcessingResponse.builder().success(false).errorMessage("Internal error: " + e.getMessage()).build();
        }
    }


    public FileMetadata getFileMetadata(Path path) {
        try {
            FileProcessingProto.GetFileMetadataRequest request = FileProcessingProto.GetFileMetadataRequest
                    .newBuilder()
                    .setPath(path.toString())
                    .build();

            FileProcessingProto.FileMetadataResponse response = getContextualBlockingStub()
                    .withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getFileMetadata(request);

            if (response.getExists()) {
                return mapper.fromGrpcMetadata(response.getMetadata());
            }

            return null;

        } catch (StatusRuntimeException e) {
            log.error("Failed to get file metadata: {}", e.getStatus(), e);
            return null;
        }
    }


    private ProcessingResponse uploadLargeFile(ProcessingRequest request) throws IOException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FileProcessingProto.UploadFileResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        StreamObserver<FileProcessingProto.UploadFileRequest> requestObserver = getContextualAsyncStub().uploadFile(new StreamObserver<FileProcessingProto.UploadFileResponse>() {
            @Override
            public void onNext(FileProcessingProto.UploadFileResponse response) {
                responseRef.set(response);
            }


            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }


            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        try {
            // Send metadata first
            FileProcessingProto.FileUploadMetadata metadata = FileProcessingProto.FileUploadMetadata
                    .newBuilder()
                    .setPath(request.getPath())
                    .setContentType(request.getContentType())
                    .setTotalSize(request.getContentLength())
                    .build();

            FileProcessingProto.UploadFileRequest metadataRequest = FileProcessingProto.UploadFileRequest.newBuilder().setMetadata(metadata).build();

            requestObserver.onNext(metadataRequest);

            // Stream file chunks
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;

            while ((bytesRead = request.getDataStream().read(buffer)) != -1) {
                FileProcessingProto.UploadFileRequest chunkRequest = FileProcessingProto.UploadFileRequest
                        .newBuilder()
                        .setChunkData(com.google.protobuf.ByteString.copyFrom(buffer, 0, bytesRead))
                        .build();

                requestObserver.onNext(chunkRequest);
            }

            requestObserver.onCompleted();

            // Wait for response
            if (!latch.await(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)) {
                throw new RuntimeException("Upload timeout");
            }

            if (errorRef.get() != null) {
                throw new RuntimeException("Upload failed", errorRef.get());
            }

            FileProcessingProto.UploadFileResponse response = responseRef.get();
            return ProcessingResponse
                    .builder()
                    .success(response.getSuccess())
                    .errorMessage(response.getErrorMessage())
                    .metadata(mapper.fromGrpcMetadata(response.getMetadata()))
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Upload interrupted", e);
        } finally {
            request.getDataStream().close();
        }
    }


    private ProcessingResponse downloadFile(ProcessingRequest request) {
        try {
            FileProcessingProto.DownloadFileRequest downloadRequest = FileProcessingProto.DownloadFileRequest
                    .newBuilder()
                    .setPath(request.getPath())
                    .build();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            getContextualBlockingStub().downloadFile(downloadRequest).forEachRemaining(response -> {
                if (response.hasChunkData()) {
                    try {
                        outputStream.write(response.getChunkData().toByteArray());
                    } catch (IOException e) {
                        errorRef.set(e);
                    }
                }
            });

            if (errorRef.get() != null) {
                throw new RuntimeException("Download failed", errorRef.get());
            }

            return ProcessingResponse.builder().success(true).dataStream(new ByteArrayInputStream(outputStream.toByteArray())).build();

        } catch (StatusRuntimeException e) {
            log.error("Download failed: {}", e.getStatus(), e);
            return ProcessingResponse.builder().success(false).errorMessage(e.getStatus().getDescription()).build();
        }
    }


    /**
     * 執行用戶身份驗證
     *
     * @param username  用戶名
     * @param password  密碼
     * @param clientIp  客戶端IP（可選）
     * @param userAgent 用戶代理（可選）
     *
     * @return 身份驗證響應
     */
    public FileProcessingProto.AuthenticateResponse authenticate(String username, String password, String clientIp, String userAgent) {
        try {
            // 構建請求
            FileProcessingProto.AuthenticateRequest.Builder requestBuilder = FileProcessingProto.AuthenticateRequest
                    .newBuilder()
                    .setUsername(username)
                    .setPassword(password);

            // 添加可選參數
            if (clientIp != null && !clientIp.isEmpty()) {
                requestBuilder.setClientIp(clientIp);
            }
            if (userAgent != null && !userAgent.isEmpty()) {
                requestBuilder.setUserAgent(userAgent);
            }

            FileProcessingProto.AuthenticateRequest request = requestBuilder.build();

            // 調用 gRPC 服務，設置超時
            return getContextualBlockingStub().withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS).authenticate(request);

        } catch (StatusRuntimeException e) {
            log.error("Failed to authenticate user: {}, status: {}", username, e.getStatus(), e);

            // 返回失敗響應
            return FileProcessingProto.AuthenticateResponse.newBuilder().setSuccess(false).setErrorMessage(handleAuthenticationError(e)).build();
        } catch (Exception e) {
            log.error("Unexpected error during authentication", e);

            return FileProcessingProto.AuthenticateResponse.newBuilder().setSuccess(false).setErrorMessage("內部錯誤：" + e.getMessage()).build();
        }
    }
    
    /**
     * 檢查 JWT 撤銷狀態（暫時返回模擬響應，等待 proto 文件更新）
     *
     * @param jwtToken JWT token
     * @param tokenId token ID（可選）
     * @param userId 用戶 ID（可選）
     * @return JWT 撤銷檢查結果
     */
    public JwtRevocationCheckResult checkJwtRevocation(String jwtToken, String tokenId, String userId) {
        // TODO: 待 proto 文件添加 JWT 撤銷檢查方法後實現
        log.debug("JWT revocation check called for token (mock implementation)");
        
        // 暫時返回 "未撤銷" 的結果，實際實現需要調用 gRPC 服務
        return new JwtRevocationCheckResult(true, false, "Token is valid (mock response)");
    }
    
    /**
     * JWT 撤銷檢查結果類（臨時）
     */
    public static class JwtRevocationCheckResult {
        private final boolean success;
        private final boolean revoked;
        private final String message;
        
        public JwtRevocationCheckResult(boolean success, boolean revoked, String message) {
            this.success = success;
            this.revoked = revoked;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public boolean isRevoked() { return revoked; }
        public String getMessage() { return message; }
    }


    /**
     * 處理身份驗證錯誤
     *
     * @param e gRPC 異常
     *
     * @return 用戶友好的錯誤消息
     */
    private String handleAuthenticationError(StatusRuntimeException e) {
        return switch (e.getStatus().getCode()) {
            case UNAVAILABLE -> "認證服務暫時不可用，請稍後再試";
            case DEADLINE_EXCEEDED -> "認證請求超時，請檢查網絡連接";
            case UNAUTHENTICATED -> "用戶名或密碼錯誤";
            case PERMISSION_DENIED -> "權限不足";
            case INVALID_ARGUMENT -> "請求參數無效";
            default -> "認證失敗：" + e.getStatus().getDescription();
        };
    }
    
    /**
     * 處理 JWT 撤銷檢查錯誤
     *
     * @param e gRPC 異常
     *
     * @return 用戶友好的錯誤消息
     */
    private String handleRevocationCheckError(StatusRuntimeException e) {
        return switch (e.getStatus().getCode()) {
            case UNAVAILABLE -> "撤銷檢查服務暫時不可用，請稍後再試";
            case DEADLINE_EXCEEDED -> "撤銷檢查請求超時，請檢查網絡連接";
            case NOT_FOUND -> "Token 不存在或已過期";
            case INVALID_ARGUMENT -> "請求參數無效";
            default -> "撤銷檢查失敗：" + e.getStatus().getDescription();
        };
    }
    
    /**
     * 從請求上下文獲取用戶 ID
     *
     * @return 用戶 ID，如果未認證則返回 null
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