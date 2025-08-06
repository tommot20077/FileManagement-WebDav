package dowob.xyz.filemanagementwebdav.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dowob.xyz.filemanagementwebdav.data.path.PathMapping;
import dowob.xyz.filemanagementwebdav.data.path.PathNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 路徑映射快取配置
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class PathMappingCacheConfig {
    
    @Value("${webdav.path-mapping.cache-size:10000}")
    private int cacheSize;
    
    @Value("${webdav.path-mapping.cache-ttl:3600}")
    private int cacheTtl;
    
    /**
     * 路徑到 ID 的映射快取
     * Key: fullPath (如 "/user1/folder/file.txt")
     * Value: PathMapping
     */
    @Bean
    public Cache<String, PathMapping> pathToIdCache() {
        return Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterAccess(cacheTtl, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }
    
    /**
     * ID 到路徑的映射快取
     * Key: fileId
     * Value: PathMapping
     */
    @Bean
    public Cache<Long, PathMapping> idToPathCache() {
        return Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterAccess(cacheTtl, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }
    
    /**
     * 用戶檔案樹快取
     * Key: userId
     * Value: 根節點 PathNode
     */
    @Bean
    public Cache<Long, PathNode> userFileTreeCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000) // 最多快取 1000 個用戶的檔案樹
                .expireAfterAccess(cacheTtl, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }
    
    /**
     * 資料夾內容快取
     * Key: "userId:folderId"
     * Value: 該資料夾下的檔案列表（已處理重複名稱）
     */
    @Bean
    public Cache<String, PathNode> folderContentCache() {
        return Caffeine.newBuilder()
                .maximumSize(cacheSize / 2)
                .expireAfterWrite(300, TimeUnit.SECONDS) // 5 分鐘後過期
                .recordStats()
                .build();
    }
}