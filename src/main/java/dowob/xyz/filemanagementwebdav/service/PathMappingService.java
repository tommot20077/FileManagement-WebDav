package dowob.xyz.filemanagementwebdav.service;

import com.github.benmanes.caffeine.cache.Cache;
import dowob.xyz.filemanagementwebdav.component.path.DuplicateNameHandler;
import dowob.xyz.filemanagementwebdav.component.path.PathResolver;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.path.PathMapping;
import dowob.xyz.filemanagementwebdav.data.path.PathNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 路徑映射服務，管理 WebDAV 路徑與檔案 ID 之間的映射關係
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Service
public class PathMappingService {
    
    private static final Logger log = LoggerFactory.getLogger(PathMappingService.class);
    
    private final Cache<String, PathMapping> pathToIdCache;
    private final Cache<Long, PathMapping> idToPathCache;
    private final Cache<Long, PathNode> userFileTreeCache;
    private final Cache<String, PathNode> folderContentCache;
    
    private final PathResolver pathResolver;
    private final DuplicateNameHandler duplicateNameHandler;
    
    @Autowired
    public PathMappingService(
            Cache<String, PathMapping> pathToIdCache,
            Cache<Long, PathMapping> idToPathCache,
            Cache<Long, PathNode> userFileTreeCache,
            Cache<String, PathNode> folderContentCache,
            PathResolver pathResolver,
            DuplicateNameHandler duplicateNameHandler) {
        this.pathToIdCache = pathToIdCache;
        this.idToPathCache = idToPathCache;
        this.userFileTreeCache = userFileTreeCache;
        this.folderContentCache = folderContentCache;
        this.pathResolver = pathResolver;
        this.duplicateNameHandler = duplicateNameHandler;
    }
    
    /**
     * 將 WebDAV 路徑解析為檔案 ID
     * 
     * @param path 完整路徑（如 /user1/folder/file.txt）
     * @return 檔案 ID，如果路徑不存在則返回 null
     */
    public Long resolvePathToId(String path) {
        String normalizedPath = pathResolver.normalizePath(path);
        
        // 先從快取查詢
        PathMapping cached = pathToIdCache.getIfPresent(normalizedPath);
        if (cached != null) {
            cached.updateAccessTime();
            return cached.getFileId();
        }
        
        // 如果是根路徑，返回特殊 ID
        if (pathResolver.isRootPath(normalizedPath)) {
            return 0L; // 根目錄的特殊 ID
        }
        
        // 解析路徑並逐層查詢
        List<String> segments = pathResolver.parsePath(normalizedPath);
        if (segments.isEmpty()) {
            return 0L;
        }
        
        // 從第一段開始（通常是用戶名）
        String username = segments.get(0);
        // TODO: 從主服務查詢用戶 ID
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            log.debug("User not found for path: {}", path);
            return null;
        }
        
        // 獲取或建立用戶的檔案樹
        PathNode root = getUserFileTree(userId);
        if (root == null) {
            log.debug("User file tree not found for userId: {}", userId);
            return null;
        }
        
        // 逐層查找
        PathNode current = root;
        StringBuilder currentPath = new StringBuilder("/").append(username);
        
        for (int i = 1; i < segments.size(); i++) {
            String segment = segments.get(i);
            PathNode child = current.getChild(segment);
            
            if (child == null) {
                log.debug("Path segment not found: {} in path: {}", segment, currentPath);
                return null;
            }
            
            current = child;
            currentPath.append("/").append(segment);
        }
        
        // 快取結果
        PathMapping mapping = PathMapping.builder()
                .fullPath(normalizedPath)
                .fileId(current.getFileId())
                .userId(userId)
                .originalName(current.getOriginalName())
                .webdavName(current.getWebdavName())
                .parentId(current.getParentId())
                .isDirectory(current.isDirectory())
                .lastAccess(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        
        pathToIdCache.put(normalizedPath, mapping);
        idToPathCache.put(current.getFileId(), mapping);
        
        return current.getFileId();
    }
    
    /**
     * 將檔案 ID 解析為 WebDAV 路徑
     * 
     * @param fileId 檔案 ID
     * @return 完整路徑，如果 ID 不存在則返回 null
     */
    public String resolveIdToPath(Long fileId) {
        if (fileId == null || fileId == 0L) {
            return "/";
        }
        
        // 先從快取查詢
        PathMapping cached = idToPathCache.getIfPresent(fileId);
        if (cached != null) {
            cached.updateAccessTime();
            return cached.getFullPath();
        }
        
        // TODO: 從主服務查詢檔案資訊並建構路徑
        // 這需要遞迴查詢父資料夾直到根目錄
        
        return null;
    }
    
    /**
     * 為資料夾內的檔案列表處理重複名稱
     * 
     * @param folderId 資料夾 ID
     * @param files 檔案列表
     * @param userId 用戶 ID
     * @return 處理後的檔案列表（包含唯一的 WebDAV 名稱）
     */
    public List<FileMetadata> processFilesInFolder(Long folderId, List<FileMetadata> files, Long userId) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 建立名稱計數器
        Map<String, Integer> nameCounter = new ConcurrentHashMap<>();
        List<FileMetadata> processedFiles = new ArrayList<>();
        
        // 獲取資料夾節點
        String folderCacheKey = userId + ":" + folderId;
        PathNode folderNode = folderContentCache.getIfPresent(folderCacheKey);
        
        if (folderNode == null) {
            folderNode = new PathNode();
            folderNode.setFileId(folderId);
            folderNode.setDirectory(true);
            folderNode.setUserId(userId);
        }
        
        // 處理每個檔案
        for (FileMetadata file : files) {
            String originalName = file.getName();
            String webdavName = duplicateNameHandler.generateUniqueName(originalName, nameCounter);
            
            // 建立或更新節點
            PathNode childNode = new PathNode(
                    file.getId(),
                    originalName,
                    folderId,
                    file.isDirectory(),
                    userId
            );
            childNode.setWebdavName(webdavName);
            
            // 添加到資料夾節點
            folderNode.addChild(childNode);
            
            // 複製檔案資訊並設定 WebDAV 名稱
            FileMetadata processedFile = copyFileMetadata(file);
            processedFile.setName(webdavName);
            processedFiles.add(processedFile);
            
            log.trace("Processed file: {} -> {}", originalName, webdavName);
        }
        
        // 快取資料夾內容
        folderContentCache.put(folderCacheKey, folderNode);
        
        return processedFiles;
    }
    
    /**
     * 註冊路徑映射
     * 
     * @param path 完整路徑
     * @param fileId 檔案 ID
     * @param metadata 檔案元資料
     * @param userId 用戶 ID
     */
    public void registerPath(String path, Long fileId, FileMetadata metadata, Long userId) {
        String normalizedPath = pathResolver.normalizePath(path);
        
        PathMapping mapping = PathMapping.builder()
                .fullPath(normalizedPath)
                .fileId(fileId)
                .userId(userId)
                .originalName(metadata.getName())
                .webdavName(metadata.getName())
                .parentId(metadata.getParentId())
                .isDirectory(metadata.isDirectory())
                .lastAccess(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        
        pathToIdCache.put(normalizedPath, mapping);
        idToPathCache.put(fileId, mapping);
        
        log.debug("Registered path mapping: {} -> {}", normalizedPath, fileId);
    }
    
    /**
     * 移除路徑映射
     * 
     * @param path 路徑
     */
    public void removePath(String path) {
        String normalizedPath = pathResolver.normalizePath(path);
        PathMapping mapping = pathToIdCache.getIfPresent(normalizedPath);
        
        if (mapping != null) {
            pathToIdCache.invalidate(normalizedPath);
            idToPathCache.invalidate(mapping.getFileId());
            log.debug("Removed path mapping: {}", normalizedPath);
        }
    }
    
    /**
     * 移動或重新命名檔案時更新映射
     * 
     * @param oldPath 舊路徑
     * @param newPath 新路徑
     * @param fileId 檔案 ID
     */
    public void updatePath(String oldPath, String newPath, Long fileId) {
        removePath(oldPath);
        
        PathMapping oldMapping = idToPathCache.getIfPresent(fileId);
        if (oldMapping != null) {
            PathMapping newMapping = PathMapping.builder()
                    .fullPath(pathResolver.normalizePath(newPath))
                    .fileId(fileId)
                    .userId(oldMapping.getUserId())
                    .originalName(pathResolver.getFileName(newPath))
                    .webdavName(pathResolver.getFileName(newPath))
                    .parentId(oldMapping.getParentId())
                    .isDirectory(oldMapping.isDirectory())
                    .lastAccess(LocalDateTime.now())
                    .createTime(oldMapping.getCreateTime())
                    .build();
            
            pathToIdCache.put(newMapping.getFullPath(), newMapping);
            idToPathCache.put(fileId, newMapping);
            
            log.debug("Updated path mapping: {} -> {}", oldPath, newPath);
        }
    }
    
    /**
     * 清除用戶的所有映射快取
     * 
     * @param userId 用戶 ID
     */
    public void clearUserCache(Long userId) {
        userFileTreeCache.invalidate(userId);
        
        // 清除該用戶的所有路徑映射
        Set<String> pathsToRemove = pathToIdCache.asMap().entrySet().stream()
                .filter(entry -> entry.getValue().getUserId().equals(userId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        
        pathsToRemove.forEach(pathToIdCache::invalidate);
        
        log.info("Cleared cache for user: {}", userId);
    }
    
    /**
     * 獲取快取統計資訊
     * 
     * @return 統計資訊
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pathToIdCacheSize", pathToIdCache.estimatedSize());
        stats.put("pathToIdCacheStats", pathToIdCache.stats());
        stats.put("idToPathCacheSize", idToPathCache.estimatedSize());
        stats.put("idToPathCacheStats", idToPathCache.stats());
        stats.put("userFileTreeCacheSize", userFileTreeCache.estimatedSize());
        stats.put("folderContentCacheSize", folderContentCache.estimatedSize());
        return stats;
    }
    
    // 輔助方法
    
    private PathNode getUserFileTree(Long userId) {
        return userFileTreeCache.getIfPresent(userId);
    }
    
    private Long getUserIdByUsername(String username) {
        // TODO: 實作從主服務查詢用戶 ID
        // 暫時返回模擬值
        return username.hashCode() > 0 ? (long) username.hashCode() : 1L;
    }
    
    private FileMetadata copyFileMetadata(FileMetadata source) {
        return FileMetadata.builder()
                .id(source.getId() != null ? source.getId().toString() : null)
                .name(source.getName())
                .path(source.getPath())
                .size(source.getSize())
                .contentType(source.getContentType())
                .isDirectory(source.isDirectory())
                .createTimestamp(source.getCreateTimestamp())
                .modifiedTimestamp(source.getModifiedTimestamp())
                .parentId(source.getParentId())
                .build();
    }
}