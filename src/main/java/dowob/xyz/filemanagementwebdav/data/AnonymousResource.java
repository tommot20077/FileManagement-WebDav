package dowob.xyz.filemanagementwebdav.data;

import dowob.xyz.filemanagementwebdav.component.factory.WebDavResourceFactory;
import dowob.xyz.filemanagementwebdav.component.security.WebDavSecurityManager;
import dowob.xyz.filemanagementwebdav.context.MiltonRequestHolder;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.Range;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.http.exceptions.NotFoundException;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.CollectionResource;
import io.milton.resource.DigestResource;
import io.milton.resource.GetableResource;
import io.milton.resource.PropFindableResource;
import io.milton.resource.PutableResource;
import io.milton.resource.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 匿名資源 - 用於未認證請求
 * 
 * 當用戶未認證時返回此資源，觸發 Milton 的認證流程。
 * 實現 DigestResource 接口以支持摘要認證（雖然我們主要使用基本認證）。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName AnonymousResource
 * @create 2025/8/15
 * @Version 1.0
 **/
@Slf4j
@Getter
public class AnonymousResource implements Resource, PropFindableResource, DigestResource, GetableResource, CollectionResource, PutableResource {
    
    private final String host;
    private final String path;
    private final String realm;
    private final WebDavSecurityManager securityManager;
    private final WebDavResourceFactory resourceFactory;
    private Resource authenticatedResource;
    
    public AnonymousResource(String host, String path, String realm, 
                           WebDavSecurityManager securityManager, 
                           WebDavResourceFactory resourceFactory) {
        this.host = host;
        this.path = path;
        this.realm = realm;
        this.securityManager = securityManager;
        this.resourceFactory = resourceFactory;
    }
    
    @Override
    public String getUniqueId() {
        return "anonymous-" + path.hashCode();
    }
    
    @Override
    public String getName() {
        // 返回路徑的最後一部分作為名稱
        if (path.equals("/") || path.equals("/dav") || path.equals("/dav/")) {
            return "dav";
        }
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "anonymous";
    }
    
    @Override
    public Object authenticate(String user, String password) {
        // 調用 SecurityManager 進行實際認證
        if (securityManager != null) {
            return securityManager.authenticate(user, password);
        }
        return null;
    }
    
    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        // 如果有認證信息且認證成功，返回 true 允許訪問
        // 但這個資源本身會重定向到正確的用戶資源
        if (auth != null && auth.getTag() != null) {
            log.debug("Auth tag found for anonymous resource, allowing access: {}", auth.getTag().getClass().getName());
            return true;
        }
        // 沒有認證信息，強制認證
        log.debug("No auth for anonymous resource, requiring authentication");
        return false;
    }
    
    @Override
    public String getRealm() {
        return realm;
    }
    
    @Override
    public Date getModifiedDate() {
        return new Date();
    }
    
    @Override
    public String checkRedirect(Request request) {
        return null;
    }
    
    @Override
    public Date getCreateDate() {
        return new Date();
    }
    
    /**
     * 實現 DigestResource 接口的方法
     * 雖然我們不使用摘要認證，但實現此接口可以提供更好的兼容性
     */
    @Override
    public Object authenticate(DigestResponse digestRequest) {
        // 不支持摘要認證
        return null;
    }
    
    @Override
    public boolean isDigestAllowed() {
        // 不允許摘要認證
        return false;
    }
    
    /**
     * 實現 GetableResource 接口
     * 如果已認證，委託給真實資源；否則返回空內容
     */
    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) 
            throws IOException, NotAuthorizedException, BadRequestException, NotFoundException {
        Resource realResource = getRealResource();
        if (realResource instanceof GetableResource) {
            // 委託給真實資源
            ((GetableResource) realResource).sendContent(out, range, params, contentType);
        } else {
            // 沒有認證或無法獲取真實資源，返回空內容
            log.debug("No authenticated resource available for sending content");
        }
    }
    
    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }
    
    @Override
    public String getContentType(String accepts) {
        return "text/html";
    }
    
    @Override
    public Long getContentLength() {
        Resource realResource = getRealResource();
        if (realResource instanceof GetableResource) {
            return ((GetableResource) realResource).getContentLength();
        }
        return 0L;
    }
    
    /**
     * 獲取真實的資源（如果已認證）
     * 
     * @return 如果已認證返回真實資源，否則返回 null
     */
    private Resource getRealResource() {
        // 如果已經有快取的認證資源，直接返回
        if (authenticatedResource != null) {
            return authenticatedResource;
        }
        
        // 檢查是否有認證信息
        Auth auth = MiltonRequestHolder.getAuth();
        if (auth != null && auth.getTag() != null) {
            try {
                // 使用工廠重新獲取資源（這次應該會返回真實的用戶資源）
                Resource resource = resourceFactory.getResource(host, path);
                if (resource != null && !(resource instanceof AnonymousResource)) {
                    authenticatedResource = resource;
                    log.debug("Successfully obtained authenticated resource for path: {}", path);
                    return resource;
                }
            } catch (Exception e) {
                log.error("Error getting authenticated resource for path: {}", path, e);
            }
        }
        
        return null;
    }
    
    // ==================== CollectionResource 實現 ====================
    
    @Override
    public Resource child(String childName) throws NotAuthorizedException, BadRequestException {
        Resource realResource = getRealResource();
        if (realResource instanceof CollectionResource) {
            return ((CollectionResource) realResource).child(childName);
        }
        // 匿名資源沒有子資源
        return null;
    }
    
    @Override
    public List<? extends Resource> getChildren() throws NotAuthorizedException, BadRequestException {
        Resource realResource = getRealResource();
        if (realResource instanceof CollectionResource) {
            return ((CollectionResource) realResource).getChildren();
        }
        // 匿名資源返回空列表
        return Collections.emptyList();
    }
    
    // ==================== PutableResource 實現 ====================
    
    @Override
    public Resource createNew(String newName, InputStream inputStream, Long length, String contentType) 
            throws IOException, ConflictException, NotAuthorizedException, BadRequestException {
        Resource realResource = getRealResource();
        if (realResource instanceof PutableResource) {
            return ((PutableResource) realResource).createNew(newName, inputStream, length, contentType);
        }
        // 匿名資源不允許創建新資源
        throw new NotAuthorizedException("Authentication required", this);
    }
}