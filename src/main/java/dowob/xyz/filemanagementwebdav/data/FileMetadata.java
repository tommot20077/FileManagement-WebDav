package dowob.xyz.filemanagementwebdav.data;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName FileMetadata
 * @create 2025/6/9
 * @Version 1.0
 **/

@Data
@Builder
public class FileMetadata {
    private String id;
    private String name;
    private String path;
    private long size;
    private String contentType;
    private boolean isDirectory;
    private LocalDateTime createDate;
    private LocalDateTime modifiedDate;
    
    // 新增欄位以支援路徑映射
    private Long parentId;
    private Long createTimestamp;
    private Long modifiedTimestamp;
    
    /**
     * 獲取數字類型的 ID
     */
    public Long getId() {
        try {
            return id != null ? Long.parseLong(id) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 獲取建立時間戳記
     */
    public Long getCreateTimestamp() {
        if (createTimestamp != null) {
            return createTimestamp;
        }
        return createDate != null ? 
            createDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null;
    }
    
    /**
     * 獲取修改時間戳記
     */
    public Long getModifiedTimestamp() {
        if (modifiedTimestamp != null) {
            return modifiedTimestamp;
        }
        return modifiedDate != null ? 
            modifiedDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null;
    }
}