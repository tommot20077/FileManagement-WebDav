package dowob.xyz.filemanagementwebdav.component.filter;

import dowob.xyz.filemanagementwebdav.context.MiltonRequestHolder;
import io.milton.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Milton 認證過濾器
 * 
 * 捕獲並存儲 Milton 的 Request 和 Auth 對象，
 * 確保在整個請求處理過程中可以訪問認證信息。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName MiltonAuthenticationFilter
 * @create 2025/8/17
 * @Version 1.0
 **/
@Slf4j
@Component
public class MiltonAuthenticationFilter implements Filter {
    
    @Override
    public void process(FilterChain chain, Request request, Response response) {
        try {
            // 設置 Milton Request 到持有器
            MiltonRequestHolder.setRequest(request);
            log.trace("Milton filter processing: {} {}", request.getMethod(), request.getAbsolutePath());
            
            // 檢查請求是否已經有認證信息
            Auth existingAuth = request.getAuthorization();
            if (existingAuth != null && existingAuth.getTag() != null) {
                MiltonRequestHolder.setAuth(existingAuth);
                log.debug("Found existing auth in request: user={}", existingAuth.getUser());
            }
            
            // 繼續處理鏈
            chain.process(request, response);
            
            // 請求處理完成後，如果有新的認證信息，設置回 Request
            Auth currentAuth = MiltonRequestHolder.getAuth();
            if (currentAuth != null && currentAuth != existingAuth) {
                // 有新的認證信息，更新到 Request
                request.setAuthorization(currentAuth);
                log.debug("Updated request with new auth: user={}", currentAuth.getUser());
            }
            
            // 確保認證信息保持在 Request 中
            if (MiltonRequestHolder.hasAuth()) {
                log.debug("Request completed with authentication: {}", request.getAbsolutePath());
            }
            
        } finally {
            // 清理上下文
            MiltonRequestHolder.clear();
        }
    }
}