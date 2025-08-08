package dowob.xyz.filemanagementwebdav.service;

import dowob.xyz.filemanagementwebdav.config.properties.WebDavUploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebDAV 資源監控服務
 * 
 * 負責監控檔案上傳過程中的資源使用情況，提供統計報告和警告
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavResourceMonitorService
 * @create 2025/8/8
 * @Version 1.0
 */
@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "webdav.upload",
    name = "enable-resource-monitoring", 
    havingValue = "true",
    matchIfMissing = true
)
public class WebDavResourceMonitorService {
    
    private final WebDavUploadProperties uploadProperties;
    private final MemoryMXBean memoryBean;
    private final RuntimeMXBean runtimeBean;
    
    // 統計計數器
    private final AtomicLong totalUploads = new AtomicLong(0);
    private final AtomicLong successfulUploads = new AtomicLong(0);
    private final AtomicLong failedUploads = new AtomicLong(0);
    private final AtomicLong totalBytesUploaded = new AtomicLong(0);
    private final AtomicLong currentActiveUploads = new AtomicLong(0);
    private final AtomicLong simpleUploads = new AtomicLong(0);
    private final AtomicLong streamingUploads = new AtomicLong(0);
    
    @Autowired
    public WebDavResourceMonitorService(WebDavUploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        
        log.info("WebDavResourceMonitorService initialized - Resource monitoring enabled");
    }
    
    /**
     * 記錄上傳開始
     */
    public void recordUploadStart(Long fileSize, boolean isSimple) {
        totalUploads.incrementAndGet();
        currentActiveUploads.incrementAndGet();
        
        if (isSimple) {
            simpleUploads.incrementAndGet();
        } else {
            streamingUploads.incrementAndGet();
        }
        
        if (fileSize != null) {
            totalBytesUploaded.addAndGet(fileSize);
        }
        
        // 檢查並發上傳限制
        long activeCount = currentActiveUploads.get();
        if (activeCount > uploadProperties.getConcurrentUploadLimit()) {
            log.warn("Concurrent upload limit exceeded: {} active uploads (limit: {})", 
                    activeCount, uploadProperties.getConcurrentUploadLimit());
        }
    }
    
    /**
     * 記錄上傳完成
     */
    public void recordUploadComplete(boolean success) {
        currentActiveUploads.decrementAndGet();
        
        if (success) {
            successfulUploads.incrementAndGet();
        } else {
            failedUploads.incrementAndGet();
        }
    }
    
    /**
     * 獲取當前統計信息
     */
    public UploadStatistics getCurrentStatistics() {
        return UploadStatistics.builder()
                .totalUploads(totalUploads.get())
                .successfulUploads(successfulUploads.get())
                .failedUploads(failedUploads.get())
                .totalBytesUploaded(totalBytesUploaded.get())
                .currentActiveUploads(currentActiveUploads.get())
                .simpleUploads(simpleUploads.get())
                .streamingUploads(streamingUploads.get())
                .successRate(calculateSuccessRate())
                .averageUploadSize(calculateAverageUploadSize())
                .build();
    }
    
    /**
     * 定期報告統計信息
     */
    @Scheduled(fixedRateString = "#{T(java.time.Duration).parse('PT' + @webDavUploadProperties.statsReportIntervalMinutes + 'M').toMillis()}")
    public void reportStatistics() {
        if (!uploadProperties.getEnableResourceMonitoring()) {
            return;
        }
        
        UploadStatistics stats = getCurrentStatistics();
        MemoryInfo memoryInfo = getCurrentMemoryInfo();
        
        log.info("=== WebDAV Upload Statistics Report ===");
        log.info("Total Uploads: {} (Simple: {}, Streaming: {})", 
                stats.getTotalUploads(), stats.getSimpleUploads(), stats.getStreamingUploads());
        log.info("Success Rate: {:.1f}% ({} successful, {} failed)", 
                stats.getSuccessRate(), stats.getSuccessfulUploads(), stats.getFailedUploads());
        log.info("Total Data: {:.2f} MB (Average: {:.2f} MB per upload)", 
                stats.getTotalBytesUploaded() / (1024.0 * 1024.0), 
                stats.getAverageUploadSize() / (1024.0 * 1024.0));
        log.info("Active Uploads: {} (Limit: {})", 
                stats.getCurrentActiveUploads(), uploadProperties.getConcurrentUploadLimit());
        log.info("Memory Usage: {:.1f}% ({:.1f}/{:.1f} MB)", 
                memoryInfo.getUsagePercentage(),
                memoryInfo.getUsedMemory() / (1024.0 * 1024.0),
                memoryInfo.getMaxMemory() / (1024.0 * 1024.0));
        log.info("JVM Uptime: {:.1f} hours", 
                runtimeBean.getUptime() / (1000.0 * 60.0 * 60.0));
        
        // 記憶體警告
        if (memoryInfo.getUsagePercentage() > 80.0) {
            log.warn("HIGH MEMORY USAGE WARNING: {:.1f}% memory used!", 
                    memoryInfo.getUsagePercentage());
        }
        
        log.info("=========================================");
    }
    
    /**
     * 定期清理過期資源（模擬）
     */
    @Scheduled(fixedRateString = "#{T(java.time.Duration).parse('PT' + @webDavUploadProperties.tempFileCleanupIntervalMinutes + 'M').toMillis()}")
    public void cleanupExpiredResources() {
        if (!uploadProperties.getEnableResourceMonitoring()) {
            return;
        }
        
        // 這裡可以實現實際的資源清理邏輯
        // 例如清理未完成的上傳、臨時檔案等
        
        log.debug("Performing periodic resource cleanup...");
        
        // 建議 GC（僅在記憶體使用率高時）
        MemoryInfo memoryInfo = getCurrentMemoryInfo();
        if (memoryInfo.getUsagePercentage() > 70.0) {
            log.info("Memory usage high ({:.1f}%), suggesting garbage collection", 
                    memoryInfo.getUsagePercentage());
            System.gc();
        }
    }
    
    /**
     * 獲取當前記憶體信息
     */
    private MemoryInfo getCurrentMemoryInfo() {
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        double usagePercentage = (double) usedMemory / maxMemory * 100.0;
        
        return new MemoryInfo(maxMemory, usedMemory, usagePercentage);
    }
    
    /**
     * 計算成功率
     */
    private double calculateSuccessRate() {
        long total = totalUploads.get();
        if (total == 0) return 100.0;
        return (double) successfulUploads.get() / total * 100.0;
    }
    
    /**
     * 計算平均上傳大小
     */
    private double calculateAverageUploadSize() {
        long total = totalUploads.get();
        if (total == 0) return 0.0;
        return (double) totalBytesUploaded.get() / total;
    }
    
    /**
     * 重置統計信息
     */
    public void resetStatistics() {
        totalUploads.set(0);
        successfulUploads.set(0);
        failedUploads.set(0);
        totalBytesUploaded.set(0);
        simpleUploads.set(0);
        streamingUploads.set(0);
        log.info("Upload statistics reset");
    }
    
    // 資料結構
    
    public static class UploadStatistics {
        private final long totalUploads;
        private final long successfulUploads;
        private final long failedUploads;
        private final long totalBytesUploaded;
        private final long currentActiveUploads;
        private final long simpleUploads;
        private final long streamingUploads;
        private final double successRate;
        private final double averageUploadSize;
        
        public UploadStatistics(long totalUploads, long successfulUploads, long failedUploads,
                               long totalBytesUploaded, long currentActiveUploads, long simpleUploads,
                               long streamingUploads, double successRate, double averageUploadSize) {
            this.totalUploads = totalUploads;
            this.successfulUploads = successfulUploads;
            this.failedUploads = failedUploads;
            this.totalBytesUploaded = totalBytesUploaded;
            this.currentActiveUploads = currentActiveUploads;
            this.simpleUploads = simpleUploads;
            this.streamingUploads = streamingUploads;
            this.successRate = successRate;
            this.averageUploadSize = averageUploadSize;
        }
        
        public static UploadStatisticsBuilder builder() {
            return new UploadStatisticsBuilder();
        }
        
        // Getters
        public long getTotalUploads() { return totalUploads; }
        public long getSuccessfulUploads() { return successfulUploads; }
        public long getFailedUploads() { return failedUploads; }
        public long getTotalBytesUploaded() { return totalBytesUploaded; }
        public long getCurrentActiveUploads() { return currentActiveUploads; }
        public long getSimpleUploads() { return simpleUploads; }
        public long getStreamingUploads() { return streamingUploads; }
        public double getSuccessRate() { return successRate; }
        public double getAverageUploadSize() { return averageUploadSize; }
        
        public static class UploadStatisticsBuilder {
            private long totalUploads;
            private long successfulUploads;
            private long failedUploads;
            private long totalBytesUploaded;
            private long currentActiveUploads;
            private long simpleUploads;
            private long streamingUploads;
            private double successRate;
            private double averageUploadSize;
            
            public UploadStatisticsBuilder totalUploads(long totalUploads) {
                this.totalUploads = totalUploads;
                return this;
            }
            
            public UploadStatisticsBuilder successfulUploads(long successfulUploads) {
                this.successfulUploads = successfulUploads;
                return this;
            }
            
            public UploadStatisticsBuilder failedUploads(long failedUploads) {
                this.failedUploads = failedUploads;
                return this;
            }
            
            public UploadStatisticsBuilder totalBytesUploaded(long totalBytesUploaded) {
                this.totalBytesUploaded = totalBytesUploaded;
                return this;
            }
            
            public UploadStatisticsBuilder currentActiveUploads(long currentActiveUploads) {
                this.currentActiveUploads = currentActiveUploads;
                return this;
            }
            
            public UploadStatisticsBuilder simpleUploads(long simpleUploads) {
                this.simpleUploads = simpleUploads;
                return this;
            }
            
            public UploadStatisticsBuilder streamingUploads(long streamingUploads) {
                this.streamingUploads = streamingUploads;
                return this;
            }
            
            public UploadStatisticsBuilder successRate(double successRate) {
                this.successRate = successRate;
                return this;
            }
            
            public UploadStatisticsBuilder averageUploadSize(double averageUploadSize) {
                this.averageUploadSize = averageUploadSize;
                return this;
            }
            
            public UploadStatistics build() {
                return new UploadStatistics(totalUploads, successfulUploads, failedUploads,
                        totalBytesUploaded, currentActiveUploads, simpleUploads, streamingUploads,
                        successRate, averageUploadSize);
            }
        }
    }
    
    private static class MemoryInfo {
        private final long maxMemory;
        private final long usedMemory;
        private final double usagePercentage;
        
        public MemoryInfo(long maxMemory, long usedMemory, double usagePercentage) {
            this.maxMemory = maxMemory;
            this.usedMemory = usedMemory;
            this.usagePercentage = usagePercentage;
        }
        
        public long getMaxMemory() { return maxMemory; }
        public long getUsedMemory() { return usedMemory; }
        public double getUsagePercentage() { return usagePercentage; }
    }
}