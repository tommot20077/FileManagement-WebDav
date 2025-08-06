package dowob.xyz.filemanagementwebdav.component.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 身份驗證快取組件
 * 
 * 使用 Caffeine 快取庫實現高性能的本地快取，
 * 用於暫存用戶身份驗證結果，減少對主服務的調用。
 * 
 * 注意：在 WebDAV 專用子服務中，此快取組件無條件啟用以確保性能。
 * 條件啟用應該在主服務中決定是否啟動此 WebDAV 子服務。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName AuthenticationCache
 * @create 2025/8/5
 * @Version 1.0
 **/
@Slf4j
@Component
public class AuthenticationCache {
    
    private final Cache<String, AuthCacheEntry> cache;
    
    /**
     * 快取項目類
     */
    public static class AuthCacheEntry {
        private final String userId;
        private final String username;
        private final boolean authenticated;
        private final long timestamp;
        
        public AuthCacheEntry(String userId, String username, boolean authenticated) {
            this.userId = userId;
            this.username = username;
            this.authenticated = authenticated;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public boolean isAuthenticated() {
            return authenticated;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 構造函數
     * 
     * 注意：在 WebDAV 專用子服務中，快取無條件啟用以確保性能
     * 
     * @param maxSize 最大快取數量
     * @param expireMinutes 過期時間（分鐘）
     */
    public AuthenticationCache(
            @Value("${webdav.auth.cache.max-size:1000}") int maxSize,
            @Value("${webdav.auth.cache.expire-minutes:5}") int expireMinutes) {
        
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireMinutes, TimeUnit.MINUTES)
                .removalListener((String key, AuthCacheEntry value, RemovalCause cause) -> {
                    if (value != null) {
                        log.debug("Cache entry removed for user: {}, cause: {}", 
                                value.getUsername(), cause);
                    }
                })
                .recordStats()  // 啟用統計
                .build();
        
        log.info("AuthenticationCache initialized - maxSize: {}, expireMinutes: {}", 
                maxSize, expireMinutes);
    }
    
    /**
     * 獲取快取項目
     * 
     * @param username 用戶名
     * @param password 密碼
     * @return 快取的驗證結果，如果沒有快取則返回 null
     */
    public AuthCacheEntry get(String username, String password) {
        String cacheKey = generateCacheKey(username, password);
        AuthCacheEntry entry = cache.getIfPresent(cacheKey);
        
        if (entry != null) {
            log.debug("Cache hit for user: {}", username);
        } else {
            log.debug("Cache miss for user: {}", username);
        }
        
        return entry;
    }
    
    /**
     * 存入快取
     * 
     * @param username 用戶名
     * @param password 密碼
     * @param userId 用戶ID
     * @param authenticated 是否驗證成功
     */
    public void put(String username, String password, String userId, boolean authenticated) {
        String cacheKey = generateCacheKey(username, password);
        AuthCacheEntry entry = new AuthCacheEntry(userId, username, authenticated);
        cache.put(cacheKey, entry);
        
        log.debug("Cached authentication result for user: {}, authenticated: {}", 
                username, authenticated);
    }
    
    /**
     * 使特定用戶的所有快取失效
     * 
     * @param username 用戶名
     */
    public void invalidateUser(String username) {
        // 移除所有包含該用戶名的快取項目
        cache.asMap().entrySet().removeIf(entry -> {
            AuthCacheEntry value = entry.getValue();
            return value != null && username.equals(value.getUsername());
        });
        
        log.info("Invalidated all cache entries for user: {}", username);
    }
    
    /**
     * 清空所有快取
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("All cache entries invalidated");
    }
    
    /**
     * 獲取快取統計信息
     * 
     * @return 快取統計信息字符串
     */
    public String getStats() {
        return cache.stats().toString();
    }
    
    /**
     * 獲取當前快取大小
     * 
     * @return 快取中的項目數量
     */
    public long getSize() {
        return cache.estimatedSize();
    }
    
    /**
     * 生成快取鍵
     * 使用 SHA-256 雜湊來保護密碼
     * 
     * @param username 用戶名
     * @param password 密碼
     * @return 快取鍵
     */
    private String generateCacheKey(String username, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = username + ":" + password;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}