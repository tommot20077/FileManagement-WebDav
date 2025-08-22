package dowob.xyz.filemanagementwebdav.config;

import dowob.xyz.filemanagementwebdav.component.security.CommonSecurityService;
import dowob.xyz.filemanagementwebdav.component.security.GrpcSecurityInterceptor;
import dowob.xyz.filemanagementwebdav.config.properties.GrpcProperties;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * gRPC 客戶端配置
 * 
 * 配置與主服務的 gRPC 連接，包含：
 * - Channel 建立
 * - API Key 認證攔截器
 * - 安全服務整合
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName GrpcClientConfig
 * @create 2025/6/9
 * @Version 1.0
 **/

@Slf4j
@Configuration
public class GrpcClientConfig {
    private final GrpcProperties grpcProperties;
    private final CommonSecurityService securityService;

    public GrpcClientConfig(GrpcProperties grpcProperties, 
                           @Autowired(required = false) CommonSecurityService securityService) {
        this.grpcProperties = grpcProperties;
        this.securityService = securityService;

        Assert.notNull(grpcProperties.getHost(), "gRPC 的主機地址不能為空");
        Assert.isTrue(grpcProperties.getPort() > 0, "gRPC 的端口號必須大於 0");
    }

    @Bean
    public ManagedChannel managedChannel() {
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                .forAddress(grpcProperties.getHost(), grpcProperties.getPort())
                .usePlaintext();
        
        // 添加 API Key 攔截器用於認證
        if (grpcProperties.getApiKey() != null && !grpcProperties.getApiKey().isEmpty()) {
            log.info("Configuring gRPC client with API Key authentication");
            ClientInterceptor apiKeyInterceptor = new ApiKeyClientInterceptor(grpcProperties.getApiKey());
            channelBuilder.intercept(apiKeyInterceptor);
        } else {
            log.warn("No API Key configured for gRPC client - requests may be rejected by the main service");
        }
        
        // 如果安全服務可用，添加客戶端安全攔截器
        if (securityService != null) {
            // 這裡可以根據需要添加客戶端攔截器
            // 例如：添加認證信息、客戶端 IP 等
            ClientInterceptor securityInterceptor = 
                GrpcSecurityInterceptor.createClientInterceptor(
                    getLocalHostIp(), 
                    "FileManagement-WebDAV-Client/1.0", 
                    null // 客戶端調用時通常沒有用戶名
                );
            channelBuilder.intercept(securityInterceptor);
        }
        
        return channelBuilder.build();
    }
    
    /**
     * 獲取本地主機 IP
     */
    private String getLocalHostIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }
    
    /**
     * gRPC 安全攔截器 Bean（用於服務端）
     * 如果這個應用也作為 gRPC 服務端，可以註冊此攔截器
     */
    @Bean
    public GrpcSecurityInterceptor grpcSecurityInterceptor() {
        if (securityService != null) {
            return new GrpcSecurityInterceptor(securityService);
        }
        return null;
    }
    
    /**
     * API Key 客戶端攔截器
     * 為所有 gRPC 請求自動添加 x-api-key 頭部
     */
    private static class ApiKeyClientInterceptor implements ClientInterceptor {
        private static final Metadata.Key<String> API_KEY_HEADER = 
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
        
        private final String apiKey;
        
        public ApiKeyClientInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }
        
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {
            
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                    next.newCall(method, callOptions)) {
                
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // 添加 API Key 到請求頭
                    headers.put(API_KEY_HEADER, apiKey);
                    super.start(responseListener, headers);
                }
            };
        }
    }
}
