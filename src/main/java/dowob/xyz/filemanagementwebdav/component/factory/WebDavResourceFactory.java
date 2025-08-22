package dowob.xyz.filemanagementwebdav.component.factory;

import dowob.xyz.filemanagementwebdav.component.path.WebDavPathConverter;
import dowob.xyz.filemanagementwebdav.component.security.WebDavSecurityManager;
import dowob.xyz.filemanagementwebdav.config.properties.WebDavSecurityProperties;
import dowob.xyz.filemanagementwebdav.context.AuthenticationContextManager;
import dowob.xyz.filemanagementwebdav.context.MiltonRequestHolder;
import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import dowob.xyz.filemanagementwebdav.data.AnonymousResource;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.WebDavFileResource;
import dowob.xyz.filemanagementwebdav.data.WebDavFolderResource;
import dowob.xyz.filemanagementwebdav.service.FileProcessingService;
import io.milton.common.Path;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebDAV 資源工廠
 * 
 * 負責根據請求路徑創建對應的 WebDAV 資源對象。
 * 支持新的 /dav 路徑結構，從認證上下文自動獲取用戶信息。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavResourceFactory
 * @create 2025/6/9
 * @Version 1.0
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class WebDavResourceFactory implements ResourceFactory {
    private final FileProcessingService fileProcessingService;
    private final WebDavPathConverter pathConverter;
    private final WebDavSecurityProperties securityProperties;
    private final WebDavSecurityManager securityManager;
    private final AuthenticationContextManager authContextManager;
    private final Map<String, FileMetadata> metadataCache = new ConcurrentHashMap<>();

    @Override
    public Resource getResource(String host, String requestPath) throws NotAuthorizedException, BadRequestException {
        log.debug("Getting resource for path: {}", requestPath);
        
        String userId = null;
        String username = null;
        
        // 優先從 Milton Request 獲取認證信息
        io.milton.http.Request miltonRequest = MiltonRequestHolder.getRequest();
        if (miltonRequest != null) {
            io.milton.http.Auth auth = miltonRequest.getAuthorization();
            if (auth != null && auth.getTag() != null) {
                if (auth.getTag() instanceof WebDavSecurityManager.AuthenticatedUser) {
                    WebDavSecurityManager.AuthenticatedUser authUser = 
                        (WebDavSecurityManager.AuthenticatedUser) auth.getTag();
                    userId = authUser.getUserId();
                    username = authUser.getUsername();
                    log.debug("Found authenticated user from Milton Request: {}", username);
                }
            }
        }
        
        // 從 Milton 的上下文獲取認證信息
        if (userId == null) {
            Object authTag = MiltonRequestHolder.getAuthTag();
            if (authTag != null) {
                if (authTag instanceof WebDavSecurityManager.AuthenticatedUser) {
                    WebDavSecurityManager.AuthenticatedUser authUser = 
                        (WebDavSecurityManager.AuthenticatedUser) authTag;
                    userId = authUser.getUserId();
                    username = authUser.getUsername();
                    log.debug("Found authenticated user from Milton context: {}", username);
                }
            }
        }
        
        // 如果 Milton 上下文沒有，嘗試從 RequestContextHolder 獲取
        if (userId == null) {
            RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
            if (context != null && context.isAuthenticated()) {
                userId = context.getUserId();
                username = context.getUsername();
                log.debug("Found authenticated user in ThreadLocal: {}", username);
            }
        }
        
        // 嘗試從持久化的認證管理器獲取
        if (userId == null) {
            RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
            String sessionId = context != null ? context.getRequestId() : null;
            if (sessionId != null) {
                AuthenticationContextManager.AuthInfo authInfo = authContextManager.getAuthentication(sessionId);
                if (authInfo != null) {
                    userId = authInfo.userId;
                    username = authInfo.username;
                    log.debug("Recovered authentication from manager for session: {}, user: {}", sessionId, username);
                    
                    // 創建並設置 Auth 對象到 MiltonRequestHolder
                    WebDavSecurityManager.AuthenticatedUser authUser = 
                        new WebDavSecurityManager.AuthenticatedUser(userId, username, "USER");
                    io.milton.http.Auth auth = new io.milton.http.Auth(username, authUser);
                    MiltonRequestHolder.setAuth(auth);
                    log.debug("Created and set Auth object for session user: {}", username);
                }
            }
        }
        
        // 最後嘗試使用最近的認證信息（備用方案）
        if (userId == null) {
            AuthenticationContextManager.AuthInfo lastAuth = authContextManager.getLastAuthentication();
            if (lastAuth != null) {
                userId = lastAuth.userId;
                username = lastAuth.username;
                log.debug("Using last authentication for user: {}", username);
                
                // 創建並設置 Auth 對象到 MiltonRequestHolder
                WebDavSecurityManager.AuthenticatedUser authUser = 
                    new WebDavSecurityManager.AuthenticatedUser(userId, username, "USER");
                io.milton.http.Auth auth = new io.milton.http.Auth(username, authUser);
                MiltonRequestHolder.setAuth(auth);
                log.debug("Created and set Auth object for recovered user: {}", username);
            }
        }
        
        // 如果仍然沒有認證信息，返回匿名資源
        if (userId == null || username == null) {
            log.debug("No authenticated user found for path: {}", requestPath);
            // 創建匿名資源，傳入工廠引用以便在認證後可以重新獲取
            return new AnonymousResource(host, requestPath, securityProperties.getRealm(), securityManager, this);
        }
        
        log.debug("Processing authenticated request for user: {}, userId: {}, path: {}", username, userId, requestPath);
        
        // 處理 /dav 根路徑
        if (requestPath.equals("/dav") || requestPath.equals("/dav/")) {
            return createUserRootFolder(host, userId, username);
        }
        
        // 轉換路徑並獲取資源
        String internalPath = pathConverter.toInternalPath(requestPath, userId);
        log.debug("Converted path: {} -> {}", requestPath, internalPath);
        
        // 使用內部路徑作為快取鍵
        FileMetadata metadata = metadataCache.get(internalPath);
        if (metadata == null) {
            // 使用內部路徑查詢文件元數據
            Path path = Path.path(internalPath);
            metadata = fileProcessingService.getFileMetadata(path);
            if (metadata != null) {
                metadataCache.put(internalPath, metadata);
            }
        }
        
        if (metadata != null) {
            // 使用原始 WebDAV 路徑創建資源對象
            Path webdavPath = Path.path(requestPath);
            if (metadata.isDirectory()) {
                return new WebDavFolderResource(host, webdavPath, metadata, this, fileProcessingService);
            }
            return new WebDavFileResource(host, webdavPath, metadata, fileProcessingService);
        }
        
        log.debug("Resource not found for path: {}", requestPath);
        return null;
    }
    
    /**
     * 創建用戶根目錄資源
     * 
     * @param host 主機名
     * @param userId 用戶 ID
     * @param username 用戶名
     * @return 根目錄資源
     */
    private Resource createUserRootFolder(String host, String userId, String username) {
        FileMetadata rootMetadata = FileMetadata.builder()
                .name(username)
                .path("/" + userId)
                .isDirectory(true)
                .size(0)
                .contentType("")
                .createDate(java.time.LocalDateTime.now())
                .modifiedDate(java.time.LocalDateTime.now())
                .build();
        
        Path webdavPath = Path.path("/dav");
        return new WebDavFolderResource(host, webdavPath, rootMetadata, this, fileProcessingService);
    }

    public void invalidateCache(String requestPath) {
        metadataCache.remove(requestPath);
    }
}
