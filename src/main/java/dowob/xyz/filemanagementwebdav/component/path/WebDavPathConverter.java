package dowob.xyz.filemanagementwebdav.component.path;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * WebDAV 路徑轉換器
 * 
 * 負責在 WebDAV 外部路徑（/dav/...）和內部路徑（/userId/...）之間進行轉換。
 * 確保用戶只能訪問自己的文件空間，不在 URL 中暴露用戶ID。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavPathConverter
 * @create 2025/8/15
 * @Version 1.0
 **/
@Log4j2
@Component
public class WebDavPathConverter {
    
    private static final String WEBDAV_PREFIX = "/dav";
    private static final String PATH_SEPARATOR = "/";
    
    /**
     * 將 WebDAV 路徑轉換為內部路徑
     * 
     * 示例：
     * - /dav → /123
     * - /dav/ → /123
     * - /dav/documents → /123/documents
     * - /dav/documents/file.txt → /123/documents/file.txt
     * 
     * @param webdavPath WebDAV 路徑
     * @param userId 用戶ID
     * @return 內部路徑
     */
    public String toInternalPath(String webdavPath, String userId) {
        if (webdavPath == null || userId == null) {
            throw new IllegalArgumentException("路徑和用戶ID不能為空");
        }
        
        // 移除 WebDAV 前綴並獲取相對路徑
        String relativePath = removeWebDavPrefix(webdavPath);
        
        // 構建內部路徑
        if (relativePath.isEmpty() || relativePath.equals(PATH_SEPARATOR)) {
            // 根路徑
            return PATH_SEPARATOR + userId;
        }
        
        // 確保相對路徑以斜線開始
        if (!relativePath.startsWith(PATH_SEPARATOR)) {
            relativePath = PATH_SEPARATOR + relativePath;
        }
        
        String internalPath = PATH_SEPARATOR + userId + relativePath;
        
        log.debug("路徑轉換: {} -> {} (用戶: {})", webdavPath, internalPath, userId);
        
        return internalPath;
    }
    
    /**
     * 將內部路徑轉換為 WebDAV 路徑
     * 
     * 示例：
     * - /123 → /dav
     * - /123/ → /dav/
     * - /123/documents → /dav/documents
     * - /123/documents/file.txt → /dav/documents/file.txt
     * 
     * @param internalPath 內部路徑
     * @param userId 用戶ID
     * @return WebDAV 路徑
     */
    public String toWebDavPath(String internalPath, String userId) {
        if (internalPath == null) {
            return WEBDAV_PREFIX;
        }
        
        // 移除用戶ID前綴
        String userPrefix = PATH_SEPARATOR + userId;
        String relativePath;
        
        if (internalPath.equals(userPrefix)) {
            // 用戶根目錄
            return WEBDAV_PREFIX;
        } else if (internalPath.startsWith(userPrefix + PATH_SEPARATOR)) {
            // 用戶子目錄或文件
            relativePath = internalPath.substring(userPrefix.length());
        } else {
            // 路徑不包含預期的用戶ID前綴
            log.warn("內部路徑不包含用戶ID前綴: {} (預期前綴: {})", internalPath, userPrefix);
            relativePath = internalPath;
        }
        
        String webdavPath = WEBDAV_PREFIX + relativePath;
        
        log.debug("路徑還原: {} -> {} (用戶: {})", internalPath, webdavPath, userId);
        
        return webdavPath;
    }
    
    /**
     * 移除 WebDAV 前綴
     * 
     * @param path 原始路徑
     * @return 相對路徑
     */
    private String removeWebDavPrefix(String path) {
        if (path == null) {
            return "";
        }
        
        // 處理根路徑
        if (path.equals(WEBDAV_PREFIX) || path.equals(WEBDAV_PREFIX + PATH_SEPARATOR)) {
            return PATH_SEPARATOR;
        }
        
        // 移除前綴
        if (path.startsWith(WEBDAV_PREFIX + PATH_SEPARATOR)) {
            return path.substring(WEBDAV_PREFIX.length());
        }
        
        if (path.startsWith(WEBDAV_PREFIX)) {
            String remaining = path.substring(WEBDAV_PREFIX.length());
            if (remaining.isEmpty()) {
                return PATH_SEPARATOR;
            }
            return remaining;
        }
        
        // 如果路徑不包含 WebDAV 前綴，返回原路徑
        log.debug("路徑不包含 WebDAV 前綴: {}", path);
        return path;
    }
    
    /**
     * 檢查是否為 WebDAV 路徑
     * 
     * @param path 路徑
     * @return true 如果是 WebDAV 路徑
     */
    public boolean isWebDavPath(String path) {
        return path != null && (path.equals(WEBDAV_PREFIX) || path.startsWith(WEBDAV_PREFIX + PATH_SEPARATOR));
    }
    
    /**
     * 標準化 WebDAV 路徑
     * 
     * @param path 原始路徑
     * @return 標準化的路徑
     */
    public String normalizeWebDavPath(String path) {
        if (path == null || path.isEmpty()) {
            return WEBDAV_PREFIX;
        }
        
        // 確保以 /dav 開頭
        if (!path.startsWith(WEBDAV_PREFIX)) {
            if (path.startsWith(PATH_SEPARATOR)) {
                return WEBDAV_PREFIX + path;
            } else {
                return WEBDAV_PREFIX + PATH_SEPARATOR + path;
            }
        }
        
        return path;
    }
    
    /**
     * 獲取 WebDAV 基礎路徑
     * 
     * @return /dav
     */
    public String getWebDavPrefix() {
        return WEBDAV_PREFIX;
    }
}