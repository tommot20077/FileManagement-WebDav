package dowob.xyz.filemanagementwebdav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FileManagement WebDAV 專用子服務啟動類
 * 
 * 這是一個專門提供 WebDAV 功能的微服務，包含：
 * - WebDAV 協議支持
 * - JWT 身份驗證
 * - IP 白名單/黑名單安全控制
 * - 頻率限制
 * - 認證快取
 * - 安全審計
 * 
 * 設計理念：
 * - 所有 WebDAV 相關功能在此子服務中無條件啟用
 * - 條件啟用邏輯應該在主服務中決定是否啟動此子服務
 * - 此服務專注於 WebDAV 功能的高性能實現
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @create 2025/8/5
 * @version 1.0
 */
@SpringBootApplication
@EnableScheduling
public class FileManagementWebDavApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(FileManagementWebDavApplication.class, args);
    }
}
