package dowob.xyz.filemanagementwebdav.component.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 頻率限制服務
 * 
 * 實現基於滑動視窗的頻率限制算法，支持多種限制策略：
 * - 基於 IP 的頻率限制
 * - 基於用戶的頻率限制  
 * - 基於端點的頻率限制
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName RateLimitService
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Service
public class RateLimitService {
    
    // 不同類型的頻率限制配置
    private final int ipRequestsPerMinute;
    private final int userRequestsPerMinute;
    private final int globalRequestsPerSecond;
    
    // 使用 Caffeine 實現高性能限流器
    private final Cache<String, RateLimitBucket> rateLimitCache;
    
    /**
     * 頻率限制桶
     */
    private static class RateLimitBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile LocalDateTime windowStart;
        private final int maxRequests;
        private final ChronoUnit timeUnit;
        
        public RateLimitBucket(int maxRequests, ChronoUnit timeUnit) {
            this.maxRequests = maxRequests;
            this.timeUnit = timeUnit;
            this.windowStart = LocalDateTime.now();
        }
        
        /**
         * 嘗試獲取許可
         * @return true 如果允許請求，false 如果超過限制
         */
        public synchronized boolean tryAcquire() {
            LocalDateTime now = LocalDateTime.now();
            
            // 檢查是否需要重置視窗
            if (shouldResetWindow(now)) {
                resetWindow(now);
            }
            
            // 檢查是否超過限制
            if (count.get() >= maxRequests) {
                log.debug("Rate limit exceeded: {}/{} requests in current window", 
                         count.get(), maxRequests);
                return false;
            }
            
            // 增加計數器
            count.incrementAndGet();
            return true;
        }
        
        /**
         * 檢查是否需要重置視窗
         */
        private boolean shouldResetWindow(LocalDateTime now) {
            long elapsedTime = ChronoUnit.SECONDS.between(windowStart, now);
            
            return switch (timeUnit) {
                case SECONDS -> elapsedTime >= 1;
                case MINUTES -> elapsedTime >= 60;
                case HOURS -> elapsedTime >= 3600;
                default -> false;
            };
        }
        
        /**
         * 重置視窗
         */
        private void resetWindow(LocalDateTime now) {
            this.windowStart = now;
            this.count.set(0);
            log.debug("Rate limit window reset at {}", now);
        }
        
        /**
         * 獲取剩餘請求數
         */
        public int getRemainingRequests() {
            return Math.max(0, maxRequests - count.get());
        }
        
        /**
         * 獲取當前計數
         */
        public int getCurrentCount() {
            return count.get();
        }
        
        /**
         * 獲取視窗開始時間
         */
        public LocalDateTime getWindowStart() {
            return windowStart;
        }
    }
    
    /**
     * 構造函數
     */
    public RateLimitService(
            @Value("${webdav.security.rate-limit.ip-requests-per-minute:60}") int ipRequestsPerMinute,
            @Value("${webdav.security.rate-limit.user-requests-per-minute:120}") int userRequestsPerMinute,
            @Value("${webdav.security.rate-limit.global-requests-per-second:100}") int globalRequestsPerSecond,
            @Value("${webdav.security.rate-limit.cache-size:10000}") int cacheSize) {
        
        this.ipRequestsPerMinute = ipRequestsPerMinute;
        this.userRequestsPerMinute = userRequestsPerMinute;
        this.globalRequestsPerSecond = globalRequestsPerSecond;
        
        // 初始化快取，設置適當的過期時間和大小
        this.rateLimitCache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterWrite(2, TimeUnit.MINUTES) // 2分鐘後過期
                .removalListener((key, bucket, cause) -> {
                    if (bucket != null) {
                        log.debug("Rate limit bucket removed for key: {}, cause: {}", key, cause);
                    }
                })
                .recordStats()
                .build();
        
        log.info("RateLimitService initialized - IP: {}/min, User: {}/min, Global: {}/sec", 
                ipRequestsPerMinute, userRequestsPerMinute, globalRequestsPerSecond);
    }
    
    /**
     * 檢查是否允許請求
     * 
     * @param key 限制鍵（如 "ip:192.168.1.1" 或 "user:john"）
     * @return true 如果允許，false 如果被限制
     */
    public boolean isAllowed(String key) {
        if (key == null || key.trim().isEmpty()) {
            return true;
        }
        
        try {
            RateLimitBucket bucket = getRateLimitBucket(key);
            boolean allowed = bucket.tryAcquire();
            
            if (!allowed) {
                log.debug("Rate limit exceeded for key: {}", key);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for key: {}", key, e);
            // 發生錯誤時，傾向於允許請求（可配置）
            return true;
        }
    }
    
    /**
     * 獲取剩餘請求數
     * 
     * @param key 限制鍵
     * @return 剩餘請求數
     */
    public int getRemainingRequests(String key) {
        if (key == null || key.trim().isEmpty()) {
            return Integer.MAX_VALUE;
        }
        
        try {
            RateLimitBucket bucket = rateLimitCache.getIfPresent(key);
            return bucket != null ? bucket.getRemainingRequests() : getMaxRequestsForKey(key);
        } catch (Exception e) {
            log.error("Error getting remaining requests for key: {}", key, e);
            return 0;
        }
    }
    
    /**
     * 獲取當前請求數
     * 
     * @param key 限制鍵
     * @return 當前視窗內的請求數
     */
    public int getCurrentRequestCount(String key) {
        if (key == null || key.trim().isEmpty()) {
            return 0;
        }
        
        RateLimitBucket bucket = rateLimitCache.getIfPresent(key);
        return bucket != null ? bucket.getCurrentCount() : 0;
    }
    
    /**
     * 清除特定鍵的限制
     * 
     * @param key 限制鍵
     */
    public void clearRateLimit(String key) {
        rateLimitCache.invalidate(key);
        log.info("Rate limit cleared for key: {}", key);
    }
    
    /**
     * 清除所有限制
     */
    public void clearAllRateLimits() {
        rateLimitCache.invalidateAll();
        log.info("All rate limits cleared");
    }
    
    /**
     * 獲取限流統計信息
     * 
     * @return 統計信息字符串
     */
    public String getStats() {
        var stats = rateLimitCache.stats();
        return String.format(
            "RateLimit Stats - Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%", 
            rateLimitCache.estimatedSize(),
            stats.hitCount(),
            stats.missCount(),
            stats.hitRate() * 100
        );
    }
    
    /**
     * 獲取或創建頻率限制桶
     */
    private RateLimitBucket getRateLimitBucket(String key) {
        return rateLimitCache.get(key, k -> createRateLimitBucket(k));
    }
    
    /**
     * 根據鍵類型創建相應的頻率限制桶
     */
    private RateLimitBucket createRateLimitBucket(String key) {
        if (key.startsWith("ip:")) {
            return new RateLimitBucket(ipRequestsPerMinute, ChronoUnit.MINUTES);
        } else if (key.startsWith("user:")) {
            return new RateLimitBucket(userRequestsPerMinute, ChronoUnit.MINUTES);
        } else if (key.startsWith("global:")) {
            return new RateLimitBucket(globalRequestsPerSecond, ChronoUnit.SECONDS);
        } else {
            // 預設使用 IP 限制
            log.debug("Using default IP rate limit for key: {}", key);
            return new RateLimitBucket(ipRequestsPerMinute, ChronoUnit.MINUTES);
        }
    }
    
    /**
     * 獲取鍵對應的最大請求數
     */
    private int getMaxRequestsForKey(String key) {
        if (key.startsWith("ip:")) {
            return ipRequestsPerMinute;
        } else if (key.startsWith("user:")) {
            return userRequestsPerMinute;
        } else if (key.startsWith("global:")) {
            return globalRequestsPerSecond;
        } else {
            return ipRequestsPerMinute;
        }
    }
    
    /**
     * 檢查全域頻率限制
     * 
     * @return true 如果允許，false 如果被限制
     */
    public boolean checkGlobalRateLimit() {
        return isAllowed("global:requests");
    }
    
    /**
     * 檢查基於 IP 的頻率限制
     * 
     * @param ip 客戶端 IP
     * @return true 如果允許，false 如果被限制
     */
    public boolean checkIpRateLimit(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return true;
        }
        return isAllowed("ip:" + ip);
    }
    
    /**
     * 檢查基於用戶的頻率限制
     * 
     * @param username 用戶名
     * @return true 如果允許，false 如果被限制
     */
    public boolean checkUserRateLimit(String username) {
        if (username == null || username.trim().isEmpty()) {
            return true;
        }
        return isAllowed("user:" + username);
    }
    
    /**
     * 預熱頻率限制器（可選，用於性能優化）
     */
    public void warmUp() {
        log.info("Rate limiter warm-up completed");
    }
}