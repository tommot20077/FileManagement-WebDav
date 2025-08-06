package dowob.xyz.filemanagementwebdav.component.path;

import dowob.xyz.filemanagementwebdav.service.GrpcClientService;
import dowob.xyz.filemanagementwebdav.service.PathMappingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 路徑映射初始化器，負責啟動時預載入和定期同步路徑映射
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Component
public class PathMappingInitializer {
    
    private static final Logger log = LoggerFactory.getLogger(PathMappingInitializer.class);
    
    private final PathMappingService pathMappingService;
    private final GrpcClientService grpcClientService;
    
    private final int syncInterval = 300; // 同步間隔（秒），專用子服務中固定值
    
    @Autowired
    public PathMappingInitializer(
            PathMappingService pathMappingService,
            GrpcClientService grpcClientService) {
        this.pathMappingService = pathMappingService;
        this.grpcClientService = grpcClientService;
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
     * 使用 cron 表達式，每隔指定時間執行一次
     */
    @Scheduled(fixedDelay = 300000) // 300秒 = 5分鐘
    public void syncPathMappings() {
        if (syncInterval <= 0) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting path mapping synchronization...");
                long startTime = System.currentTimeMillis();
                
                // 獲取快取統計
                var stats = pathMappingService.getCacheStats();
                log.debug("Cache stats before sync: {}", stats);
                
                // TODO: 實作同步邏輯
                // 1. 檢查快取中的過期項目
                // 2. 從主服務更新變更的檔案
                // 3. 清理無效的映射
                
                long duration = System.currentTimeMillis() - startTime;
                log.debug("Path mapping synchronization completed in {} ms", duration);
                
            } catch (Exception e) {
                log.error("Failed to synchronize path mappings", e);
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