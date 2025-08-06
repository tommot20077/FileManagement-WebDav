package dowob.xyz.filemanagementwebdav.component.path;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 路徑解析器，負責解析和處理 WebDAV 路徑
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Component
public class PathResolver {
    
    private static final String PATH_SEPARATOR = "/";
    
    /**
     * 解析路徑為路徑段列表
     * 
     * @param path 完整路徑（如 /user1/folder/file.txt）
     * @return 路徑段列表（如 ["user1", "folder", "file.txt"]）
     */
    public List<String> parsePath(String path) {
        if (path == null || path.isEmpty() || path.equals(PATH_SEPARATOR)) {
            return new ArrayList<>();
        }
        
        // 移除開頭和結尾的斜線
        String cleanPath = path.trim();
        if (cleanPath.startsWith(PATH_SEPARATOR)) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanPath.endsWith(PATH_SEPARATOR)) {
            cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
        }
        
        // 分割路徑並過濾空字串
        return Arrays.stream(cleanPath.split(PATH_SEPARATOR))
                .filter(segment -> !segment.isEmpty())
                .collect(Collectors.toList());
    }
    
    /**
     * 構建完整路徑
     * 
     * @param segments 路徑段列表
     * @return 完整路徑
     */
    public String buildPath(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return PATH_SEPARATOR;
        }
        
        return PATH_SEPARATOR + String.join(PATH_SEPARATOR, segments);
    }
    
    /**
     * 構建完整路徑
     * 
     * @param parentPath 父路徑
     * @param fileName 檔案名稱
     * @return 完整路徑
     */
    public String buildPath(String parentPath, String fileName) {
        if (parentPath == null || parentPath.isEmpty()) {
            parentPath = PATH_SEPARATOR;
        }
        
        if (!parentPath.endsWith(PATH_SEPARATOR)) {
            parentPath += PATH_SEPARATOR;
        }
        
        return parentPath + fileName;
    }
    
    /**
     * 獲取父路徑
     * 
     * @param path 完整路徑
     * @return 父路徑，根路徑返回 "/"
     */
    public String getParentPath(String path) {
        List<String> segments = parsePath(path);
        if (segments.size() <= 1) {
            return PATH_SEPARATOR;
        }
        
        segments.removeLast();
        return buildPath(segments);
    }
    
    /**
     * 獲取檔案名稱（路徑的最後一段）
     * 
     * @param path 完整路徑
     * @return 檔案名稱，如果是根路徑則返回空字串
     */
    public String getFileName(String path) {
        List<String> segments = parsePath(path);
        if (segments.isEmpty()) {
            return "";
        }
        
        return segments.getLast();
    }
    
    /**
     * 標準化路徑
     * 
     * @param path 原始路徑
     * @return 標準化後的路徑
     */
    public String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return PATH_SEPARATOR;
        }
        
        // 解析並重建路徑以確保格式一致
        List<String> segments = parsePath(path);
        return buildPath(segments);
    }
    
    /**
     * 判斷是否為根路徑
     * 
     * @param path 路徑
     * @return 是否為根路徑
     */
    public boolean isRootPath(String path) {
        return path == null || path.isEmpty() || path.equals(PATH_SEPARATOR);
    }
    
    /**
     * 計算路徑深度
     * 
     * @param path 路徑
     * @return 深度（根路徑為 0）
     */
    public int getPathDepth(String path) {
        return parsePath(path).size();
    }
    
    /**
     * 檢查路徑是否為另一個路徑的子路徑
     * 
     * @param childPath 可能的子路徑
     * @param parentPath 父路徑
     * @return 是否為子路徑
     */
    public boolean isChildPath(String childPath, String parentPath) {
        String normalizedChild = normalizePath(childPath);
        String normalizedParent = normalizePath(parentPath);
        
        if (normalizedParent.equals(PATH_SEPARATOR)) {
            return !normalizedChild.equals(PATH_SEPARATOR);
        }
        
        return normalizedChild.startsWith(normalizedParent + PATH_SEPARATOR);
    }
}