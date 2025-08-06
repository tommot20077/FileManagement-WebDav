package dowob.xyz.filemanagementwebdav.component.path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathResolver 的單元測試
 */
class PathResolverTest {
    
    private PathResolver resolver;
    
    @BeforeEach
    void setUp() {
        resolver = new PathResolver();
    }
    
    @Test
    void testParsePath_RootPath() {
        List<String> segments = resolver.parsePath("/");
        assertTrue(segments.isEmpty());
        
        segments = resolver.parsePath("");
        assertTrue(segments.isEmpty());
        
        segments = resolver.parsePath(null);
        assertTrue(segments.isEmpty());
    }
    
    @Test
    void testParsePath_SimplePath() {
        List<String> segments = resolver.parsePath("/user1/folder/file.txt");
        assertEquals(Arrays.asList("user1", "folder", "file.txt"), segments);
    }
    
    @Test
    void testParsePath_WithTrailingSlash() {
        List<String> segments = resolver.parsePath("/user1/folder/");
        assertEquals(Arrays.asList("user1", "folder"), segments);
    }
    
    @Test
    void testParsePath_WithoutLeadingSlash() {
        List<String> segments = resolver.parsePath("user1/folder/file.txt");
        assertEquals(Arrays.asList("user1", "folder", "file.txt"), segments);
    }
    
    @Test
    void testParsePath_WithDoubleSlash() {
        List<String> segments = resolver.parsePath("/user1//folder/file.txt");
        assertEquals(Arrays.asList("user1", "folder", "file.txt"), segments);
    }
    
    @Test
    void testBuildPath_EmptySegments() {
        String path = resolver.buildPath(List.of());
        assertEquals("/", path);
        
        path = resolver.buildPath(null);
        assertEquals("/", path);
    }
    
    @Test
    void testBuildPath_WithSegments() {
        String path = resolver.buildPath(Arrays.asList("user1", "folder", "file.txt"));
        assertEquals("/user1/folder/file.txt", path);
    }
    
    @Test
    void testBuildPath_FromParentAndFile() {
        String path = resolver.buildPath("/user1/folder", "file.txt");
        assertEquals("/user1/folder/file.txt", path);
        
        path = resolver.buildPath("/user1/folder/", "file.txt");
        assertEquals("/user1/folder/file.txt", path);
        
        path = resolver.buildPath("/", "file.txt");
        assertEquals("/file.txt", path);
        
        path = resolver.buildPath(null, "file.txt");
        assertEquals("/file.txt", path);
    }
    
    @Test
    void testGetParentPath() {
        assertEquals("/", resolver.getParentPath("/file.txt"));
        assertEquals("/user1", resolver.getParentPath("/user1/file.txt"));
        assertEquals("/user1/folder", resolver.getParentPath("/user1/folder/file.txt"));
        assertEquals("/", resolver.getParentPath("/"));
    }
    
    @Test
    void testGetFileName() {
        assertEquals("file.txt", resolver.getFileName("/user1/folder/file.txt"));
        assertEquals("folder", resolver.getFileName("/user1/folder"));
        assertEquals("file.txt", resolver.getFileName("/file.txt"));
        assertEquals("", resolver.getFileName("/"));
    }
    
    @Test
    void testNormalizePath() {
        assertEquals("/user1/folder/file.txt", resolver.normalizePath("/user1/folder/file.txt"));
        assertEquals("/user1/folder/file.txt", resolver.normalizePath("user1/folder/file.txt"));
        assertEquals("/user1/folder/file.txt", resolver.normalizePath("/user1//folder/file.txt"));
        assertEquals("/user1/folder/file.txt", resolver.normalizePath("/user1/folder/file.txt/"));
        assertEquals("/", resolver.normalizePath("/"));
        assertEquals("/", resolver.normalizePath(""));
        assertEquals("/", resolver.normalizePath(null));
    }
    
    @Test
    void testIsRootPath() {
        assertTrue(resolver.isRootPath("/"));
        assertTrue(resolver.isRootPath(""));
        assertTrue(resolver.isRootPath(null));
        assertFalse(resolver.isRootPath("/user1"));
        assertFalse(resolver.isRootPath("/file.txt"));
    }
    
    @Test
    void testGetPathDepth() {
        assertEquals(0, resolver.getPathDepth("/"));
        assertEquals(1, resolver.getPathDepth("/file.txt"));
        assertEquals(2, resolver.getPathDepth("/user1/file.txt"));
        assertEquals(3, resolver.getPathDepth("/user1/folder/file.txt"));
    }
    
    @Test
    void testIsChildPath() {
        assertTrue(resolver.isChildPath("/user1/folder/file.txt", "/user1/folder"));
        assertTrue(resolver.isChildPath("/user1/folder", "/user1"));
        assertTrue(resolver.isChildPath("/user1", "/"));
        
        assertFalse(resolver.isChildPath("/user1", "/user2"));
        assertFalse(resolver.isChildPath("/", "/"));
        assertFalse(resolver.isChildPath("/user1", "/user1"));
        assertFalse(resolver.isChildPath("/user1/folder", "/user2/folder"));
    }
    
    @Test
    void testIsChildPath_RootAsParent() {
        assertTrue(resolver.isChildPath("/user1", "/"));
        assertTrue(resolver.isChildPath("/user1/folder", "/"));
        assertFalse(resolver.isChildPath("/", "/"));
    }
}