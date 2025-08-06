package dowob.xyz.filemanagementwebdav.controller;

import dowob.xyz.filemanagementwebdav.service.PathMappingService;
import dowob.xyz.filemanagementwebdav.component.path.PathMappingInitializer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 路徑映射管理控制器
 * 提供路徑映射的監控和管理功能
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/admin/path-mapping")
@RequiredArgsConstructor
public class PathMappingController {
    
    private final PathMappingService pathMappingService;
    private final PathMappingInitializer pathMappingInitializer;
    
    /**
     * 獲取快取統計資訊
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(pathMappingService.getCacheStats());
    }
    
    /**
     * 解析路徑到檔案 ID
     */
    @GetMapping("/resolve/path")
    public ResponseEntity<Map<String, Object>> resolvePath(@RequestParam String path) {
        Long fileId = pathMappingService.resolvePathToId(path);
        
        Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("fileId", fileId);
        result.put("found", fileId != null);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 解析檔案 ID 到路徑
     */
    @GetMapping("/resolve/id")
    public ResponseEntity<Map<String, Object>> resolveId(@RequestParam Long fileId) {
        String path = pathMappingService.resolveIdToPath(fileId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("path", path);
        result.put("found", path != null);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 手動觸發同步
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> triggerSync() {
        pathMappingInitializer.triggerSync();
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "Synchronization triggered");
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 清除指定用戶的快取
     */
    @DeleteMapping("/cache/user/{userId}")
    public ResponseEntity<Map<String, Object>> clearUserCache(@PathVariable Long userId) {
        pathMappingService.clearUserCache(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "User cache cleared");
        result.put("userId", userId);
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 清除所有快取
     */
    @DeleteMapping("/cache/all")
    public ResponseEntity<Map<String, Object>> clearAllCache() {
        pathMappingInitializer.clearAllCache();
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "All caches cleared");
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }
}