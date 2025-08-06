package dowob.xyz.filemanagementwebdav.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
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
}
