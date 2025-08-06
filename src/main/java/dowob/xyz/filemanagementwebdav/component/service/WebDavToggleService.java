package dowob.xyz.filemanagementwebdav.component.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebDAV 服務開關管理
 * 
 * 提供動態啟用/禁用 WebDAV 服務的功能。
 * 支持運行時切換服務狀態，用於維護或緊急情況下的服務控制。
 * 
 * 注意：在 WebDAV 專用子服務中，此服務默認啟用。
 * toggle功能主要用於運行時的維護控制，而不是條件啟動。
 * 條件啟動應該在主服務中決定是否啟動此整個 WebDAV 子服務。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavToggleService
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Service
public class WebDavToggleService {
    
    private final AtomicBoolean serviceEnabled;
    
    public WebDavToggleService() {
        // 專用 WebDAV 子服務中所有功能強制啟用
        this.serviceEnabled = new AtomicBoolean(true);
        
        log.info("WebDavToggleService initialized - Service is always enabled in dedicated WebDAV sub-service");
    }
    
    /**
     * 檢查 WebDAV 服務是否啟用
     * 
     * @return true 如果服務已啟用，false 否則
     */
    public boolean isServiceEnabled() {
        return serviceEnabled.get();
    }
    
    /**
     * 啟用 WebDAV 服務
     * 
     * @param reason 啟用原因（用於日誌記錄）
     * @return 操作結果
     */
    public ToggleResult enableService(String reason) {
        boolean wasEnabled = serviceEnabled.getAndSet(true);
        
        if (wasEnabled) {
            log.debug("WebDAV service is already enabled");
            return ToggleResult.noChange("Service was already enabled");
        } else {
            log.info("WebDAV service enabled - Reason: {}", reason != null ? reason : "Not specified");
            return ToggleResult.success("Service enabled successfully");
        }
    }
    
    /**
     * 禁用 WebDAV 服務
     * 
     * @param reason 禁用原因（用於日誌記錄）
     * @return 操作結果
     */
    public ToggleResult disableService(String reason) {
        boolean wasEnabled = serviceEnabled.getAndSet(false);
        
        if (!wasEnabled) {
            log.debug("WebDAV service is already disabled");
            return ToggleResult.noChange("Service was already disabled");
        } else {
            log.warn("WebDAV service disabled - Reason: {}", reason != null ? reason : "Not specified");
            return ToggleResult.success("Service disabled successfully");
        }
    }
    
    /**
     * 切換 WebDAV 服務狀態
     * 
     * @param reason 切換原因（用於日誌記錄）
     * @return 操作結果
     */
    public ToggleResult toggleService(String reason) {
        boolean currentState = serviceEnabled.get();
        
        if (currentState) {
            return disableService(reason);
        } else {
            return enableService(reason);
        }
    }
    
    /**
     * 重置服務狀態到默認值
     * 
     * @param reason 重置原因（用於日誌記錄）
     * @return 操作結果
     */
    public ToggleResult resetToDefault(String reason) {
        final boolean defaultEnabled = true; // 專用子服務中默認為啟用
        boolean previousState = serviceEnabled.getAndSet(defaultEnabled);
        
        if (previousState == defaultEnabled) {
            log.debug("WebDAV service state already matches default: {}", defaultEnabled);
            return ToggleResult.noChange("Service state already matches default");
        } else {
            log.info("WebDAV service reset to default state: {} - Reason: {}", 
                    defaultEnabled, reason != null ? reason : "Not specified");
            return ToggleResult.success(String.format("Service reset to default state: %s", defaultEnabled));
        }
    }
    
    /**
     * 獲取服務狀態信息
     * 
     * @return 狀態資訊字符串
     */
    public String getServiceStatus() {
        final boolean defaultEnabled = true; // 專用子服務中默認為啟用
        return String.format("WebDAV Service - Current: %s, Default: %s", 
                            serviceEnabled.get() ? "ENABLED" : "DISABLED", 
                            defaultEnabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * 檢查服務是否可用（包含額外的健康檢查）
     * 
     * @return true 如果服務可用
     */
    public boolean isServiceAvailable() {
        if (!serviceEnabled.get()) {
            return false;
        }
        
        // 可以在這裡添加額外的健康檢查邏輯
        // 例如：檢查 gRPC 連接、資料庫連接等
        
        return true;
    }
    
    /**
     * 創建服務不可用的響應
     * 
     * @return 標準的服務不可用訊息
     */
    public String getServiceUnavailableMessage() {
        return "WebDAV service is currently disabled. Please contact the administrator.";
    }
    
    /**
     * 開關操作結果
     */
    public static class ToggleResult {
        private final boolean success;
        private final boolean changed;
        private final String message;
        
        private ToggleResult(boolean success, boolean changed, String message) {
            this.success = success;
            this.changed = changed;
            this.message = message;
        }
        
        public static ToggleResult success(String message) {
            return new ToggleResult(true, true, message);
        }
        
        public static ToggleResult noChange(String message) {
            return new ToggleResult(true, false, message);
        }
        
        public static ToggleResult error(String message) {
            return new ToggleResult(false, false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public boolean isChanged() {
            return changed;
        }
        
        public String getMessage() {
            return message;
        }
    }
}