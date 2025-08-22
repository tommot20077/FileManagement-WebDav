package dowob.xyz.filemanagementwebdav.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 認證上下文管理器
 * 
 * 管理跨線程的認證狀態，解決 Milton 框架可能在不同線程調用的問題。
 * 使用會話 ID 或請求標識符來持久化認證狀態。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName AuthenticationContextManager
 * @create 2025/8/16
 * @Version 1.0
 **/
@Slf4j
@Component
public class AuthenticationContextManager {
    
    /**
     * 認證信息緩存
     * key: sessionId or requestId
     * value: 認證信息
     */
    private final Map<String, AuthInfo> authCache = new ConcurrentHashMap<>();
    
    /**
     * 最後一次成功認證的信息（作為備用）
     */
    private volatile AuthInfo lastAuthInfo;
    
    /**
     * 存儲認證信息
     */
    public void storeAuthentication(String sessionId, String userId, String username) {
        AuthInfo authInfo = new AuthInfo(userId, username, System.currentTimeMillis());
        authCache.put(sessionId, authInfo);
        lastAuthInfo = authInfo; // 更新最後認證信息
        log.debug("Stored authentication for session: {}, user: {}", sessionId, username);
        
        // 清理過期的認證信息（超過 30 分鐘）
        cleanupExpiredAuth();
    }
    
    /**
     * 獲取認證信息
     */
    public AuthInfo getAuthentication(String sessionId) {
        AuthInfo authInfo = authCache.get(sessionId);
        if (authInfo != null) {
            log.debug("Retrieved authentication for session: {}, user: {}", sessionId, authInfo.username);
        }
        return authInfo;
    }
    
    /**
     * 獲取最後一次認證信息（備用方案）
     */
    public AuthInfo getLastAuthentication() {
        if (lastAuthInfo != null) {
            // 檢查是否過期（5 分鐘內有效）
            long age = System.currentTimeMillis() - lastAuthInfo.timestamp;
            if (age < TimeUnit.MINUTES.toMillis(5)) {
                log.debug("Using last authentication for user: {}", lastAuthInfo.username);
                return lastAuthInfo;
            }
        }
        return null;
    }
    
    /**
     * 清除認證信息
     */
    public void clearAuthentication(String sessionId) {
        authCache.remove(sessionId);
        log.debug("Cleared authentication for session: {}", sessionId);
    }
    
    /**
     * 清理過期的認證信息
     */
    private void cleanupExpiredAuth() {
        long now = System.currentTimeMillis();
        long maxAge = TimeUnit.MINUTES.toMillis(30);
        
        authCache.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue().timestamp) > maxAge;
            if (expired) {
                log.debug("Removing expired authentication for session: {}", entry.getKey());
            }
            return expired;
        });
    }
    
    /**
     * 認證信息內部類
     */
    public static class AuthInfo {
        public final String userId;
        public final String username;
        public final long timestamp;
        
        public AuthInfo(String userId, String username, long timestamp) {
            this.userId = userId;
            this.username = username;
            this.timestamp = timestamp;
        }
    }
}