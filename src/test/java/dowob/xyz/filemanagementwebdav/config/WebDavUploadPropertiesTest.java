package dowob.xyz.filemanagementwebdav.config;

import dowob.xyz.filemanagementwebdav.config.properties.WebDavUploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebDavUploadProperties 配置測試
 */
@DisplayName("WebDAV 上傳配置屬性測試")
class WebDavUploadPropertiesTest {
    
    private WebDavUploadProperties properties;
    
    @BeforeEach
    void setUp() {
        properties = new WebDavUploadProperties();
    }
    
    @Test
    @DisplayName("測試預設配置值")
    void testDefaultValues() {
        assertThat(properties.getMaxSimpleUploadSize()).isEqualTo(10 * 1024 * 1024L); // 10MB
        assertThat(properties.getStreamBufferSize()).isEqualTo(1024 * 1024); // 1MB
        assertThat(properties.getTimeoutSeconds()).isEqualTo(300); // 5 minutes
        assertThat(properties.getEnableResourceMonitoring()).isTrue();
        assertThat(properties.getConcurrentUploadLimit()).isEqualTo(50);
        assertThat(properties.getRetryCount()).isEqualTo(3);
        assertThat(properties.getEnableMd5Verification()).isTrue();
    }
    
    @Test
    @DisplayName("測試檔案大小判斷邏輯")
    void testShouldUseSimpleUpload() {
        // 小於等於限制的檔案應該使用簡單上傳
        assertThat(properties.shouldUseSimpleUpload(5 * 1024 * 1024L)).isTrue(); // 5MB
        assertThat(properties.shouldUseSimpleUpload(10 * 1024 * 1024L)).isTrue(); // 10MB 正好等於限制
        
        // 大於限制的檔案應該使用串流上傳
        assertThat(properties.shouldUseSimpleUpload(15 * 1024 * 1024L)).isFalse(); // 15MB
        assertThat(properties.shouldUseSimpleUpload(100 * 1024 * 1024L)).isFalse(); // 100MB
        
        // null 檔案大小應該返回 false
        assertThat(properties.shouldUseSimpleUpload(null)).isFalse();
    }
    
    @Test
    @DisplayName("測試配置摘要生成")
    void testConfigSummary() {
        String summary = properties.getConfigSummary();
        
        assertThat(summary).isNotEmpty();
        assertThat(summary).contains("MaxSimple=10MB");
        assertThat(summary).contains("BufferSize=1024KB");
        assertThat(summary).contains("Timeout=300s");
        assertThat(summary).contains("ConcurrentLimit=50");
        assertThat(summary).contains("ResourceMonitoring=true");
        assertThat(summary).contains("MD5Verification=true");
    }
    
    @Test
    @DisplayName("測試自訂配置值")
    void testCustomValues() {
        // 設定自訂值
        properties.setMaxSimpleUploadSize(20 * 1024 * 1024L); // 20MB
        properties.setStreamBufferSize(2 * 1024 * 1024); // 2MB
        properties.setTimeoutSeconds(600); // 10 minutes
        properties.setEnableResourceMonitoring(false);
        properties.setConcurrentUploadLimit(100);
        properties.setEnableMd5Verification(false);
        
        // 驗證設定成功
        assertThat(properties.getMaxSimpleUploadSize()).isEqualTo(20 * 1024 * 1024L);
        assertThat(properties.getStreamBufferSize()).isEqualTo(2 * 1024 * 1024);
        assertThat(properties.getTimeoutSeconds()).isEqualTo(600);
        assertThat(properties.getEnableResourceMonitoring()).isFalse();
        assertThat(properties.getConcurrentUploadLimit()).isEqualTo(100);
        assertThat(properties.getEnableMd5Verification()).isFalse();
        
        // 測試更新後的邏輯
        assertThat(properties.shouldUseSimpleUpload(15 * 1024 * 1024L)).isTrue(); // 15MB 現在應該可以簡單上傳
        assertThat(properties.shouldUseSimpleUpload(25 * 1024 * 1024L)).isFalse(); // 25MB 超過新限制
    }
    
    @Test
    @DisplayName("測試時間 Duration 轉換")
    void testDurationConversions() {
        properties.setTimeoutSeconds(120);
        properties.setRetryDelayMillis(2000L);
        properties.setTempFileCleanupIntervalMinutes(30);
        properties.setStatsReportIntervalMinutes(10);
        
        assertThat(properties.getTimeoutDuration().getSeconds()).isEqualTo(120);
        assertThat(properties.getRetryDelay().toMillis()).isEqualTo(2000L);
        assertThat(properties.getTempFileCleanupInterval().toMinutes()).isEqualTo(30);
        assertThat(properties.getStatsReportInterval().toMinutes()).isEqualTo(10);
    }
}