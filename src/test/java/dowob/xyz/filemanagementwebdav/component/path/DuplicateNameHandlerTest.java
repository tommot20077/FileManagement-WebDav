package dowob.xyz.filemanagementwebdav.component.path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DuplicateNameHandler 的單元測試
 */
class DuplicateNameHandlerTest {
    
    private DuplicateNameHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new DuplicateNameHandler();
    }
    
    @Test
    void testGenerateUniqueName_NoConflict() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("other.txt");
        
        String result = handler.generateUniqueName("file.txt", existingNames);
        assertEquals("file.txt", result);
    }
    
    @Test
    void testGenerateUniqueName_WithConflict() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("file.txt");
        existingNames.add("other.txt");
        
        String result = handler.generateUniqueName("file.txt", existingNames);
        assertEquals("file(1).txt", result);
    }
    
    @Test
    void testGenerateUniqueName_MultipleConflicts() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("file.txt");
        existingNames.add("file(1).txt");
        existingNames.add("file(2).txt");
        
        String result = handler.generateUniqueName("file.txt", existingNames);
        assertEquals("file(3).txt", result);
    }
    
    @Test
    void testGenerateUniqueName_NoExtension() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("README");
        
        String result = handler.generateUniqueName("README", existingNames);
        assertEquals("README(1)", result);
    }
    
    @Test
    void testGenerateUniqueName_WithCounter() {
        Map<String, Integer> nameCounter = new HashMap<>();
        
        // 第一次使用
        String result1 = handler.generateUniqueName("file.txt", nameCounter);
        assertEquals("file.txt", result1);
        
        // 第二次使用相同名稱
        String result2 = handler.generateUniqueName("file.txt", nameCounter);
        assertEquals("file(1).txt", result2);
        
        // 第三次使用相同名稱
        String result3 = handler.generateUniqueName("file.txt", nameCounter);
        assertEquals("file(2).txt", result3);
    }
    
    @Test
    void testExtractOriginalName() {
        assertEquals("file.txt", handler.extractOriginalName("file(1).txt"));
        assertEquals("file.txt", handler.extractOriginalName("file(99).txt"));
        assertEquals("README", handler.extractOriginalName("README(1)"));
        assertEquals("original.txt", handler.extractOriginalName("original.txt"));
    }
    
    @Test
    void testIsNumberedName() {
        assertTrue(handler.isNumberedName("file(1).txt"));
        assertTrue(handler.isNumberedName("file(99).txt"));
        assertTrue(handler.isNumberedName("README(1)"));
        assertFalse(handler.isNumberedName("file.txt"));
        assertFalse(handler.isNumberedName("file[1].txt"));
    }
    
    @Test
    void testGetFileNumber() {
        assertEquals(1, handler.getFileNumber("file(1).txt"));
        assertEquals(99, handler.getFileNumber("file(99).txt"));
        assertEquals(0, handler.getFileNumber("file.txt"));
    }
    
    @Test
    void testGenerateUniqueName_SpecialCharacters() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("file-name.2024.txt");
        
        String result = handler.generateUniqueName("file-name.2024.txt", existingNames);
        assertEquals("file-name.2024(1).txt", result);
    }
    
    @Test
    void testGenerateUniqueName_MultipleExtensions() {
        Set<String> existingNames = new HashSet<>();
        existingNames.add("archive.tar.gz");
        
        String result = handler.generateUniqueName("archive.tar.gz", existingNames);
        assertEquals("archive.tar(1).gz", result);
    }
}