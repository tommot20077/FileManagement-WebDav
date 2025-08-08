package dowob.xyz.filemanagementwebdav.config.properties;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

import java.time.Duration;

/**
 * WebDAV 上傳配置屬性
 * 
 * 提供靈活的上傳參數配置選項，讓系統更容易調整和優化
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavUploadProperties
 * @create 2025/8/8
 * @Version 1.0
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "webdav.upload")
public class WebDavUploadProperties {
    
    /**
     * 簡單上傳最大檔案大小（位元組）
     * 超過此大小的檔案將使用串流上傳
     * 預設：10MB
     */
    @NotNull
    @Min(value = 1024, message = "Simple upload max size must be at least 1KB")
    private Long maxSimpleUploadSize = 10 * 1024 * 1024L; // 10MB
    
    /**
     * 串流上傳分塊大小（位元組）
     * 預設：1MB
     */
    @NotNull
    @Min(value = 1024, message = "Stream buffer size must be at least 1KB")
    private Integer streamBufferSize = 1024 * 1024; // 1MB
    
    /**
     * 上傳超時時間（秒）
     * 預設：300秒（5分鐘）
     */
    @NotNull
    @Min(value = 10, message = "Timeout must be at least 10 seconds")
    private Integer timeoutSeconds = 300;
    
    /**
     * 是否啟用資源監控
     * 預設：true
     */
    @NotNull
    private Boolean enableResourceMonitoring = true;
    
    /**
     * 並發上傳限制
     * 同時進行的上傳數量限制
     * 預設：50
     */
    @NotNull
    @Min(value = 1, message = "Concurrent upload limit must be at least 1")
    private Integer concurrentUploadLimit = 50;
    
    /**
     * 上傳重試次數
     * 上傳失敗時的重試次數
     * 預設：3
     */
    @NotNull
    @Min(value = 0, message = "Retry count cannot be negative")
    private Integer retryCount = 3;
    
    /**
     * 重試間隔（毫秒）
     * 重試之間的等待時間
     * 預設：1000毫秒（1秒）
     */
    @NotNull
    @Min(value = 100, message = "Retry delay must be at least 100ms")
    private Long retryDelayMillis = 1000L;
    
    /**
     * 上傳進度報告間隔（分塊數）
     * 每上傳多少個分塊報告一次進度
     * 預設：10
     */
    @NotNull
    @Min(value = 1, message = "Progress report interval must be at least 1")
    private Integer progressReportInterval = 10;
    
    /**
     * 是否啟用 MD5 校驗
     * 預設：true
     */
    @NotNull
    private Boolean enableMd5Verification = true;
    
    /**
     * 臨時檔案清理間隔（分鐘）
     * 定期清理未完成的上傳臨時資源
     * 預設：60分鐘
     */
    @NotNull
    @Min(value = 1, message = "Cleanup interval must be at least 1 minute")
    private Integer tempFileCleanupIntervalMinutes = 60;
    
    /**
     * 上傳統計報告間隔（分鐘）
     * 定期輸出上傳統計信息
     * 預設：15分鐘
     */
    @NotNull
    @Min(value = 1, message = "Stats report interval must be at least 1 minute")
    private Integer statsReportIntervalMinutes = 15;
    
    // 便利方法
    
    /**
     * 獲取上傳超時時間作為 Duration
     */
    public Duration getTimeoutDuration() {
        return Duration.ofSeconds(timeoutSeconds);
    }
    
    /**
     * 獲取重試間隔作為 Duration
     */
    public Duration getRetryDelay() {
        return Duration.ofMillis(retryDelayMillis);
    }
    
    /**
     * 獲取臨時檔案清理間隔作為 Duration
     */
    public Duration getTempFileCleanupInterval() {
        return Duration.ofMinutes(tempFileCleanupIntervalMinutes);
    }
    
    /**
     * 獲取統計報告間隔作為 Duration
     */
    public Duration getStatsReportInterval() {
        return Duration.ofMinutes(statsReportIntervalMinutes);
    }
    
    /**
     * 檢查檔案大小是否應該使用簡單上傳
     */
    public boolean shouldUseSimpleUpload(Long fileSize) {
        return fileSize != null && fileSize <= maxSimpleUploadSize;
    }
    
    /**
     * 獲取格式化的配置摘要
     */
    public String getConfigSummary() {
        return String.format(
            "WebDAV Upload Config: MaxSimple=%dMB, BufferSize=%dKB, Timeout=%ds, " +
            "ConcurrentLimit=%d, ResourceMonitoring=%s, MD5Verification=%s",
            maxSimpleUploadSize / (1024 * 1024),
            streamBufferSize / 1024,
            timeoutSeconds,
            concurrentUploadLimit,
            enableResourceMonitoring,
            enableMd5Verification
        );
    }
}