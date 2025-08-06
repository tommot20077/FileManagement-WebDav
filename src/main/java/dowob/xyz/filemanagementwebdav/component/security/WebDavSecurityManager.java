package dowob.xyz.filemanagementwebdav.component.security;

import dowob.xyz.filemanagementwebdav.component.cache.AuthenticationCache;
import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingProto;
import dowob.xyz.filemanagementwebdav.service.GrpcClientService;
import dowob.xyz.filemanagementwebdav.utils.LogUtils;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.Request.Method;
import io.milton.http.SecurityManager;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebDAV 安全管理器實現
 * 
 * 負責處理 WebDAV 請求的身份驗證和授權。
 * 通過 gRPC 調用主服務進行實際的身份驗證。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavSecurityManager
 * @create 2025/8/5
 * @Version 1.0
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class WebDavSecurityManager implements SecurityManager {
    
    private final GrpcClientService grpcClientService;
    private final AuthenticationCache authCache;
    private final JwtService jwtService;
    private final JwtRevocationService jwtRevocationService;
    
    /**
     * 驗證用戶憑證
     * 
     * @param user 用戶名
     * @param password 密碼或 JWT token
     * @return 驗證成功返回用戶對象，失敗返回 null
     */
    @Override
    public Object authenticate(String user, String password) {
        log.debug("Authenticating user: {}", user);
        
        try {
            // 1. 檢查是否為 JWT token
            if (jwtService.isJwtFormat(password)) {
                return authenticateWithJwt(user, password);
            }
            
            // 2. 傳統密碼認證 - 先檢查快取
            AuthenticationCache.AuthCacheEntry cacheEntry = authCache.get(user, password);
            if (cacheEntry != null) {
                if (cacheEntry.isAuthenticated()) {
                    log.debug("User {} authenticated from cache", user);
                    return new AuthenticatedUser(
                        cacheEntry.getUserId(),
                        cacheEntry.getUsername(),
                        null  // TODO: 可能需要在快取中也存儲角色信息
                    );
                } else {
                    log.debug("User {} authentication failed (cached result)", user);
                    return null;
                }
            }
            
            // 3. 快取未命中，調用 gRPC 服務
            return authenticateWithPassword(user, password);
            
        } catch (Exception e) {
            log.error("Error during authentication for user: {}", user, e);
            return null;
        }
    }
    
    /**
     * 使用 JWT token 進行身份驗證
     * 
     * @param requestedUser 請求的用戶名
     * @param jwtToken JWT token
     * @return 驗證成功返回用戶對象，失敗返回 null
     */
    private Object authenticateWithJwt(String requestedUser, String jwtToken) {
        log.debug("Attempting JWT authentication for user: {}", requestedUser);
        
        // 驗證 JWT token
        JwtService.JwtValidationResult jwtResult = jwtService.validateToken(jwtToken);
        
        if (!jwtResult.isValid()) {
            LogUtils.logAuthentication("JWT_LOGIN", requestedUser, false, 
                "JWT validation failed: " + jwtResult.getErrorMessage());
            return null;
        }
        
        // 檢查用戶名是否匹配（可選，根據業務需求決定）
        String jwtUsername = jwtResult.getUsername();
        if (!requestedUser.equals(jwtUsername)) {
            LogUtils.logAuthentication("JWT_LOGIN", requestedUser, false, 
                String.format("Username mismatch: requested=%s, jwt=%s", requestedUser, jwtUsername));
            return null;
        }
        
        // 檢查 JWT 是否被撤銷
        JwtRevocationService.RevocationCheckResult revocationResult = 
            jwtRevocationService.isTokenRevoked(jwtToken, null, jwtResult.getUserId());
        
        if (!revocationResult.isSuccess()) {
            LogUtils.logAuthentication("JWT_LOGIN", requestedUser, false, 
                "JWT revocation check failed: " + revocationResult.getMessage());
            return null;
        }
        
        if (revocationResult.isRevoked()) {
            LogUtils.logAuthentication("JWT_LOGIN", requestedUser, false, 
                "JWT token has been revoked: " + revocationResult.getMessage());
            return null;
        }
        
        // 設置認證用戶信息到請求上下文
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        if (context != null) {
            context.setAuthenticatedUser(jwtResult.getUserId(), jwtResult.getUsername());
        }
        
        // 記錄認證成功
        LogUtils.logAuthentication("JWT_LOGIN", jwtResult.getUsername(), true, 
            "JWT authentication successful");
        
        // 返回認證結果對象
        return new AuthenticatedUser(
            jwtResult.getUserId(),
            jwtResult.getUsername(),
            jwtResult.getRoles()
        );
    }
    
    /**
     * 使用傳統密碼進行身份驗證
     * 
     * @param user 用戶名
     * @param password 密碼
     * @return 驗證成功返回用戶對象，失敗返回 null
     */
    private Object authenticateWithPassword(String user, String password) {
        log.debug("Attempting password authentication for user: {}", user);
        
        // 從請求上下文獲取客戶端信息
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        String clientIp = context != null ? context.getClientIp() : null;
        String userAgent = context != null ? context.getUserAgent() : null;
        
        // 調用 gRPC 服務進行身份驗證
        FileProcessingProto.AuthenticateResponse response = 
            grpcClientService.authenticate(user, password, clientIp, userAgent);
        
        // 將結果存入快取
        authCache.put(user, password, 
                     response.hasUserId() ? response.getUserId() : null, 
                     response.getSuccess());
        
        if (response.getSuccess()) {
            // 設置認證用戶信息到請求上下文
            if (context != null) {
                context.setAuthenticatedUser(response.getUserId(), user);
            }
            
            // 記錄認證成功
            LogUtils.logAuthentication("PASSWORD_LOGIN", user, true, "WebDAV authentication successful");
            
            // 返回認證結果對象，包含用戶信息
            return new AuthenticatedUser(
                response.getUserId(),
                user,
                response.getRolesList()
            );
        } else {
            // 記錄認證失敗
            LogUtils.logAuthentication("PASSWORD_LOGIN", user, false, response.getErrorMessage());
            return null;
        }
    }
    
    /**
     * 授權檢查
     * 
     * @param request 請求對象
     * @param method HTTP 方法
     * @param auth 認證信息
     * @param resource 資源對象
     * @return true 表示授權通過，false 表示拒絕
     */
    @Override
    public boolean authorise(Request request, Method method, Auth auth, Resource resource) {
        // 如果沒有認證信息，拒絕訪問
        if (auth == null) {
            log.debug("No auth provided, denying access");
            return false;
        }
        
        // 獲取認證用戶
        Object tag = auth.getTag();
        if (!(tag instanceof AuthenticatedUser)) {
            log.warn("Invalid auth tag type: {}", tag != null ? tag.getClass() : "null");
            return false;
        }
        
        AuthenticatedUser user = (AuthenticatedUser) tag;
        log.debug("Authorizing user {} for method {} on resource {}", 
                  user.getUsername(), method, resource.getName());
        
        // TODO: 根據用戶角色和資源類型進行更細粒度的授權
        // 目前簡單地允許所有已認證用戶訪問
        return true;
    }
    
    /**
     * 獲取領域名稱
     * 
     * @param host 主機名
     * @return 領域名稱
     */
    @Override
    public String getRealm(String host) {
        return "FileManagement WebDAV";
    }
    
    /**
     * 檢查是否支持摘要認證
     * @return false 表示不支持摘要認證
     */
    @Override
    public boolean isDigestAllowed() {
        return false;
    }
    
    /**
     * 處理摘要認證（我們不支持摘要認證）
     * 
     * @param digestResponse 摘要響應
     * @return null 表示不支持摘要認證
     */
    @Override
    public Object authenticate(DigestResponse digestResponse) {
        log.debug("Digest authentication not supported");
        return null;
    }
    
    /**
     * 內部類：表示已認證的用戶
     */
    public static class AuthenticatedUser {
        private final String userId;
        private final String username;
        private final List<String> roles;
        
        public AuthenticatedUser(String userId, String username, List<String> roles) {
            this.userId = userId;
            this.username = username;
            this.roles = roles;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public List<String> getRoles() {
            return roles;
        }
        
        public boolean hasRole(String role) {
            return roles != null && roles.contains(role);
        }
    }
}