package dowob.xyz.filemanagementwebdav.context;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 請求上下文持有器
 * 
 * 使用 ThreadLocal 存儲當前請求的上下文信息
 * 適配 Servlet 環境的線程模型
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName RequestContextHolder
 * @create 2025/8/5
 * @Version 1.0
 **/
@Slf4j
public class RequestContextHolder {
    
    private static final ThreadLocal<RequestContext> contextHolder = new ThreadLocal<>();
    
    /**
     * 設置請求上下文
     * 
     * @param context 請求上下文
     */
    public static void setContext(RequestContext context) {
        contextHolder.set(context);
        log.debug("Request context set: {}", context.getRequestId());
    }
    
    /**
     * 獲取當前請求上下文
     * 
     * @return 請求上下文，如果不存在則返回 null
     */
    public static RequestContext getContext() {
        return contextHolder.get();
    }
    
    /**
     * 清理請求上下文
     * 必須在請求結束時調用，避免內存洩漏
     */
    public static void clearContext() {
        RequestContext context = contextHolder.get();
        if (context != null) {
            log.debug("Request context cleared: {}", context.getRequestId());
            contextHolder.remove();
        }
    }
    
    /**
     * 從 HTTP 請求創建上下文
     * 
     * @param request HTTP 請求
     * @param clientIp 客戶端 IP
     * @param userAgent 用戶代理
     * @return 請求上下文
     */
    public static RequestContext createFromRequest(HttpServletRequest request, String clientIp, String userAgent) {
        return RequestContext.builder()
            .requestId(generateRequestId())
            .clientIp(clientIp)
            .userAgent(userAgent)
            .requestUri(request.getRequestURI())
            .method(request.getMethod())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * 生成請求 ID
     * 
     * @return 唯一的請求 ID
     */
    private static String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 請求上下文數據類
     */
    @Data
    @Builder
    public static class RequestContext {
        /**
         * 請求唯一標識符
         */
        private String requestId;
        
        /**
         * 客戶端 IP 地址
         */
        private String clientIp;
        
        /**
         * 用戶代理字符串
         */
        private String userAgent;
        
        /**
         * 請求 URI
         */
        private String requestUri;
        
        /**
         * HTTP 方法
         */
        private String method;
        
        /**
         * 請求時間戳
         */
        private LocalDateTime timestamp;
        
        /**
         * 認證用戶 ID（認證後設置）
         */
        private String userId;
        
        /**
         * 認證用戶名（認證後設置）
         */
        private String username;
        
        /**
         * 設置認證用戶信息
         * 
         * @param userId 用戶 ID
         * @param username 用戶名
         */
        public void setAuthenticatedUser(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }
        
        /**
         * 檢查是否已認證
         * 
         * @return true 如果已認證
         */
        public boolean isAuthenticated() {
            return userId != null && username != null;
        }
        
        /**
         * 獲取用於日誌的標識字符串
         * 
         * @return 日誌標識
         */
        public String getLogIdentifier() {
            StringBuilder sb = new StringBuilder();
            sb.append("[RequestID: ").append(requestId);
            
            if (clientIp != null) {
                sb.append("] [IP: ").append(clientIp);
            }
            
            if (isAuthenticated()) {
                sb.append("] [User: ").append(username);
            }
            
            sb.append("]");
            return sb.toString();
        }
    }
}