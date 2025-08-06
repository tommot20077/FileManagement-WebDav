package dowob.xyz.filemanagementwebdav.service;

import com.github.benmanes.caffeine.cache.Cache;
import dowob.xyz.filemanagementwebdav.component.path.DuplicateNameHandler;
import dowob.xyz.filemanagementwebdav.component.path.PathResolver;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.path.PathMapping;
import dowob.xyz.filemanagementwebdav.data.path.PathNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PathMappingService 的單元測試
 */
@ExtendWith(MockitoExtension.class)
class PathMappingServiceTest {
    
    @Mock
    private Cache<String, PathMapping> pathToIdCache;
    
    @Mock
    private Cache<Long, PathMapping> idToPathCache;
    
    @Mock
    private Cache<Long, PathNode> userFileTreeCache;
    
    @Mock
    private Cache<String, PathNode> folderContentCache;
    
    private PathResolver pathResolver;
    private DuplicateNameHandler duplicateNameHandler;
    private PathMappingService pathMappingService;
    
    @BeforeEach
    void setUp() {
        pathResolver = new PathResolver();
        duplicateNameHandler = new DuplicateNameHandler();
        pathMappingService = new PathMappingService(
            pathToIdCache,
            idToPathCache,
            userFileTreeCache,
            folderContentCache,
            pathResolver,
            duplicateNameHandler
        );
    }
    
    @Test
    void testResolvePathToId_RootPath() {
        Long result = pathMappingService.resolvePathToId("/");
        assertEquals(0L, result);
    }
    
    @Test
    void testResolvePathToId_CachedPath() {
        String path = "/user1/folder/file.txt";
        PathMapping cachedMapping = PathMapping.builder()
            .fullPath(path)
            .fileId(123L)
            .userId(1L)
            .lastAccess(LocalDateTime.now())
            .build();
        
        when(pathToIdCache.getIfPresent(path)).thenReturn(cachedMapping);
        
        Long result = pathMappingService.resolvePathToId(path);
        assertEquals(123L, result);
        verify(pathToIdCache).getIfPresent(path);
    }
    
    @Test
    void testResolveIdToPath_RootId() {
        String result = pathMappingService.resolveIdToPath(0L);
        assertEquals("/", result);
        
        result = pathMappingService.resolveIdToPath(null);
        assertEquals("/", result);
    }
    
    @Test
    void testResolveIdToPath_CachedId() {
        Long fileId = 123L;
        String path = "/user1/folder/file.txt";
        PathMapping cachedMapping = PathMapping.builder()
            .fullPath(path)
            .fileId(fileId)
            .userId(1L)
            .lastAccess(LocalDateTime.now())
            .build();
        
        when(idToPathCache.getIfPresent(fileId)).thenReturn(cachedMapping);
        
        String result = pathMappingService.resolveIdToPath(fileId);
        assertEquals(path, result);
        verify(idToPathCache).getIfPresent(fileId);
    }
    
    @Test
    void testProcessFilesInFolder_EmptyList() {
        List<FileMetadata> result = pathMappingService.processFilesInFolder(1L, null, 1L);
        assertTrue(result.isEmpty());
        
        result = pathMappingService.processFilesInFolder(1L, List.of(), 1L);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testProcessFilesInFolder_WithDuplicates() {
        Long folderId = 1L;
        Long userId = 1L;
        
        List<FileMetadata> files = Arrays.asList(
            FileMetadata.builder()
                .id("101")
                .name("file.txt")
                .isDirectory(false)
                .build(),
            FileMetadata.builder()
                .id("102")
                .name("file.txt")
                .isDirectory(false)
                .build(),
            FileMetadata.builder()
                .id("103")
                .name("file.txt")
                .isDirectory(false)
                .build(),
            FileMetadata.builder()
                .id("104")
                .name("folder")
                .isDirectory(true)
                .build()
        );
        
        List<FileMetadata> result = pathMappingService.processFilesInFolder(folderId, files, userId);
        
        assertEquals(4, result.size());
        assertEquals("file.txt", result.get(0).getName());
        assertEquals("file(1).txt", result.get(1).getName());
        assertEquals("file(2).txt", result.get(2).getName());
        assertEquals("folder", result.get(3).getName());
        
        verify(folderContentCache).put(anyString(), any(PathNode.class));
    }
    
    @Test
    void testRegisterPath() {
        String path = "/user1/folder/file.txt";
        Long fileId = 123L;
        FileMetadata metadata = FileMetadata.builder()
            .id("123")
            .name("file.txt")
            .parentId(100L)
            .isDirectory(false)
            .build();
        Long userId = 1L;
        
        pathMappingService.registerPath(path, fileId, metadata, userId);
        
        verify(pathToIdCache).put(eq(path), any(PathMapping.class));
        verify(idToPathCache).put(eq(fileId), any(PathMapping.class));
    }
    
    @Test
    void testRemovePath() {
        String path = "/user1/folder/file.txt";
        PathMapping mapping = PathMapping.builder()
            .fullPath(path)
            .fileId(123L)
            .userId(1L)
            .build();
        
        when(pathToIdCache.getIfPresent(path)).thenReturn(mapping);
        
        pathMappingService.removePath(path);
        
        verify(pathToIdCache).invalidate(path);
        verify(idToPathCache).invalidate(123L);
    }
    
    @Test
    void testUpdatePath() {
        String oldPath = "/user1/folder/old.txt";
        String newPath = "/user1/folder/new.txt";
        Long fileId = 123L;
        
        PathMapping oldMapping = PathMapping.builder()
            .fullPath(oldPath)
            .fileId(fileId)
            .userId(1L)
            .parentId(100L)
            .isDirectory(false)
            .createTime(LocalDateTime.now())
            .build();
        
        when(idToPathCache.getIfPresent(fileId)).thenReturn(oldMapping);
        
        pathMappingService.updatePath(oldPath, newPath, fileId);
        
        // updatePath 內部呼叫 removePath，removePath 會檢查快取是否存在
        // 因為 mock 返回 null，所以不會執行 invalidate
        // 但會直接執行 put 新路徑
        verify(pathToIdCache).getIfPresent(oldPath);
        verify(pathToIdCache).put(eq(newPath), any(PathMapping.class));
        verify(idToPathCache).put(eq(fileId), any(PathMapping.class));
    }
    
    @Test
    void testClearUserCache() {
        Long userId = 1L;
        
        // 模擬快取中有該用戶的映射
        PathMapping mapping1 = PathMapping.builder()
            .fullPath("/user1/file1.txt")
            .fileId(101L)
            .userId(userId)
            .build();
        PathMapping mapping2 = PathMapping.builder()
            .fullPath("/user1/file2.txt")
            .fileId(102L)
            .userId(userId)
            .build();
        PathMapping mapping3 = PathMapping.builder()
            .fullPath("/user2/file3.txt")
            .fileId(103L)
            .userId(2L)
            .build();
        
        var cacheMap = new java.util.concurrent.ConcurrentHashMap<String, PathMapping>();
        cacheMap.put("/user1/file1.txt", mapping1);
        cacheMap.put("/user1/file2.txt", mapping2);
        cacheMap.put("/user2/file3.txt", mapping3);
        
        when(pathToIdCache.asMap()).thenReturn(cacheMap);
        
        pathMappingService.clearUserCache(userId);
        
        verify(userFileTreeCache).invalidate(userId);
        verify(pathToIdCache, times(2)).invalidate(anyString());
    }
    
    @Test
    void testGetCacheStats() {
        when(pathToIdCache.estimatedSize()).thenReturn(100L);
        when(idToPathCache.estimatedSize()).thenReturn(100L);
        when(userFileTreeCache.estimatedSize()).thenReturn(10L);
        when(folderContentCache.estimatedSize()).thenReturn(50L);
        
        Map<String, Object> stats = pathMappingService.getCacheStats();
        
        assertEquals(100L, stats.get("pathToIdCacheSize"));
        assertEquals(100L, stats.get("idToPathCacheSize"));
        assertEquals(10L, stats.get("userFileTreeCacheSize"));
        assertEquals(50L, stats.get("folderContentCacheSize"));
    }
}