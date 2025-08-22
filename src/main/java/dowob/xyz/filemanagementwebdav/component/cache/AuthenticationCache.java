package dowob.xyz.filemanagementwebdav.component.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 基於 Caffeine 框架的高效能身份驗證快取實現，提供執行緒安全的本地快取機制以減少對主服務的 gRPC 調用開銷。
 * 
 * <p>此實現使用 SHA-256 雜湊演算法生成安全的快取鍵，確保敏感資訊不以明文形式儲存。快取支援基於時間的自動過期、
 * 基於容量的 LRU 清理機制，以及詳細的效能統計功能。所有快取操作均為非阻塞式，適合高並發 WebDAV 環境使用。
 * 
 * <p>快取項目包含用戶識別碼、用戶名稱、驗證狀態和時間戳記等資訊。支援單一用戶快取失效和全域快取清空操作，
 * 適用於密碼變更或系統維護等場景。在 WebDAV 專用子服務中此快取無條件啟用以確保最佳效能。
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Log4j2
@Component
public class AuthenticationCache {
    
    private final Cache<String, AuthCacheEntry> cache;
    
    /**
     * 身份驗證快取項目，包含用戶身份驗證的相關資訊和時間戳記。
     * <p>
     * 此類別為不可變物件，一旦建立後其內容無法修改。時間戳記在建構時自動設定為當前系統時間。
     * 
     * @since 1.0
     */
    public static class AuthCacheEntry {
        private final String userId;
        private final String username;
        private final String role;
        private final boolean authenticated;
        private final long timestamp;
        
        /**
         * 建構身份驗證快取項目，包含完整的用戶身份資訊。
         * <p>
         * 時間戳記會自動設定為當前系統時間（毫秒）。
         * 
         * @param userId 用戶識別碼，可以為 {@code null}
         * @param username 用戶名稱，不可為 {@code null}
         * @param authenticated 是否驗證成功
         */
        public AuthCacheEntry(String userId, String username, String role, boolean authenticated) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.authenticated = authenticated;
            this.timestamp = System.currentTimeMillis();
        }
        
        /**
         * 取得用戶識別碼。
         * 
         * @return 用戶識別碼，可能為 {@code null}
         */
        public String getUserId() {
            return userId;
        }
        
        /**
         * 取得用戶名稱。
         * 
         * @return 用戶名稱，不會為 {@code null}
         */
        public String getUsername() {
            return username;
        }
        
        /**
         * 取得用戶角色。
         * <p>
         * 返回用戶的系統角色，用於權限控制和存取管理。
         * 
         * @return 用戶角色字串，例如 ADMIN、USER 等
         * @since 1.0
         */
        public String getRole() {
            return role;
        }
        
        /**
         * 檢查身份驗證是否成功。
         * 
         * @return {@code true} 表示驗證成功，{@code false} 表示驗證失敗
         */
        public boolean isAuthenticated() {
            return authenticated;
        }
        
        /**
         * 取得快取項目的建立時間戳記。
         * 
         * @return 建立時間，以毫秒為單位的 Unix 時間戳記
         */
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 建構身份驗證快取組件並初始化 Caffeine 快取實例。
     * <p>
     * 快取會自動啟用統計功能和移除監聽器，用於記錄快取項目的移除情況。
     * 快取項目會在寫入後的指定時間後過期，並且當快取大小超過最大容量時會自動清理最舊的項目。
     * <p>
     * 在 WebDAV 專用子服務中，此快取無條件啟用以確保最佳性能表現。
     * 
     * @param maxSize 快取的最大項目數量，必須為正整數
     * @param expireMinutes 快取項目的過期時間（分鐘），必須為正整數
     */
    public AuthenticationCache(
            @Value("${webdav.auth.cache.max-size:1000}") int maxSize,
            @Value("${webdav.auth.cache.expire-minutes:5}") int expireMinutes) {
        
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireMinutes, TimeUnit.MINUTES)
                .removalListener((String key, AuthCacheEntry value, RemovalCause cause) -> {
                    if (value != null) {
                        log.debug("使用者快取項目已移除：{}, 原因：{}", 
                                value.getUsername(), cause);
                    }
                })
                .recordStats()
                .build();
        
        log.info("身份驗證快取已初始化 - 最大容量：{}，過期時間：{}分鐘", 
                maxSize, expireMinutes);
    }
    
    /**
     * 根據用戶名和密碼從快取中取得身份驗證結果。
     * <p>
     * 此方法會使用 SHA-256 雜湊演算法生成安全的快取鍵來查詢快取項目。
     * 如果快取中存在對應的項目且尚未過期，則返回該項目；否則返回 {@code null}。
     * <p>
     * 快取命中或未命中的情況會記錄在除錯日誌中。
     * 
     * @param username 用戶名稱，不可為 {@code null}
     * @param password 用戶密碼，不可為 {@code null}
     * @return 快取的驗證結果，如果沒有對應的快取項目或已過期則返回 {@code null}
     */
    public AuthCacheEntry get(String username, String password) {
        String cacheKey = generateCacheKey(username, password);
        AuthCacheEntry entry = cache.getIfPresent(cacheKey);
        
        if (entry != null) {
            log.debug("使用者快取命中：{}", username);
        } else {
            log.debug("使用者快取未命中：{}", username);
        }
        
        return entry;
    }
    
    /**
     * 將身份驗證結果儲存至快取中。
     * <p>
     * 此方法會使用 SHA-256 雜湊演算法生成安全的快取鍵，並建立新的快取項目。
     * 如果快取已達到最大容量，最舊的項目會被自動清理。快取項目會在配置的過期時間後自動失效。
     * <p>
     * 快取操作會記錄在除錯日誌中，包含用戶名和驗證狀態。
     * 
     * @param username 用戶名稱，不可為 {@code null}
     * @param password 用戶密碼，不可為 {@code null}
     * @param userId 用戶識別碼，可以為 {@code null}
     * @param authenticated 身份驗證是否成功
     */
    public void put(String username, String password, String userId, String role, boolean authenticated) {
        String cacheKey = generateCacheKey(username, password);
        AuthCacheEntry entry = new AuthCacheEntry(userId, username, role, authenticated);
        cache.put(cacheKey, entry);
        
        log.debug("已快取使用者驗證結果：{}，角色：{}，驗證狀態：{}", 
                username, role, authenticated);
    }
    
    /**
     * 將身份驗證結果儲存至快取中（向後相容版本）。
     * <p>
     * 此方法保留向後相容性，但建議使用包含角色資訊的新版本。
     * 
     * @param username 用戶名稱
     * @param password 用戶密碼
     * @param userId 用戶識別碼
     * @param authenticated 身份驗證是否成功
     * @deprecated 使用 {@link #put(String, String, String, String, boolean)}
     */
    @Deprecated
    public void put(String username, String password, String userId, boolean authenticated) {
        put(username, password, userId, null, authenticated);
    }
    
    /**
     * 使特定用戶的所有快取項目失效。
     * <p>
     * 此方法會遍歷快取中的所有項目，移除所有屬於指定用戶名的快取項目。
     * 這對於用戶密碼變更或帳戶停用等情況很有用。
     * <p>
     * 失效操作會記錄在資訊日誌中。
     * 
     * @param username 要失效的用戶名稱，不可為 {@code null}
     */
    public void invalidateUser(String username) {
        cache.asMap().entrySet().removeIf(entry -> {
            AuthCacheEntry value = entry.getValue();
            return value != null && username.equals(value.getUsername());
        });
        
        log.info("已使所有使用者快取項目失效：{}", username);
    }
    
    /**
     * 清空快取中的所有項目。
     * <p>
     * 此操作會立即移除快取中的所有項目，適用於系統維護或安全性考量需要清空所有快取的情況。
     * 清空操作會記錄在資訊日誌中。
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("已清空所有快取項目");
    }
    
    /**
     * 取得快取的統計資訊。
     * <p>
     * 返回的統計資訊包含快取命中率、請求次數、清理次數等效能指標。
     * 統計功能在快取初始化時已自動啟用。
     * 
     * @return 包含詳細統計資訊的字串表示
     */
    public String getStats() {
        return cache.stats().toString();
    }
    
    /**
     * 取得快取中當前的項目數量估計值。
     * <p>
     * 由於 Caffeine 的非同步清理機制，此值可能略大於實際的項目數量。
     * 對於精確的統計資訊，建議使用 {@link #getStats()} 方法。
     * 
     * @return 快取中的項目數量估計值，永遠不會是負數
     */
    public long getSize() {
        return cache.estimatedSize();
    }
    
    /**
     * 使用 SHA-256 雜湊演算法生成安全的快取鍵。
     * <p>
     * 此方法將用戶名和密碼組合後進行 SHA-256 雜湊處理，然後使用 Base64 編碼生成最終的快取鍵。
     * 這確保了密碼不會以明文形式出現在快取鍵中，提高了安全性。
     * <p>
     * 快取鍵的格式為：Base64(SHA-256(username + ":" + password))
     * 
     * @param username 用戶名稱，不可為 {@code null}
     * @param password 用戶密碼，不可為 {@code null}
     * @return 經過 Base64 編碼的 SHA-256 雜湊值作為快取鍵
     * @throws RuntimeException 當 SHA-256 雜湊演算法不可用時拋出，通常不會發生
     */
    private String generateCacheKey(String username, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = username + ":" + password;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 演算法不可用", e);
        }
    }
}