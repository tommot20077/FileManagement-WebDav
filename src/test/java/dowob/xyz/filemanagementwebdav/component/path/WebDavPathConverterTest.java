package dowob.xyz.filemanagementwebdav.component.path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WebDavPathConverter 單元測試
 * 
 * 測試 WebDAV 路徑和內部路徑之間的轉換邏輯
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavPathConverterTest
 * @create 2025/8/15
 * @Version 1.0
 **/
@DisplayName("WebDAV 路徑轉換器測試")
class WebDavPathConverterTest {
    
    private WebDavPathConverter pathConverter;
    private static final String USER_ID = "123";
    private static final String WEBDAV_PREFIX = "/dav";
    
    @BeforeEach
    void setUp() {
        pathConverter = new WebDavPathConverter();
    }
    
    // ===== toInternalPath 測試 =====
    
    @Test
    @DisplayName("轉換 WebDAV 根路徑到內部路徑")
    void testToInternalPath_RootPath() {
        // Given
        String webdavPath = "/dav";
        
        // When
        String internalPath = pathConverter.toInternalPath(webdavPath, USER_ID);
        
        // Then
        assertThat(internalPath).isEqualTo("/123");
    }
    
    @Test
    @DisplayName("轉換 WebDAV 根路徑（帶斜線）到內部路徑")
    void testToInternalPath_RootPathWithSlash() {
        // Given
        String webdavPath = "/dav/";
        
        // When
        String internalPath = pathConverter.toInternalPath(webdavPath, USER_ID);
        
        // Then
        assertThat(internalPath).isEqualTo("/123");
    }
    
    @ParameterizedTest
    @DisplayName("轉換各種 WebDAV 路徑到內部路徑")
    @CsvSource({
        "/dav/documents, /123/documents",
        "/dav/documents/, /123/documents/",
        "/dav/documents/file.txt, /123/documents/file.txt",
        "/dav/folder/subfolder/file.pdf, /123/folder/subfolder/file.pdf",
        "/dav/中文資料夾/檔案.txt, /123/中文資料夾/檔案.txt"
    })
    void testToInternalPath_VariousPaths(String webdavPath, String expectedInternal) {
        // When
        String internalPath = pathConverter.toInternalPath(webdavPath, USER_ID);
        
        // Then
        assertThat(internalPath).isEqualTo(expectedInternal);
    }
    
    @Test
    @DisplayName("轉換空路徑應拋出異常")
    void testToInternalPath_NullPath() {
        // When & Then
        assertThatThrownBy(() -> pathConverter.toInternalPath(null, USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("路徑和用戶ID不能為空");
    }
    
    @Test
    @DisplayName("轉換路徑時用戶ID為空應拋出異常")
    void testToInternalPath_NullUserId() {
        // When & Then
        assertThatThrownBy(() -> pathConverter.toInternalPath("/dav/test", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("路徑和用戶ID不能為空");
    }
    
    // ===== toWebDavPath 測試 =====
    
    @Test
    @DisplayName("轉換內部根路徑到 WebDAV 路徑")
    void testToWebDavPath_UserRoot() {
        // Given
        String internalPath = "/123";
        
        // When
        String webdavPath = pathConverter.toWebDavPath(internalPath, USER_ID);
        
        // Then
        assertThat(webdavPath).isEqualTo("/dav");
    }
    
    @ParameterizedTest
    @DisplayName("轉換各種內部路徑到 WebDAV 路徑")
    @CsvSource({
        "/123/documents, /dav/documents",
        "/123/documents/, /dav/documents/",
        "/123/documents/file.txt, /dav/documents/file.txt",
        "/123/folder/subfolder/file.pdf, /dav/folder/subfolder/file.pdf",
        "/123/中文資料夾/檔案.txt, /dav/中文資料夾/檔案.txt"
    })
    void testToWebDavPath_VariousPaths(String internalPath, String expectedWebDav) {
        // When
        String webdavPath = pathConverter.toWebDavPath(internalPath, USER_ID);
        
        // Then
        assertThat(webdavPath).isEqualTo(expectedWebDav);
    }
    
    @Test
    @DisplayName("轉換 null 內部路徑應返回 WebDAV 前綴")
    void testToWebDavPath_NullPath() {
        // When
        String webdavPath = pathConverter.toWebDavPath(null, USER_ID);
        
        // Then
        assertThat(webdavPath).isEqualTo("/dav");
    }
    
    @Test
    @DisplayName("轉換不匹配用戶ID的內部路徑")
    void testToWebDavPath_MismatchedUserId() {
        // Given
        String internalPath = "/456/documents/file.txt";
        
        // When
        String webdavPath = pathConverter.toWebDavPath(internalPath, USER_ID);
        
        // Then
        // 應該仍然返回路徑，但會記錄警告
        assertThat(webdavPath).isEqualTo("/dav/456/documents/file.txt");
    }
    
    // ===== isWebDavPath 測試 =====
    
    @ParameterizedTest
    @DisplayName("檢查是否為 WebDAV 路徑")
    @ValueSource(strings = {
        "/dav",
        "/dav/",
        "/dav/documents",
        "/dav/documents/file.txt"
    })
    void testIsWebDavPath_ValidPaths(String path) {
        // When & Then
        assertThat(pathConverter.isWebDavPath(path)).isTrue();
    }
    
    @ParameterizedTest
    @DisplayName("檢查非 WebDAV 路徑")
    @ValueSource(strings = {
        "/webdav",
        "/123/documents",
        "/files/test.txt",
        "dav/test",
        "/dave/test"
    })
    void testIsWebDavPath_InvalidPaths(String path) {
        // When & Then
        assertThat(pathConverter.isWebDavPath(path)).isFalse();
    }
    
    @Test
    @DisplayName("檢查 null 路徑不是 WebDAV 路徑")
    void testIsWebDavPath_NullPath() {
        // When & Then
        assertThat(pathConverter.isWebDavPath(null)).isFalse();
    }
    
    // ===== normalizeWebDavPath 測試 =====
    
    @ParameterizedTest
    @DisplayName("標準化各種路徑為 WebDAV 路徑")
    @CsvSource({
        "'', /dav",
        "documents, /dav/documents",
        "/documents, /dav/documents",
        "documents/file.txt, /dav/documents/file.txt",
        "/dav/documents, /dav/documents"
    })
    void testNormalizeWebDavPath(String input, String expected) {
        // When
        String normalized = pathConverter.normalizeWebDavPath(input);
        
        // Then
        assertThat(normalized).isEqualTo(expected);
    }
    
    @Test
    @DisplayName("標準化 null 路徑")
    void testNormalizeWebDavPath_Null() {
        // When
        String normalized = pathConverter.normalizeWebDavPath(null);
        
        // Then
        assertThat(normalized).isEqualTo("/dav");
    }
    
    // ===== getWebDavPrefix 測試 =====
    
    @Test
    @DisplayName("獲取 WebDAV 前綴")
    void testGetWebDavPrefix() {
        // When
        String prefix = pathConverter.getWebDavPrefix();
        
        // Then
        assertThat(prefix).isEqualTo("/dav");
    }
    
    // ===== 邊界情況測試 =====
    
    @Test
    @DisplayName("處理特殊字符路徑")
    void testSpecialCharacterPaths() {
        // Given
        String webdavPath = "/dav/path with spaces/file (1).txt";
        
        // When
        String internalPath = pathConverter.toInternalPath(webdavPath, USER_ID);
        String backToWebDav = pathConverter.toWebDavPath(internalPath, USER_ID);
        
        // Then
        assertThat(internalPath).isEqualTo("/123/path with spaces/file (1).txt");
        assertThat(backToWebDav).isEqualTo(webdavPath);
    }
    
    @Test
    @DisplayName("雙向轉換應該保持一致")
    void testBidirectionalConversion() {
        // Given
        String originalWebDavPath = "/dav/documents/report.pdf";
        
        // When
        String internalPath = pathConverter.toInternalPath(originalWebDavPath, USER_ID);
        String convertedBack = pathConverter.toWebDavPath(internalPath, USER_ID);
        
        // Then
        assertThat(convertedBack).isEqualTo(originalWebDavPath);
    }
    
    @Test
    @DisplayName("處理多層深度路徑")
    void testDeepNestedPath() {
        // Given
        String deepPath = "/dav/a/b/c/d/e/f/g/h/i/j/file.txt";
        
        // When
        String internalPath = pathConverter.toInternalPath(deepPath, USER_ID);
        
        // Then
        assertThat(internalPath).isEqualTo("/123/a/b/c/d/e/f/g/h/i/j/file.txt");
    }
}