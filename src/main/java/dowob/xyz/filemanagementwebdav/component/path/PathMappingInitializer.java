package dowob.xyz.filemanagementwebdav.component.path;

import dowob.xyz.filemanagementwebdav.config.properties.WebDavPathMappingProperties;
import dowob.xyz.filemanagementwebdav.service.GrpcClientService;
import dowob.xyz.filemanagementwebdav.service.PathMappingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 路徑映射初始化器，負責啟動時預載入和定期同步路徑映射
 * 
 * 從配置文件讀取同步間隔等參數，提供靈活的路徑映射管理功能。
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Log4j2
@Component
public class PathMappingInitializer {
    
    private final PathMappingService pathMappingService;
    private final GrpcClientService grpcClientService;
    private final WebDavPathMappingProperties pathMappingProperties;
    
    @Autowired
    public PathMappingInitializer(
            PathMappingService pathMappingService,
            GrpcClientService grpcClientService,
            WebDavPathMappingProperties pathMappingProperties) {
        this.pathMappingService = pathMappingService;
        this.grpcClientService = grpcClientService;
        this.pathMappingProperties = pathMappingProperties;
    }
    
    /**
     * 初始化方法，在應用啟動後執行
     * 路徑映射預載入功能在專用 WebDAV 子服務中強制啟用
     */
    @PostConstruct
    public void initialize() {
        // 異步執行初始化，避免阻塞應用啟動
        CompletableFuture.runAsync(() -> {
            try {
                // 等待其他服務啟動完成
                TimeUnit.SECONDS.sleep(5);
                
                log.info("Starting path mapping initialization...");
                long startTime = System.currentTimeMillis();
                
                // TODO: 從主服務載入常用路徑
                // 例如：載入最近活躍的用戶根目錄
                // preloadActiveUserRoots();
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("Path mapping initialization completed in {} ms", duration);
                
            } catch (Exception e) {
                log.error("Failed to initialize path mapping", e);
            }
        });
    }
    
    /**
     * 定期同步路徑映射
     * 使用配置文件中的間隔時間，通過 fixedDelayString 支援 SpEL 表達式
     */
    @Scheduled(fixedDelayString = "#{@webDavPathMappingProperties.syncInterval * 1000}")
    public void syncPathMappings() {
        int configuredSyncInterval = pathMappingProperties.getSyncInterval();
        if (configuredSyncInterval <= 0) {
            log.debug("Path mapping synchronization is disabled (syncInterval <= 0)");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("開始路徑映射同步... (間隔: {}秒)", configuredSyncInterval);
                long startTime = System.currentTimeMillis();
                
                // 獲取快取統計
                var stats = pathMappingService.getCacheStats();
                log.debug("同步前快取統計: {}", stats);
                
                // TODO: 實作同步邏輯
                // 1. 檢查快取中的過期項目
                // 2. 從主服務更新變更的檔案
                // 3. 清理無效的映射
                
                long duration = System.currentTimeMillis() - startTime;
                log.debug("路徑映射同步完成，耗時 {} 毫秒", duration);
                
            } catch (Exception e) {
                log.error("路徑映射同步失敗", e);
            }
        });
    }
    
    /**
     * 預載入活躍用戶的根目錄
     */
    private void preloadActiveUserRoots() {
        // TODO: 實作預載入邏輯
        // 1. 從主服務獲取最近活躍的用戶列表
        // 2. 為每個用戶載入根目錄結構
        // 3. 快取常用資料夾的內容
        
        log.debug("Preloading active user roots...");
    }
    
    /**
     * 手動觸發同步（供管理介面使用）
     */
    public void triggerSync() {
        log.info("Manually triggering path mapping synchronization");
        syncPathMappings();
    }
    
    /**
     * 清除所有快取（供管理介面使用）
     */
    public void clearAllCache() {
        log.warn("Clearing all path mapping caches");
        // TODO: 實作清除所有快取的邏輯
    }
}