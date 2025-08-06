package dowob.xyz.filemanagementwebdav.component.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingProto;
import dowob.xyz.filemanagementwebdav.service.GrpcClientService;
import dowob.xyz.filemanagementwebdav.utils.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT 撤銷檢查服務
 * 
 * 負責檢查 JWT token 是否已被撤銷。
 * 支持本地快取和遠程 gRPC 服務檢查。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName JwtRevocationService
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Service
public class JwtRevocationService {
    
    private final GrpcClientService grpcClientService;
    private final Cache<String, RevocationCacheEntry> revocationCache;
    private final int cacheExpireMinutes;
    
    public JwtRevocationService(
            GrpcClientService grpcClientService,
            @Value("${webdav.security.jwt.revocation.cache.expire-minutes:10}") int cacheExpireMinutes,
            @Value("${webdav.security.jwt.revocation.cache.max-size:5000}") int cacheMaxSize) {
        
        this.grpcClientService = grpcClientService;
        this.cacheExpireMinutes = cacheExpireMinutes;
        
        // 初始化撤銷快取
        this.revocationCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(cacheExpireMinutes, TimeUnit.MINUTES)
                .removalListener((key, entry, cause) -> {
                    if (entry != null) {
                        log.debug("JWT revocation cache entry removed for token: {}, cause: {}", 
                                maskToken((String) key), cause);
                    }
                })
                .recordStats()
                .build();
        
        log.info("JwtRevocationService initialized - Cache always enabled, expire: {} minutes", 
                cacheExpireMinutes);
    }
    
    /**
     * 檢查 JWT token 是否已被撤銷
     * 
     * @param jwtToken JWT token
     * @param tokenId token ID（可選，用於優化查詢）
     * @param userId 用戶 ID（可選，用於優化查詢）
     * @return RevocationCheckResult 撤銷檢查結果
     */
    public RevocationCheckResult isTokenRevoked(String jwtToken, String tokenId, String userId) {
        if (jwtToken == null || jwtToken.trim().isEmpty()) {
            return RevocationCheckResult.error("Token is null or empty");
        }
        
        try {
            // 1. 檢查本地快取
            RevocationCacheEntry cacheEntry = revocationCache.getIfPresent(jwtToken);
            if (cacheEntry != null && !cacheEntry.isExpired()) {
                log.debug("JWT revocation check - cache hit for token: {}", maskToken(jwtToken));
                return cacheEntry.isRevoked() 
                    ? RevocationCheckResult.revoked("Token is revoked (cached)")
                    : RevocationCheckResult.valid("Token is valid (cached)");
            }
            
            // 2. 調用遠程服務檢查
            RevocationCheckResult result = checkRevocationRemotely(jwtToken, tokenId, userId);
            
            // 3. 更新快取
            if (result.isSuccess()) {
                RevocationCacheEntry newCacheEntry = new RevocationCacheEntry(
                    result.isRevoked(), 
                    LocalDateTime.now().plusMinutes(cacheExpireMinutes)
                );
                revocationCache.put(jwtToken, newCacheEntry);
                log.debug("JWT revocation result cached for token: {}", maskToken(jwtToken));
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error checking JWT revocation for token: {}", maskToken(jwtToken), e);
            return RevocationCheckResult.error("Internal error during revocation check: " + e.getMessage());
        }
    }
    
    /**
     * 通過 gRPC 遠程檢查 token 撤銷狀態
     */
    private RevocationCheckResult checkRevocationRemotely(String jwtToken, String tokenId, String userId) {
        try {
            // 調用 gRPC 服務（使用臨時實現）
            GrpcClientService.JwtRevocationCheckResult grpcResult = 
                grpcClientService.checkJwtRevocation(jwtToken, tokenId, userId);
            
            if (grpcResult.isSuccess()) {
                if (grpcResult.isRevoked()) {
                    LogUtils.logSecurity("JWT_REVOCATION_CHECK", "Token is revoked: " + maskToken(jwtToken), "WARN");
                    return RevocationCheckResult.revoked(grpcResult.getMessage());
                } else {
                    log.debug("JWT revocation check - token is valid: {}", maskToken(jwtToken));
                    return RevocationCheckResult.valid(grpcResult.getMessage());
                }
            } else {
                log.warn("JWT revocation check failed: {}", grpcResult.getMessage());
                return RevocationCheckResult.error(grpcResult.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error during remote JWT revocation check", e);
            return RevocationCheckResult.error("Failed to check revocation status: " + e.getMessage());
        }
    }
    
    /**
     * 使撤銷快取失效
     * 
     * @param jwtToken JWT token
     */
    public void invalidateCache(String jwtToken) {
        if (jwtToken != null) {
            revocationCache.invalidate(jwtToken);
            log.debug("JWT revocation cache invalidated for token: {}", maskToken(jwtToken));
        }
    }
    
    /**
     * 清除所有撤銷快取
     */
    public void clearCache() {
        revocationCache.invalidateAll();
        log.info("All JWT revocation cache cleared");
    }
    
    /**
     * 獲取撤銷服務統計信息
     */
    public String getStats() {
        var stats = revocationCache.stats();
        return String.format(
            "JWT Revocation Cache - Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%", 
            revocationCache.estimatedSize(),
            stats.hitCount(),
            stats.missCount(),
            stats.hitRate() * 100
        );
    }
    
    /**
     * 遮蔽 token 用於日誌記錄
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 20) {
            return "***";
        }
        return token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }
    
    /**
     * 撤銷快取條目
     */
    private static class RevocationCacheEntry {
        private final boolean revoked;
        private final LocalDateTime expiresAt;
        
        public RevocationCacheEntry(boolean revoked, LocalDateTime expiresAt) {
            this.revoked = revoked;
            this.expiresAt = expiresAt;
        }
        
        public boolean isRevoked() {
            return revoked;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
    
    /**
     * 撤銷檢查結果
     */
    public static class RevocationCheckResult {
        private final boolean success;
        private final boolean revoked;
        private final String message;
        
        private RevocationCheckResult(boolean success, boolean revoked, String message) {
            this.success = success;
            this.revoked = revoked;
            this.message = message;
        }
        
        public static RevocationCheckResult valid(String message) {
            return new RevocationCheckResult(true, false, message);
        }
        
        public static RevocationCheckResult revoked(String message) {
            return new RevocationCheckResult(true, true, message);
        }
        
        public static RevocationCheckResult error(String message) {
            return new RevocationCheckResult(false, false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public boolean isRevoked() {
            return revoked;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isValid() {
            return success && !revoked;
        }
    }
}