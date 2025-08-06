package dowob.xyz.filemanagementwebdav.controller;

import dowob.xyz.filemanagementwebdav.component.security.CommonSecurityService;
import dowob.xyz.filemanagementwebdav.component.security.IpWhitelistService;
import dowob.xyz.filemanagementwebdav.component.security.RateLimitService;
import dowob.xyz.filemanagementwebdav.component.security.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全管理控制器
 * <p>
 * 提供安全組件的管理和監控端點，包括：
 * - IP 白名單/黑名單管理
 * - 頻率限制管理
 * - 安全統計查看
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName SecurityManagementController
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityManagementController {
    
    private final CommonSecurityService securityService;
    private final IpWhitelistService ipWhitelistService;
    private final RateLimitService rateLimitService;
    private final SecurityAuditService auditService;
    
    /**
     * 獲取安全統計信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSecurityStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // IP 白名單統計
            stats.put("ipWhitelist", Map.of(
                "enabled", ipWhitelistService.isEnabled(),
                "stats", ipWhitelistService.getWhitelistStats()
            ));
            
            // IP 黑名單統計
            stats.put("ipBlacklist", Map.of(
                "stats", ipWhitelistService.getBlacklistStats()
            ));
            
            // 頻率限制統計
            stats.put("rateLimit", Map.of(
                "stats", rateLimitService.getStats()
            ));
            
            // 審計統計
            stats.put("audit", auditService.getAuditStats());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting security stats", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "獲取安全統計失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 添加 IP 到白名單
     */
    @PostMapping("/whitelist/add")
    public ResponseEntity<Map<String, String>> addToWhitelist(@RequestBody Map<String, String> request) {
        String ip = request.get("ip");
        
        if (ip == null || ip.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "IP 地址不能為空"));
        }
        
        try {
            ipWhitelistService.addToWhitelist(ip.trim());
            log.info("IP {} added to whitelist", ip);
            return ResponseEntity.ok(Map.of("message", "IP 已添加到白名單: " + ip));
            
        } catch (Exception e) {
            log.error("Error adding IP to whitelist", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "添加 IP 到白名單失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 從白名單移除 IP
     */
    @PostMapping("/whitelist/remove")
    public ResponseEntity<Map<String, String>> removeFromWhitelist(@RequestBody Map<String, String> request) {
        String ip = request.get("ip");
        
        if (ip == null || ip.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "IP 地址不能為空"));
        }
        
        try {
            ipWhitelistService.removeFromWhitelist(ip.trim());
            log.info("IP {} removed from whitelist", ip);
            return ResponseEntity.ok(Map.of("message", "IP 已從白名單移除: " + ip));
            
        } catch (Exception e) {
            log.error("Error removing IP from whitelist", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "從白名單移除 IP 失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 添加 IP 到黑名單
     */
    @PostMapping("/blacklist/add")
    public ResponseEntity<Map<String, String>> addToBlacklist(@RequestBody Map<String, String> request) {
        String ip = request.get("ip");
        
        if (ip == null || ip.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "IP 地址不能為空"));
        }
        
        try {
            ipWhitelistService.addToBlacklist(ip.trim());
            log.info("IP {} added to blacklist", ip);
            return ResponseEntity.ok(Map.of("message", "IP 已添加到黑名單: " + ip));
            
        } catch (Exception e) {
            log.error("Error adding IP to blacklist", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "添加 IP 到黑名單失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 從黑名單移除 IP
     */
    @PostMapping("/blacklist/remove")
    public ResponseEntity<Map<String, String>> removeFromBlacklist(@RequestBody Map<String, String> request) {
        String ip = request.get("ip");
        
        if (ip == null || ip.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "IP 地址不能為空"));
        }
        
        try {
            ipWhitelistService.removeFromBlacklist(ip.trim());
            log.info("IP {} removed from blacklist", ip);
            return ResponseEntity.ok(Map.of("message", "IP 已從黑名單移除: " + ip));
            
        } catch (Exception e) {
            log.error("Error removing IP from blacklist", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "從黑名單移除 IP 失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 檢查 IP 狀態
     */
    @GetMapping("/ip/check")
    public ResponseEntity<Map<String, Object>> checkIpStatus(@RequestParam String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "IP 地址不能為空"));
        }
        
        try {
            boolean isWhitelisted = ipWhitelistService.isWhitelisted(ip);
            boolean isBlacklisted = ipWhitelistService.isBlacklisted(ip);
            
            Map<String, Object> status = Map.of(
                "ip", ip,
                "whitelisted", isWhitelisted,
                "blacklisted", isBlacklisted,
                "status", isBlacklisted ? "blocked" : (isWhitelisted ? "allowed" : "default")
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error checking IP status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "檢查 IP 狀態失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 清除頻率限制
     */
    @PostMapping("/rate-limit/clear")
    public ResponseEntity<Map<String, String>> clearRateLimit(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        
        if (key == null || key.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "限制鍵不能為空"));
        }
        
        try {
            rateLimitService.clearRateLimit(key.trim());
            log.info("Rate limit cleared for key: {}", key);
            return ResponseEntity.ok(Map.of("message", "頻率限制已清除: " + key));
            
        } catch (Exception e) {
            log.error("Error clearing rate limit", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "清除頻率限制失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 清除所有頻率限制
     */
    @PostMapping("/rate-limit/clear-all")
    public ResponseEntity<Map<String, String>> clearAllRateLimits() {
        try {
            rateLimitService.clearAllRateLimits();
            log.info("All rate limits cleared");
            return ResponseEntity.ok(Map.of("message", "所有頻率限制已清除"));
            
        } catch (Exception e) {
            log.error("Error clearing all rate limits", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "清除所有頻率限制失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取頻率限制狀態
     */
    @GetMapping("/rate-limit/status")
    public ResponseEntity<Map<String, Object>> getRateLimitStatus(@RequestParam String key) {
        if (key == null || key.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "限制鍵不能為空"));
        }
        
        try {
            int remaining = rateLimitService.getRemainingRequests(key);
            int current = rateLimitService.getCurrentRequestCount(key);
            
            Map<String, Object> status = Map.of(
                "key", key,
                "remainingRequests", remaining,
                "currentRequests", current,
                "isAllowed", rateLimitService.isAllowed(key)
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting rate limit status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "獲取頻率限制狀態失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 清空快取
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCaches() {
        try {
            ipWhitelistService.clearCache();
            rateLimitService.clearAllRateLimits();
            
            log.info("All security caches cleared");
            return ResponseEntity.ok(Map.of("message", "所有安全快取已清空"));
            
        } catch (Exception e) {
            log.error("Error clearing caches", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "清空快取失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 執行安全檢查（測試端點）
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> performSecurityCheck(
            @RequestBody Map<String, String> request) {
        
        String clientIp = request.get("clientIp");
        String userAgent = request.get("userAgent");
        String username = request.get("username");
        String path = request.get("path");
        String method = request.get("method");
        
        if (clientIp == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "clientIp 不能為空"));
        }
        
        try {
            CommonSecurityService.RequestContext context = 
                new CommonSecurityService.RequestContext(clientIp, userAgent, username, path, method);
            
            CommonSecurityService.SecurityCheckResult result = 
                securityService.performSecurityCheck(context);
            
            Map<String, Object> response = Map.of(
                "allowed", result.isAllowed(),
                "reason", result.getReason() != null ? result.getReason() : "N/A",
                "action", result.getRecommendedAction().name()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error performing security check", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "執行安全檢查失敗: " + e.getMessage()));
        }
    }
}