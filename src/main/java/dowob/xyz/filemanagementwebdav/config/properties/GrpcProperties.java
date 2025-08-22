package dowob.xyz.filemanagementwebdav.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC 連線配置屬性
 * 
 * 包含與主服務通訊所需的配置：
 * - 主服務地址和端口
 * - API Key 認證憑證
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName GrpcProperties
 * @create 2025/6/9
 * @Version 1.0
 **/

@Data
@Configuration
@ConfigurationProperties(prefix = "grpc")
public class GrpcProperties {
    private String host = "localhost";
    private int port = 9090;
    
    /**
     * API Key 用於與主服務進行認證
     * 必須與主服務的 global.webdav.api-key 配置相同
     */
    private String apiKey;
}
