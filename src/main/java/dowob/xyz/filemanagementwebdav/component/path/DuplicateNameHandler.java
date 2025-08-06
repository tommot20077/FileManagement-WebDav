package dowob.xyz.filemanagementwebdav.component.path;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 重複檔名處理器，負責為重複的檔案名稱生成唯一的 WebDAV 名稱
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Component
public class DuplicateNameHandler {
    
    /**
     * 用於匹配已經有序號的檔名（如 "file(1).txt"）
     */
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^(.+)\\((\\d+)\\)(\\.[^.]+)?$");
    
    /**
     * 生成唯一的檔案名稱
     * 
     * @param originalName 原始檔案名稱
     * @param existingNames 同一資料夾下已存在的檔案名稱集合
     * @return 唯一的檔案名稱
     */
    public String generateUniqueName(String originalName, Set<String> existingNames) {
        if (!existingNames.contains(originalName)) {
            return originalName;
        }
        
        // 分離檔名和副檔名
        String baseName;
        String extension = "";
        int lastDotIndex = originalName.lastIndexOf('.');
        
        if (lastDotIndex > 0 && lastDotIndex < originalName.length() - 1) {
            baseName = originalName.substring(0, lastDotIndex);
            extension = originalName.substring(lastDotIndex);
        } else {
            baseName = originalName;
        }
        
        // 找出最大的序號
        int maxNumber = 0;
        String basePattern = Pattern.quote(baseName) + "\\((\\d+)\\)" + Pattern.quote(extension);
        Pattern pattern = Pattern.compile(basePattern);
        
        for (String existingName : existingNames) {
            Matcher matcher = pattern.matcher(existingName);
            if (matcher.matches()) {
                int number = Integer.parseInt(matcher.group(1));
                maxNumber = Math.max(maxNumber, number);
            }
        }
        
        // 生成新的唯一名稱
        return String.format("%s(%d)%s", baseName, maxNumber + 1, extension);
    }
    
    /**
     * 生成唯一名稱（使用名稱計數器）
     * 
     * @param originalName 原始檔案名稱
     * @param nameCounter 名稱計數器，記錄每個基礎名稱的使用次數
     * @return 唯一的檔案名稱
     */
    public String generateUniqueName(String originalName, Map<String, Integer> nameCounter) {
        Integer count = nameCounter.get(originalName);
        
        if (count == null) {
            nameCounter.put(originalName, 1);
            return originalName;
        }
        
        // 分離檔名和副檔名
        String baseName;
        String extension = "";
        int lastDotIndex = originalName.lastIndexOf('.');
        
        if (lastDotIndex > 0 && lastDotIndex < originalName.length() - 1) {
            baseName = originalName.substring(0, lastDotIndex);
            extension = originalName.substring(lastDotIndex);
        } else {
            baseName = originalName;
        }
        
        // 生成唯一名稱
        String uniqueName = String.format("%s(%d)%s", baseName, count, extension);
        nameCounter.put(originalName, count + 1);
        
        return uniqueName;
    }
    
    /**
     * 從帶序號的檔名中提取原始檔名
     * 
     * @param numberedName 帶序號的檔名（如 "file(1).txt"）
     * @return 原始檔名（如 "file.txt"），如果不是帶序號的檔名則返回原值
     */
    public String extractOriginalName(String numberedName) {
        Matcher matcher = NUMBERED_PATTERN.matcher(numberedName);
        if (matcher.matches()) {
            String baseName = matcher.group(1);
            String extension = matcher.group(3);
            return baseName + (extension != null ? extension : "");
        }
        return numberedName;
    }
    
    /**
     * 檢查是否為帶序號的檔名
     * 
     * @param fileName 檔案名稱
     * @return 是否為帶序號的檔名
     */
    public boolean isNumberedName(String fileName) {
        return NUMBERED_PATTERN.matcher(fileName).matches();
    }
    
    /**
     * 獲取檔名的序號
     * 
     * @param numberedName 帶序號的檔名
     * @return 序號，如果不是帶序號的檔名則返回 0
     */
    public int getFileNumber(String numberedName) {
        Matcher matcher = NUMBERED_PATTERN.matcher(numberedName);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(2));
        }
        return 0;
    }
}