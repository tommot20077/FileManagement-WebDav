package dowob.xyz.filemanagementwebdav.context;

import io.milton.http.Auth;
import io.milton.http.Request;
import lombok.extern.slf4j.Slf4j;

/**
 * Milton Request 持有器
 * 
 * 管理 Milton 框架的 Request 和 Auth 對象，確保在整個請求處理週期中可用。
 * 使用 ThreadLocal 存儲當前線程的 Milton Request 和認證信息。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName MiltonRequestHolder
 * @create 2025/8/17
 * @Version 1.0
 **/
@Slf4j
public class MiltonRequestHolder {
    
    private static final ThreadLocal<Request> requestHolder = new ThreadLocal<>();
    private static final ThreadLocal<Auth> authHolder = new ThreadLocal<>();
    
    /**
     * 設置當前 Milton Request
     */
    public static void setRequest(Request request) {
        requestHolder.set(request);
        log.trace("Milton request set for thread: {}", Thread.currentThread().getName());
    }
    
    /**
     * 獲取當前 Milton Request
     */
    public static Request getRequest() {
        return requestHolder.get();
    }
    
    /**
     * 設置當前認證信息
     */
    public static void setAuth(Auth auth) {
        authHolder.set(auth);
        if (auth != null && auth.getTag() != null) {
            log.debug("Milton auth set: user={}, tag={}", 
                auth.getUser(), auth.getTag().getClass().getSimpleName());
        }
    }
    
    /**
     * 獲取當前認證信息
     */
    public static Auth getAuth() {
        return authHolder.get();
    }
    
    /**
     * 清理當前線程的上下文
     */
    public static void clear() {
        requestHolder.remove();
        authHolder.remove();
        log.trace("Milton context cleared for thread: {}", Thread.currentThread().getName());
    }
    
    /**
     * 檢查是否有認證信息
     */
    public static boolean hasAuth() {
        Auth auth = authHolder.get();
        return auth != null && auth.getTag() != null;
    }
    
    /**
     * 獲取認證的用戶對象
     */
    public static Object getAuthTag() {
        Auth auth = authHolder.get();
        return auth != null ? auth.getTag() : null;
    }
}