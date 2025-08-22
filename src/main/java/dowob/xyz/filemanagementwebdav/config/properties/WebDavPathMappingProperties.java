package dowob.xyz.filemanagementwebdav.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WebDAV 路徑映射配置屬性類
 * 
 * 映射 application.yaml 中的 webdav.path-mapping 配置項，
 * 提供路徑映射快取和同步相關配置。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavPathMappingProperties
 * @create 2025/8/14
 * @Version 1.0
 **/
@Data
@Component
@ConfigurationProperties(prefix = "webdav.path-mapping")
public class WebDavPathMappingProperties {
    
    /**
     * 路徑映射快取大小
     */
    private int cacheSize = 10000;
    
    /**
     * 快取過期時間（秒）
     */
    private int cacheTtl = 3600;
    
    /**
     * 同步間隔（秒）
     */
    private int syncInterval = 300;
}