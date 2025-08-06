package dowob.xyz.filemanagementwebdav.component.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 安全審計服務
 * 
 * 負責記錄和分析安全相關事件，提供安全監控和威脅檢測功能。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName SecurityAuditService
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    
    private final ObjectMapper objectMapper;
    
    // 異步執行器，用於非阻塞的日誌記錄
    private final ExecutorService auditExecutor = Executors.newFixedThreadPool(2);
    
    // 審計配置 - 在專用 WebDAV 子服務中永遠啟用
    
    @Value("${webdav.security.audit.include-request-details:true}")
    private boolean includeRequestDetails;
    
    @Value("${webdav.security.audit.sensitive-data-mask:true}")
    private boolean maskSensitiveData;
    
    /**
     * 安全事件級別
     */
    public enum SecurityEventLevel {
        INFO,    // 一般信息
        WARN,    // 警告
        ERROR,   // 錯誤
        CRITICAL // 嚴重安全事件
    }
    
    /**
     * 安全事件類型
     */
    public enum SecurityEventType {
        AUTHENTICATION_SUCCESS,
        AUTHENTICATION_FAILURE,
        AUTHORIZATION_FAILURE,
        IP_BLOCKED,
        RATE_LIMITED,
        SUSPICIOUS_ACTIVITY,
        MALICIOUS_REQUEST,
        SYSTEM_ERROR
    }
    
    /**
     * 記錄安全事件
     * 
     * @param context 請求上下文
     * @param eventType 事件類型
     * @param details 事件詳情
     */
    public void logSecurityEvent(CommonSecurityService.RequestContext context, 
                                String eventType, String details) {
        logSecurityEvent(context, eventType, details, SecurityEventLevel.INFO);
    }
    
    /**
     * 記錄安全事件（指定級別）
     * 
     * @param context 請求上下文
     * @param eventType 事件類型
     * @param details 事件詳情
     * @param level 事件級別
     */
    public void logSecurityEvent(CommonSecurityService.RequestContext context, 
                                String eventType, String details, SecurityEventLevel level) {
        
        // 異步記錄以避免影響主線程性能
        CompletableFuture.runAsync(() -> {
            try {
                SecurityEvent event = createSecurityEvent(context, eventType, details, level);
                writeSecurityEvent(event);
            } catch (Exception e) {
                log.error("Error writing security audit log", e);
            }
        }, auditExecutor);
    }
    
    /**
     * 記錄身份驗證事件
     * 
     * @param username 用戶名
     * @param clientIp 客戶端 IP
     * @param userAgent 用戶代理
     * @param success 是否成功
     * @param errorMessage 錯誤消息（如果有）
     */
    public void logAuthenticationEvent(String username, String clientIp, String userAgent, 
                                     boolean success, String errorMessage) {
        
        CommonSecurityService.RequestContext context = new CommonSecurityService.RequestContext(
            clientIp, userAgent, username, "/authenticate", "POST"
        );
        
        String eventType = success ? "AUTHENTICATION_SUCCESS" : "AUTHENTICATION_FAILURE";
        SecurityEventLevel level = success ? SecurityEventLevel.INFO : SecurityEventLevel.WARN;
        String details = success ? "用戶成功登入" : "用戶登入失敗: " + errorMessage;
        
        logSecurityEvent(context, eventType, details, level);
    }
    
    /**
     * 記錄可疑活動
     * 
     * @param context 請求上下文
     * @param suspiciousActivity 可疑活動描述
     * @param riskLevel 風險級別 (1-10)
     */
    public void logSuspiciousActivity(CommonSecurityService.RequestContext context, 
                                    String suspiciousActivity, int riskLevel) {
        SecurityEventLevel level = riskLevel >= 8 ? SecurityEventLevel.CRITICAL :
                                 riskLevel >= 5 ? SecurityEventLevel.ERROR : 
                                 SecurityEventLevel.WARN;
        
        String details = String.format("可疑活動 (風險級別: %d): %s", riskLevel, suspiciousActivity);
        logSecurityEvent(context, "SUSPICIOUS_ACTIVITY", details, level);
    }
    
    /**
     * 記錄系統錯誤
     * 
     * @param context 請求上下文
     * @param error 錯誤信息
     * @param exception 異常對象
     */
    public void logSystemError(CommonSecurityService.RequestContext context, 
                              String error, Throwable exception) {
        String details = error;
        if (exception != null) {
            details += ": " + exception.getMessage();
        }
        
        logSecurityEvent(context, "SYSTEM_ERROR", details, SecurityEventLevel.ERROR);
    }
    
    /**
     * 創建安全事件對象
     */
    private SecurityEvent createSecurityEvent(CommonSecurityService.RequestContext context, 
                                            String eventType, String details, SecurityEventLevel level) {
        SecurityEvent event = new SecurityEvent();
        event.timestamp = LocalDateTime.now();
        event.eventType = eventType;
        event.level = level.name();
        event.details = details;
        
        if (context != null) {
            event.clientIp = maskSensitiveData ? maskIp(context.getClientIp()) : context.getClientIp();
            event.username = maskSensitiveData ? maskUsername(context.getUsername()) : context.getUsername();
            event.userAgent = context.getUserAgent();
            
            if (includeRequestDetails) {
                event.requestPath = context.getRequestPath();
                event.requestMethod = context.getRequestMethod();
            }
        }
        
        return event;
    }
    
    /**
     * 寫入安全事件到日誌
     */
    private void writeSecurityEvent(SecurityEvent event) {
        try {
            String jsonEvent = objectMapper.writeValueAsString(event);
            
            // 根據事件級別選擇不同的日誌級別
            switch (SecurityEventLevel.valueOf(event.level)) {
                case INFO -> log.info("SECURITY_AUDIT: {}", jsonEvent);
                case WARN -> log.warn("SECURITY_AUDIT: {}", jsonEvent);
                case ERROR -> log.error("SECURITY_AUDIT: {}", jsonEvent);
                case CRITICAL -> {
                    log.error("CRITICAL_SECURITY_EVENT: {}", jsonEvent);
                    // 嚴重事件可能需要額外的通知機制
                    handleCriticalSecurityEvent(event);
                }
            }
            
        } catch (JsonProcessingException e) {
            log.error("Error serializing security event", e);
            // 備用方案：直接記錄文本
            log.warn("SECURITY_AUDIT_FALLBACK: {} - {} - {}", 
                    event.timestamp, event.eventType, event.details);
        }
    }
    
    /**
     * 處理嚴重安全事件
     */
    private void handleCriticalSecurityEvent(SecurityEvent event) {
        // 這裡可以實現：
        // 1. 發送郵件通知
        // 2. 推送到監控系統
        // 3. 觸發自動防護措施
        log.error("CRITICAL SECURITY EVENT DETECTED: {}", event.eventType);
        
        // 示例：如果檢測到持續的惡意活動，可以自動添加到黑名單
        if ("MALICIOUS_REQUEST".equals(event.eventType) && event.clientIp != null) {
            // 這裡可以調用 IpWhitelistService 自動添加到黑名單
            log.warn("Consider adding IP {} to blacklist due to critical security event", event.clientIp);
        }
    }
    
    /**
     * 遮蔽 IP 地址
     */
    private String maskIp(String ip) {
        if (ip == null || ip.length() < 7) {
            return "***";
        }
        
        // IPv4: 192.168.1.100 -> 192.168.*.**
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.**";
            }
        }
        
        // IPv6 或其他格式的簡單遮蔽
        return ip.substring(0, ip.length() / 2) + "***";
    }
    
    /**
     * 遮蔽用戶名
     */
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        
        if (username.length() <= 4) {
            return username.charAt(0) + "***";
        }
        
        return username.substring(0, 2) + "***" + username.substring(username.length() - 1);
    }
    
    /**
     * 獲取審計統計信息
     */
    public Map<String, Object> getAuditStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("auditEnabled", true); // 永遠啟用
        stats.put("includeRequestDetails", includeRequestDetails);
        stats.put("maskSensitiveData", maskSensitiveData);
        stats.put("executorActiveThreads", ((java.util.concurrent.ThreadPoolExecutor) auditExecutor).getActiveCount());
        stats.put("executorQueueSize", ((java.util.concurrent.ThreadPoolExecutor) auditExecutor).getQueue().size());
        return stats;
    }
    
    /**
     * 關閉審計服務
     */
    public void shutdown() {
        auditExecutor.shutdown();
        log.info("SecurityAuditService shutdown completed");
    }
    
    /**
     * 安全事件數據類
     */
    private static class SecurityEvent {
        public LocalDateTime timestamp;
        public String eventType;
        public String level;
        public String details;
        public String clientIp;
        public String username;
        public String userAgent;
        public String requestPath;
        public String requestMethod;
        
        // 添加 getter 方法以便 Jackson 序列化
        public String getTimestamp() {
            return timestamp != null ? timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        }
        
        public String getEventType() { return eventType; }
        public String getLevel() { return level; }
        public String getDetails() { return details; }
        public String getClientIp() { return clientIp; }
        public String getUsername() { return username; }
        public String getUserAgent() { return userAgent; }
        public String getRequestPath() { return requestPath; }
        public String getRequestMethod() { return requestMethod; }
    }
}