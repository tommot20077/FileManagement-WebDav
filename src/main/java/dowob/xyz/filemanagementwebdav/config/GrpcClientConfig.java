package dowob.xyz.filemanagementwebdav.config;

import dowob.xyz.filemanagementwebdav.component.security.CommonSecurityService;
import dowob.xyz.filemanagementwebdav.component.security.GrpcSecurityInterceptor;
import dowob.xyz.filemanagementwebdav.config.properties.GrpcProperties;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName GrpcClientConfig
 * @create 2025/6/9
 * @Version 1.0
 **/

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
}
