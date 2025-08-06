package dowob.xyz.filemanagementwebdav.component.security;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * gRPC 安全攔截器
 * <p>
 * 實現 gRPC 請求的安全檢查，複用統一安全服務的邏輯。
 * 提供與 HTTP Filter 一致的安全保護機制。
 *
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName GrpcSecurityInterceptor
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcSecurityInterceptor implements ServerInterceptor {

    private final CommonSecurityService securityService;

    // gRPC 元數據鍵
    private static final Metadata.Key<String> CLIENT_IP_KEY = Metadata.Key.of("client-ip", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> USER_AGENT_KEY = Metadata.Key.of("user-agent", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> USERNAME_KEY = Metadata.Key.of("username", Metadata.ASCII_STRING_MARSHALLER);


    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        try {
            // 創建請求上下文
            CommonSecurityService.RequestContext context = createRequestContext(call, headers);

            // 執行安全檢查
            CommonSecurityService.SecurityCheckResult result = securityService.performSecurityCheck(context);

            if (!result.isAllowed()) {
                // 安全檢查失敗，拒絕請求
                handleSecurityViolation(call, result);
                return new ServerCall.Listener<ReqT>() {
                }; // 返回空監聽器
            }

            // 安全檢查通過，繼續處理請求
            log.debug("gRPC security check passed for method: {}", call.getMethodDescriptor().getFullMethodName());

            return next.startCall(call, headers);

        } catch (Exception e) {
            log.error("Error in GrpcSecurityInterceptor", e);

            // 記錄安全事件
            try {
                CommonSecurityService.RequestContext context = createRequestContext(call, headers);
                securityService.logSecurityEvent(context, "GRPC_SECURITY_ERROR", e.getMessage());
            } catch (Exception auditError) {
                log.error("Error logging security event", auditError);
            }

            // 安全優先：發生錯誤時拒絕請求
            call.close(Status.INTERNAL.withDescription("安全檢查失敗"), new Metadata());
            return new ServerCall.Listener<ReqT>() {
            };
        }
    }


    /**
     * 創建請求上下文
     */
    private CommonSecurityService.RequestContext createRequestContext(ServerCall<?, ?> call, Metadata headers) {

        // 從 gRPC 元數據中提取信息
        String clientIp = headers.get(CLIENT_IP_KEY);
        String userAgent = headers.get(USER_AGENT_KEY);
        String username = headers.get(USERNAME_KEY);
        String requestPath = call.getMethodDescriptor().getFullMethodName();
        String requestMethod = "GRPC"; // gRPC 方法

        // 如果沒有客戶端 IP，嘗試從其他來源獲取
        if (clientIp == null) {
            clientIp = extractClientIpFromContext(call);
        }

        return new CommonSecurityService.RequestContext(clientIp, userAgent, username, requestPath, requestMethod);
    }


    /**
     * 從 gRPC 上下文中提取客戶端 IP
     */
    private String extractClientIpFromContext(ServerCall<?, ?> call) {
        try {
            // 嘗試從調用屬性中獲取客戶端地址
            java.net.SocketAddress remoteAddress = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            if (remoteAddress instanceof java.net.InetSocketAddress inetAddress) {
                return inetAddress.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            log.debug("Could not extract client IP from gRPC context", e);
        }

        return "unknown"; // 無法獲取 IP 時的預設值
    }


    /**
     * 處理安全違規
     */
    private void handleSecurityViolation(ServerCall<?, ?> call, CommonSecurityService.SecurityCheckResult result) {

        // 根據不同的安全動作設置相應的 gRPC 狀態碼
        Status status = switch (result.getRecommendedAction()) {
            case IP_BLOCK -> Status.PERMISSION_DENIED.withDescription("IP 地址被禁止訪問");
            case RATE_LIMIT -> Status.RESOURCE_EXHAUSTED.withDescription("請求頻率過高，請稍後再試");
            case CAPTCHA_REQUIRED -> Status.UNAUTHENTICATED.withDescription("需要完成安全驗證");
            case DENY -> Status.PERMISSION_DENIED.withDescription("訪問被拒絕");
            default -> Status.PERMISSION_DENIED.withDescription("安全檢查失敗");
        };

        // 創建包含詳細信息的元數據
        Metadata responseHeaders = new Metadata();
        responseHeaders.put(Metadata.Key.of("security-reason", Metadata.ASCII_STRING_MARSHALLER), result.getRecommendedAction().name());
        responseHeaders.put(Metadata.Key.of("security-details", Metadata.ASCII_STRING_MARSHALLER), result.getReason());

        // 關閉調用並返回錯誤
        call.close(status, responseHeaders);

        log.info("gRPC security violation handled: {} - {} for method: {}",
                 status.getCode(),
                 result.getReason(),
                 call.getMethodDescriptor().getFullMethodName()
        );
    }


    /**
     * 創建帶有安全上下文的攔截器實例
     * <p>
     * 這個方法可以用於在 gRPC 服務器配置中註冊攔截器
     */
    public static GrpcSecurityInterceptor create(CommonSecurityService securityService) {
        return new GrpcSecurityInterceptor(securityService);
    }


    /**
     * 創建客戶端攔截器（用於出站請求的安全處理）
     */
    public static ClientInterceptor createClientInterceptor(String clientIp, String userAgent, String username) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

                return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        // 添加安全相關的元數據
                        if (clientIp != null) {
                            headers.put(CLIENT_IP_KEY, clientIp);
                        }
                        if (userAgent != null) {
                            headers.put(USER_AGENT_KEY, userAgent);
                        }
                        if (username != null) {
                            headers.put(USERNAME_KEY, username);
                        }

                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }


    /**
     * 處理認證相關的 gRPC 調用
     * <p>
     * 這個方法專門處理認證請求，提供額外的安全檢查
     */
    public <ReqT, RespT> ServerCall.Listener<ReqT> handleAuthenticationCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        // 對於認證請求，進行額外的安全檢查
        String clientIp = headers.get(CLIENT_IP_KEY);
        if (clientIp != null) {
            // 檢查是否來自可信的 IP
            CommonSecurityService.RequestContext context = new CommonSecurityService.RequestContext(clientIp,
                                                                                                    headers.get(USER_AGENT_KEY),
                                                                                                    null,
                                                                                                    call.getMethodDescriptor().getFullMethodName(),
                                                                                                    "GRPC_AUTH"
            );

            // 記錄認證嘗試
            securityService.logSecurityEvent(context, "AUTHENTICATION_ATTEMPT", "gRPC 認證請求來自 IP: " + clientIp);
        }

        return next.startCall(call, headers);
    }
}