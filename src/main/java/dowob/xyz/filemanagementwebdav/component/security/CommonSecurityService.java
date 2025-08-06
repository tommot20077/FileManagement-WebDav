package dowob.xyz.filemanagementwebdav.component.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 統一安全服務
 * 
 * 提供可被 HTTP Filter 和 gRPC Interceptor 共用的安全邏輯，
 * 包括 IP 檢測、頻率限制、權限驗證等核心功能。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName CommonSecurityService
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonSecurityService {
    
    private final IpWhitelistService ipWhitelistService;
    private final RateLimitService rateLimitService;
    private final SecurityAuditService auditService;
    
    /**
     * 安全檢查結果
     */
    public static class SecurityCheckResult {
        private final boolean allowed;
        private final String reason;
        private final SecurityAction recommendedAction;
        
        public SecurityCheckResult(boolean allowed, String reason, SecurityAction recommendedAction) {
            this.allowed = allowed;
            this.reason = reason;
            this.recommendedAction = recommendedAction;
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public SecurityAction getRecommendedAction() { return recommendedAction; }
        
        public static SecurityCheckResult allow() { 
            return new SecurityCheckResult(true, null, SecurityAction.ALLOW); 
        }
        
        public static SecurityCheckResult deny(String reason, SecurityAction action) { 
            return new SecurityCheckResult(false, reason, action); 
        }
    }
    
    /**
     * 安全操作類型
     */
    public enum SecurityAction {
        ALLOW,           // 允許
        DENY,            // 拒絕
        RATE_LIMIT,      // 頻率限制
        IP_BLOCK,        // IP 封鎖
        CAPTCHA_REQUIRED // 需要驗證碼
    }
    
    /**
     * 請求上下文
     */
    public static class RequestContext {
        private final String clientIp;
        private final String userAgent;
        private final String username;
        private final String requestPath;
        private final String requestMethod;
        private final LocalDateTime timestamp;
        
        public RequestContext(String clientIp, String userAgent, String username, 
                            String requestPath, String requestMethod) {
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.username = username;
            this.requestPath = requestPath;
            this.requestMethod = requestMethod;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getClientIp() { return clientIp; }
        public String getUserAgent() { return userAgent; }
        public String getUsername() { return username; }
        public String getRequestPath() { return requestPath; }
        public String getRequestMethod() { return requestMethod; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    /**
     * 執行綜合安全檢查
     * 
     * @param context 請求上下文
     * @return 安全檢查結果
     */
    public SecurityCheckResult performSecurityCheck(RequestContext context) {
        log.debug("Performing security check for IP: {}, User: {}, Path: {}", 
                  context.getClientIp(), context.getUsername(), context.getRequestPath());
        
        try {
            // 1. IP 白名單檢查
            SecurityCheckResult ipCheck = checkIpWhitelist(context);
            if (!ipCheck.isAllowed()) {
                auditService.logSecurityEvent(context, "IP_BLOCKED", ipCheck.getReason());
                return ipCheck;
            }
            
            // 2. 惡意 IP 檢查
            SecurityCheckResult maliciousIpCheck = checkMaliciousIp(context);
            if (!maliciousIpCheck.isAllowed()) {
                auditService.logSecurityEvent(context, "MALICIOUS_IP", maliciousIpCheck.getReason());
                return maliciousIpCheck;
            }
            
            // 3. 頻率限制檢查
            SecurityCheckResult rateLimitCheck = checkRateLimit(context);
            if (!rateLimitCheck.isAllowed()) {
                auditService.logSecurityEvent(context, "RATE_LIMITED", rateLimitCheck.getReason());
                return rateLimitCheck;
            }
            
            // 4. 用戶代理檢查
            SecurityCheckResult userAgentCheck = checkUserAgent(context);
            if (!userAgentCheck.isAllowed()) {
                auditService.logSecurityEvent(context, "SUSPICIOUS_USER_AGENT", userAgentCheck.getReason());
                return userAgentCheck;
            }
            
            // 5. 路徑安全檢查
            SecurityCheckResult pathCheck = checkPathSecurity(context);
            if (!pathCheck.isAllowed()) {
                auditService.logSecurityEvent(context, "SUSPICIOUS_PATH", pathCheck.getReason());
                return pathCheck;
            }
            
            // 所有檢查通過
            log.debug("Security check passed for IP: {}, User: {}", 
                      context.getClientIp(), context.getUsername());
            return SecurityCheckResult.allow();
            
        } catch (Exception e) {
            log.error("Error during security check", e);
            auditService.logSecurityEvent(context, "SECURITY_CHECK_ERROR", e.getMessage());
            // 安全優先：發生錯誤時拒絕訪問
            return SecurityCheckResult.deny("安全檢查失敗", SecurityAction.DENY);
        }
    }
    
    /**
     * IP 白名單檢查
     */
    private SecurityCheckResult checkIpWhitelist(RequestContext context) {
        if (!ipWhitelistService.isEnabled()) {
            return SecurityCheckResult.allow();
        }
        
        if (ipWhitelistService.isWhitelisted(context.getClientIp())) {
            return SecurityCheckResult.allow();
        }
        
        return SecurityCheckResult.deny(
            "IP 不在白名單中: " + context.getClientIp(), 
            SecurityAction.IP_BLOCK
        );
    }
    
    /**
     * 惡意 IP 檢查
     */
    private SecurityCheckResult checkMaliciousIp(RequestContext context) {
        if (ipWhitelistService.isBlacklisted(context.getClientIp())) {
            return SecurityCheckResult.deny(
                "IP 在黑名單中: " + context.getClientIp(), 
                SecurityAction.IP_BLOCK
            );
        }
        
        return SecurityCheckResult.allow();
    }
    
    /**
     * 頻率限制檢查
     */
    private SecurityCheckResult checkRateLimit(RequestContext context) {
        // 基於 IP 的頻率限制
        if (!rateLimitService.isAllowed("ip:" + context.getClientIp())) {
            return SecurityCheckResult.deny(
                "IP 頻率限制: " + context.getClientIp(), 
                SecurityAction.RATE_LIMIT
            );
        }
        
        // 基於用戶的頻率限制
        if (context.getUsername() != null && 
            !rateLimitService.isAllowed("user:" + context.getUsername())) {
            return SecurityCheckResult.deny(
                "用戶頻率限制: " + context.getUsername(), 
                SecurityAction.RATE_LIMIT
            );
        }
        
        return SecurityCheckResult.allow();
    }
    
    /**
     * 用戶代理檢查
     */
    private SecurityCheckResult checkUserAgent(RequestContext context) {
        String userAgent = context.getUserAgent();
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return SecurityCheckResult.deny("空的 User-Agent", SecurityAction.DENY);
        }
        
        // 檢查是否為已知的惡意用戶代理
        if (isSuspiciousUserAgent(userAgent)) {
            return SecurityCheckResult.deny(
                "可疑的 User-Agent: " + userAgent, 
                SecurityAction.DENY
            );
        }
        
        return SecurityCheckResult.allow();
    }
    
    /**
     * 路徑安全檢查
     */
    private SecurityCheckResult checkPathSecurity(RequestContext context) {
        String path = context.getRequestPath();
        if (path == null) {
            return SecurityCheckResult.allow();
        }
        
        // 檢查路徑遍歷攻擊
        if (containsPathTraversal(path)) {
            return SecurityCheckResult.deny(
                "路徑遍歷攻擊: " + path, 
                SecurityAction.DENY
            );
        }
        
        // 檢查隱藏文件訪問
        if (containsHiddenFileAccess(path)) {
            return SecurityCheckResult.deny(
                "嘗試訪問隱藏文件: " + path, 
                SecurityAction.DENY
            );
        }
        
        return SecurityCheckResult.allow();
    }
    
    /**
     * 檢查是否為可疑的用戶代理
     */
    private boolean isSuspiciousUserAgent(String userAgent) {
        List<Pattern> suspiciousPatterns = List.of(
            Pattern.compile("(?i).*bot.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*crawler.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*spider.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*scanner.*", Pattern.CASE_INSENSITIVE)
        );
        
        return suspiciousPatterns.stream()
                .anyMatch(pattern -> pattern.matcher(userAgent).matches());
    }
    
    /**
     * 檢查路徑遍歷
     */
    private boolean containsPathTraversal(String path) {
        return path.contains("../") || 
               path.contains("..\\") || 
               path.contains("%2e%2e") ||
               path.contains("....//");
    }
    
    /**
     * 檢查隱藏文件訪問
     */
    private boolean containsHiddenFileAccess(String path) {
        return path.contains("/.") || 
               path.contains("\\.") || 
               path.startsWith(".") ||
               path.contains("__") ||
               path.toLowerCase().contains("passwd") ||
               path.toLowerCase().contains("shadow");
    }
    
    /**
     * 記錄安全事件（用於後續分析）
     */
    public void logSecurityEvent(RequestContext context, String eventType, String details) {
        auditService.logSecurityEvent(context, eventType, details);
    }
    
    /**
     * 獲取客戶端真實 IP
     */
    public String getRealClientIp(String xForwardedFor, String xRealIp, String remoteAddr) {
        // 檢查 X-Forwarded-For 標頭（可能包含多個 IP）
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim(); // 取第一個 IP
        }
        
        // 檢查 X-Real-IP 標頭
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        
        // 使用遠程地址
        return remoteAddr;
    }
}