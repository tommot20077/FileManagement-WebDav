package dowob.xyz.filemanagementwebdav.component.filter;

import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import dowob.xyz.filemanagementwebdav.utils.ClientIpResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * WebDAV 請求上下文過濾器
 * 
 * 在每個請求開始時設置上下文信息，請求結束時清理
 * 確保整個請求生命週期內都能訪問到客戶端信息
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavRequestContextFilter
 * @create 2025/8/5
 * @Version 1.0
 **/
@Slf4j
@Component("webDavRequestContextFilter")
@Order(1) // 在 SecurityFilter 之後執行，設置請求上下文
@RequiredArgsConstructor
public class WebDavRequestContextFilter implements Filter {
    
    private final ClientIpResolver clientIpResolver;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // 只處理 HTTP 請求
        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            // 1. 獲取客戶端信息
            String clientIp = clientIpResolver.getClientIp(httpRequest);
            String userAgent = clientIpResolver.getUserAgent(httpRequest);
            
            // 2. 創建並設置請求上下文
            RequestContextHolder.RequestContext context = 
                RequestContextHolder.createFromRequest(httpRequest, clientIp, userAgent);
            
            RequestContextHolder.setContext(context);
            
            log.debug("Request context initialized: {} {} from {}", 
                     context.getMethod(), context.getRequestUri(), context.getClientIp());
            
            // 3. 繼續過濾器鏈
            chain.doFilter(request, response);
            
        } finally {
            // 4. 清理上下文（避免內存洩漏）
            try {
                RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
                if (context != null) {
                    log.debug("Request completed: {}", context.getLogIdentifier());
                }
            } catch (Exception e) {
                log.warn("Error while logging request completion", e);
            }
            
            RequestContextHolder.clearContext();
        }
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("WebDAV RequestContextFilter initialized");
    }
    
    @Override
    public void destroy() {
        log.info("WebDAV RequestContextFilter destroyed");
    }
}