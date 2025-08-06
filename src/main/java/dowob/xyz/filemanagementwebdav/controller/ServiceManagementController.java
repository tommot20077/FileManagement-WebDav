package dowob.xyz.filemanagementwebdav.controller;

import dowob.xyz.filemanagementwebdav.component.service.WebDavToggleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 服務管理控制器
 * 
 * 提供 WebDAV 服務的管理端點，包括：
 * - 服務開關控制
 * - 服務狀態查看
 * - 健康檢查
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName ServiceManagementController
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/service")
@RequiredArgsConstructor
public class ServiceManagementController {
    
    private final WebDavToggleService toggleService;
    
    /**
     * 獲取服務狀態
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getServiceStatus() {
        try {
            boolean enabled = toggleService.isServiceEnabled();
            boolean available = toggleService.isServiceAvailable();
            String statusInfo = toggleService.getServiceStatus();
            
            Map<String, Object> status = Map.of(
                "enabled", enabled,
                "available", available,
                "status", enabled ? "RUNNING" : "STOPPED",
                "details", statusInfo
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting service status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "獲取服務狀態失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 啟用服務
     */
    @PostMapping("/enable")
    public ResponseEntity<Map<String, String>> enableService(@RequestBody(required = false) Map<String, String> request) {
        String reason = request != null ? request.get("reason") : null;
        
        try {
            WebDavToggleService.ToggleResult result = toggleService.enableService(reason);
            
            if (result.isSuccess()) {
                log.info("Service enable request completed: {}", result.getMessage());
                return ResponseEntity.ok(Map.of(
                    "message", result.getMessage(),
                    "changed", String.valueOf(result.isChanged())
                ));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", result.getMessage()));
            }
            
        } catch (Exception e) {
            log.error("Error enabling service", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "啟用服務失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 禁用服務
     */
    @PostMapping("/disable")
    public ResponseEntity<Map<String, String>> disableService(@RequestBody(required = false) Map<String, String> request) {
        String reason = request != null ? request.get("reason") : null;
        
        try {
            WebDavToggleService.ToggleResult result = toggleService.disableService(reason);
            
            if (result.isSuccess()) {
                log.info("Service disable request completed: {}", result.getMessage());
                return ResponseEntity.ok(Map.of(
                    "message", result.getMessage(),
                    "changed", String.valueOf(result.isChanged())
                ));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", result.getMessage()));
            }
            
        } catch (Exception e) {
            log.error("Error disabling service", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "禁用服務失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 切換服務狀態
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, String>> toggleService(@RequestBody(required = false) Map<String, String> request) {
        String reason = request != null ? request.get("reason") : null;
        
        try {
            WebDavToggleService.ToggleResult result = toggleService.toggleService(reason);
            
            if (result.isSuccess()) {
                log.info("Service toggle request completed: {}", result.getMessage());
                return ResponseEntity.ok(Map.of(
                    "message", result.getMessage(),
                    "changed", String.valueOf(result.isChanged()),
                    "currentState", toggleService.isServiceEnabled() ? "ENABLED" : "DISABLED"
                ));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", result.getMessage()));
            }
            
        } catch (Exception e) {
            log.error("Error toggling service", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "切換服務狀態失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 重置服務到默認狀態
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetService(@RequestBody(required = false) Map<String, String> request) {
        String reason = request != null ? request.get("reason") : null;
        
        try {
            WebDavToggleService.ToggleResult result = toggleService.resetToDefault(reason);
            
            if (result.isSuccess()) {
                log.info("Service reset request completed: {}", result.getMessage());
                return ResponseEntity.ok(Map.of(
                    "message", result.getMessage(),
                    "changed", String.valueOf(result.isChanged())
                ));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", result.getMessage()));
            }
            
        } catch (Exception e) {
            log.error("Error resetting service", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "重置服務失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 健康檢查端點
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            boolean available = toggleService.isServiceAvailable();
            String status = available ? "UP" : "DOWN";
            
            Map<String, Object> health = Map.of(
                "status", status,
                "available", available,
                "timestamp", System.currentTimeMillis()
            );
            
            return available 
                ? ResponseEntity.ok(health)
                : ResponseEntity.status(503).body(health);
            
        } catch (Exception e) {
            log.error("Error during health check", e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
}