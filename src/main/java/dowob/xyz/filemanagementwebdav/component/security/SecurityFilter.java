package dowob.xyz.filemanagementwebdav.component.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 安全過濾器
 * 
 * 實現 HTTP 請求的安全檢查，集成統一安全服務。
 * 在所有其他過濾器之前執行安全檢查。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName SecurityFilter
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // 確保在其他過濾器之前執行
public class SecurityFilter implements Filter {
    
    private final CommonSecurityService securityService;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("SecurityFilter initialized");
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // 創建請求上下文
            CommonSecurityService.RequestContext context = createRequestContext(httpRequest);
            
            // 執行安全檢查
            CommonSecurityService.SecurityCheckResult result = securityService.performSecurityCheck(context);
            
            if (!result.isAllowed()) {
                // 安全檢查失敗，拒絕請求
                handleSecurityViolation(httpResponse, result);
                return;
            }
            
            // 添加安全標頭
            addSecurityHeaders(httpResponse);
            
            // 繼續過濾鏈
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error in SecurityFilter", e);
            
            // 記錄安全事件
            CommonSecurityService.RequestContext context = createRequestContext(httpRequest);
            securityService.logSecurityEvent(context, "SECURITY_FILTER_ERROR", e.getMessage());
            
            // 安全優先：發生錯誤時拒絕請求
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "安全檢查失敗");
        }
    }
    
    @Override
    public void destroy() {
        log.info("SecurityFilter destroyed");
    }
    
    /**
     * 創建請求上下文
     */
    private CommonSecurityService.RequestContext createRequestContext(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String requestPath = request.getRequestURI();
        String requestMethod = request.getMethod();
        
        // 嘗試從請求中獲取用戶名（如果已認證）
        String username = extractUsername(request);
        
        return new CommonSecurityService.RequestContext(
            clientIp, userAgent, username, requestPath, requestMethod
        );
    }
    
    /**
     * 獲取客戶端真實 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        String remoteAddr = request.getRemoteAddr();
        
        return securityService.getRealClientIp(xForwardedFor, xRealIp, remoteAddr);
    }
    
    /**
     * 從請求中提取用戶名
     */
    private String extractUsername(HttpServletRequest request) {
        // 嘗試從不同來源獲取用戶名
        
        // 1. 從 Principal 獲取（如果使用 Spring Security）
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }
        
        // 2. 從 HTTP Basic Auth 獲取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring("Basic ".length());
                String credentials = new String(java.util.Base64.getDecoder().decode(base64Credentials));
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2) {
                    return parts[0]; // 用戶名
                }
            } catch (Exception e) {
                log.debug("Error parsing Basic Auth header", e);
            }
        }
        
        // 3. 從會話獲取（如果有自定義會話管理）
        Object sessionUser = request.getSession(false) != null ? 
                            request.getSession().getAttribute("username") : null;
        if (sessionUser != null) {
            return sessionUser.toString();
        }
        
        return null; // 未認證用戶
    }
    
    /**
     * 處理安全違規
     */
    private void handleSecurityViolation(HttpServletResponse response, 
                                       CommonSecurityService.SecurityCheckResult result) 
            throws IOException {
        
        // 根據不同的安全動作設置相應的 HTTP 狀態碼
        int statusCode = switch (result.getRecommendedAction()) {
            case IP_BLOCK -> HttpServletResponse.SC_FORBIDDEN;
            case RATE_LIMIT -> 429; // Too Many Requests
            case CAPTCHA_REQUIRED -> HttpServletResponse.SC_UNAUTHORIZED;
            case DENY -> HttpServletResponse.SC_FORBIDDEN;
            default -> HttpServletResponse.SC_FORBIDDEN;
        };
        
        // 設置響應標頭
        response.setHeader("X-Security-Reason", result.getRecommendedAction().name());
        response.setContentType("application/json;charset=UTF-8");
        
        // 根據狀態碼設置不同的錯誤消息
        String errorMessage = switch (result.getRecommendedAction()) {
            case IP_BLOCK -> "您的 IP 地址被禁止訪問";
            case RATE_LIMIT -> "請求頻率過高，請稍後再試";
            case CAPTCHA_REQUIRED -> "需要完成安全驗證";
            default -> "訪問被拒絕";
        };
        
        // 發送錯誤響應
        response.setStatus(statusCode);
        response.getWriter().write(String.format(
            "{\"error\":\"%s\",\"reason\":\"%s\",\"timestamp\":\"%s\"}", 
            errorMessage, 
            result.getReason(), 
            java.time.LocalDateTime.now()
        ));
        
        log.info("Security violation handled: {} - {}", statusCode, result.getReason());
    }
    
    /**
     * 添加安全標頭
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        // 防止點擊劫持
        response.setHeader("X-Frame-Options", "DENY");
        
        // 防止 MIME 類型嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // XSS 保護
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // 強制 HTTPS（如果是 HTTPS 環境）
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        
        // 內容安全策略（根據需要調整）
        response.setHeader("Content-Security-Policy", "default-src 'self'");
        
        // 引用者策略
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // 權限策略
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    }
}