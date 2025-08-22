package dowob.xyz.filemanagementwebdav.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

/**
 * WebDAV 安全配置屬性類
 * 
 * 映射 application.yaml 中的 webdav.security 配置項，
 * 提供 JWT、IP 白名單、頻率限制、審計等安全相關配置。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavSecurityProperties
 * @create 2025/8/14
 * @Version 1.0
 **/
@Data
@Component
@ConfigurationProperties(prefix = "webdav.security")
public class WebDavSecurityProperties {
    
    /**
     * WebDAV 認證域名
     */
    private String realm = "FileManagement WebDAV";
    
    /**
     * JWT 配置
     */
    private JwtConfig jwt = new JwtConfig();
    
    /**
     * IP 白名單配置
     */
    private IpConfig ip = new IpConfig();
    
    /**
     * 頻率限制配置
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();
    
    /**
     * 審計配置
     */
    private AuditConfig audit = new AuditConfig();
    
    /**
     * JWT 配置內嵌類
     */
    @Data
    public static class JwtConfig {
        /**
         * JWT 密鑰
         */
        private String secret = "change-this-secret-in-production-environment-to-a-strong-secret-key";
        
        /**
         * JWT 發行者
         */
        private String issuer = "FileManagement-System";
        
        /**
         * JWT 撤銷檢查配置
         */
        private RevocationConfig revocation = new RevocationConfig();
        
        /**
         * JWT 撤銷檢查配置內嵌類
         */
        @Data
        public static class RevocationConfig {
            private CacheConfig cache = new CacheConfig();
            
            @Data
            public static class CacheConfig {
                /**
                 * 快取過期時間（分鐘）
                 */
                private int expireMinutes = 10;
                
                /**
                 * 最大快取條目數
                 */
                private int maxSize = 5000;
            }
        }
    }
    
    /**
     * IP 配置內嵌類
     */
    @Data
    public static class IpConfig {
        /**
         * IP 白名單配置
         */
        private WhitelistConfig whitelist = new WhitelistConfig();
        
        /**
         * IP 黑名單配置
         */
        private BlacklistConfig blacklist = new BlacklistConfig();
        
        /**
         * IP 白名單配置內嵌類
         */
        @Data
        public static class WhitelistConfig {
            /**
             * 是否啟用 IP 白名單
             */
            private boolean enabled = false;
            
            /**
             * 白名單 IP 列表
             */
            private List<String> ips = new ArrayList<>();
        }
        
        /**
         * IP 黑名單配置內嵌類
         */
        @Data
        public static class BlacklistConfig {
            /**
             * 黑名單 IP 列表
             */
            private List<String> ips = new ArrayList<>();
        }
    }
    
    /**
     * 頻率限制配置內嵌類
     */
    @Data
    public static class RateLimitConfig {
        /**
         * 每個 IP 每分鐘最大請求數
         */
        private int ipRequestsPerMinute = 60;
        
        /**
         * 每個用戶每分鐘最大請求數
         */
        private int userRequestsPerMinute = 120;
        
        /**
         * 全域每秒最大請求數
         */
        private int globalRequestsPerSecond = 100;
        
        /**
         * 頻率限制快取大小
         */
        private int cacheSize = 10000;
    }
    
    /**
     * 審計配置內嵌類
     */
    @Data
    public static class AuditConfig {
        /**
         * 是否包含請求詳情
         */
        private boolean includeRequestDetails = true;
        
        /**
         * 是否遮蔽敏感數據
         */
        private boolean sensitiveDataMask = true;
    }
}