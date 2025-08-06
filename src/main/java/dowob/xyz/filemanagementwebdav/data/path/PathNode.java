package dowob.xyz.filemanagementwebdav.data.path;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路徑節點資料結構，用於建立檔案系統的樹狀結構映射
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Getter
@Setter
public class PathNode {
    /**
     * 檔案或資料夾的唯一識別碼
     */
    private Long fileId;
    
    /**
     * 原始檔案名稱（主服務中的實際名稱）
     */
    private String originalName;
    
    /**
     * WebDAV 顯示名稱（處理重複後的唯一名稱）
     */
    private String webdavName;
    
    /**
     * 父節點 ID
     */
    private Long parentId;
    
    /**
     * 是否為資料夾
     */
    private boolean isDirectory;
    
    /**
     * 子節點映射表，key 為 webdavName
     */
    private Map<String, PathNode> children;
    
    /**
     * 用戶 ID（檔案擁有者）
     */
    private Long userId;
    
    /**
     * 完整路徑（快取用）
     */
    private String fullPath;
    
    public PathNode() {
        this.children = new ConcurrentHashMap<>();
    }
    
    public PathNode(Long fileId, String originalName, Long parentId, boolean isDirectory, Long userId) {
        this();
        this.fileId = fileId;
        this.originalName = originalName;
        this.webdavName = originalName; // 初始相同，後續可能修改
        this.parentId = parentId;
        this.isDirectory = isDirectory;
        this.userId = userId;
    }
    
    /**
     * 添加子節點
     * 
     * @param child 子節點
     */
    public void addChild(PathNode child) {
        if (children != null && child != null) {
            children.put(child.getWebdavName(), child);
        }
    }
    
    /**
     * 根據名稱獲取子節點
     * 
     * @param name WebDAV 名稱
     * @return 子節點，如果不存在則返回 null
     */
    public PathNode getChild(String name) {
        return children != null ? children.get(name) : null;
    }
    
    /**
     * 檢查是否有指定名稱的子節點
     * 
     * @param name 檔案名稱
     * @return 是否存在
     */
    public boolean hasChild(String name) {
        return children != null && children.containsKey(name);
    }
    
    /**
     * 移除子節點
     * 
     * @param name WebDAV 名稱
     * @return 被移除的節點，如果不存在則返回 null
     */
    public PathNode removeChild(String name) {
        return children != null ? children.remove(name) : null;
    }
}