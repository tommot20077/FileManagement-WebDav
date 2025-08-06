package dowob.xyz.filemanagementwebdav.data.path;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 路徑映射記錄，用於快取路徑與檔案 ID 的對應關係
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathMapping {
    /**
     * 完整的 WebDAV 路徑（如 /user1/folder/file.txt）
     */
    private String fullPath;
    
    /**
     * 對應的檔案 ID
     */
    private Long fileId;
    
    /**
     * 用戶 ID
     */
    private Long userId;
    
    /**
     * 原始檔案名稱
     */
    private String originalName;
    
    /**
     * WebDAV 顯示名稱（處理重複後的名稱）
     */
    private String webdavName;
    
    /**
     * 父資料夾 ID
     */
    private Long parentId;
    
    /**
     * 是否為資料夾
     */
    private boolean isDirectory;
    
    /**
     * 最後存取時間（用於 LRU 快取策略）
     */
    private LocalDateTime lastAccess;
    
    /**
     * 建立時間
     */
    private LocalDateTime createTime;
    
    /**
     * 更新快取的存取時間
     */
    public void updateAccessTime() {
        this.lastAccess = LocalDateTime.now();
    }
}