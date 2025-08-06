package dowob.xyz.filemanagementwebdav.utils;

import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 統一日誌工具類
 * 
 * 自動整合請求上下文信息，提供結構化的日誌輸出
 * 參考主服務的 LogUnity 實現
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName LogUtils
 * @create 2025/8/5
 * @Version 1.0
 **/
@Slf4j
public class LogUtils {
    
    /**
     * 記錄調試信息
     * 
     * @param logger 日誌記錄器
     * @param message 日誌消息
     * @param args 消息參數
     */
    public static void debug(Logger logger, String message, Object... args) {
        if (logger.isDebugEnabled()) {
            String formattedMessage = formatMessage(message);
            logger.debug(formattedMessage, args);
        }
    }
    
    /**
     * 記錄信息
     * 
     * @param logger 日誌記錄器
     * @param message 日誌消息
     * @param args 消息參數
     */
    public static void info(Logger logger, String message, Object... args) {
        if (logger.isInfoEnabled()) {
            String formattedMessage = formatMessage(message);
            logger.info(formattedMessage, args);
        }
    }
    
    /**
     * 記錄警告
     * 
     * @param logger 日誌記錄器
     * @param message 日誌消息
     * @param args 消息參數
     */
    public static void warn(Logger logger, String message, Object... args) {
        String formattedMessage = formatMessage(message);
        logger.warn(formattedMessage, args);
    }
    
    /**
     * 記錄錯誤
     * 
     * @param logger 日誌記錄器
     * @param message 日誌消息
     * @param throwable 異常對象
     * @param args 消息參數
     */
    public static void error(Logger logger, String message, Throwable throwable, Object... args) {
        String formattedMessage = formatMessage(message);
        if (throwable != null) {
            logger.error(formattedMessage, args, throwable);
        } else {
            logger.error(formattedMessage, args);
        }
    }
    
    /**
     * 記錄錯誤（無異常對象）
     * 
     * @param logger 日誌記錄器
     * @param message 日誌消息
     * @param args 消息參數
     */
    public static void error(Logger logger, String message, Object... args) {
        error(logger, message, null, args);
    }
    
    /**
     * 記錄認證相關日誌
     * 
     * @param operation 操作類型
     * @param username 用戶名
     * @param success 是否成功
     * @param details 詳細信息
     */
    public static void logAuthentication(String operation, String username, boolean success, String details) {
        Logger authLogger = LoggerFactory.getLogger("WebDAV.Auth");
        
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        String logMessage = formatMessage("Auth {} - User: {}, Success: {}, Details: {}");
        
        if (success) {
            authLogger.info(logMessage, operation, username, success, details);
        } else {
            authLogger.warn(logMessage, operation, username, success, details);
        }
    }
    
    /**
     * 記錄檔案操作日誌
     * 
     * @param operation 操作類型（GET, PUT, DELETE 等）
     * @param path 檔案路徑
     * @param success 是否成功
     * @param size 檔案大小（可選）
     */
    public static void logFileOperation(String operation, String path, boolean success, Long size) {
        Logger fileLogger = LoggerFactory.getLogger("WebDAV.File");
        
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        String username = context != null && context.isAuthenticated() ? context.getUsername() : "anonymous";
        
        String sizeInfo = size != null ? String.format(" (%d bytes)", size) : "";
        String logMessage = formatMessage("{} {} - Path: {}{}, User: {}");
        
        if (success) {
            fileLogger.info(logMessage, operation, success ? "SUCCESS" : "FAILED", path, sizeInfo, username);
        } else {
            fileLogger.warn(logMessage, operation, success ? "SUCCESS" : "FAILED", path, sizeInfo, username);
        }
    }
    
    /**
     * 記錄安全相關日誌
     * 
     * @param event 安全事件
     * @param details 事件詳情
     * @param level 日誌級別（INFO, WARN, ERROR）
     */
    public static void logSecurity(String event, String details, String level) {
        Logger secLogger = LoggerFactory.getLogger("WebDAV.Security");
        
        String logMessage = formatMessage("Security Event: {} - {}");
        
        switch (level.toUpperCase()) {
            case "ERROR" -> secLogger.error(logMessage, event, details);
            case "WARN" -> secLogger.warn(logMessage, event, details);
            default -> secLogger.info(logMessage, event, details);
        }
    }
    
    /**
     * 記錄性能相關日誌
     * 
     * @param operation 操作名稱
     * @param duration 耗時（毫秒）
     * @param details 詳細信息
     */
    public static void logPerformance(String operation, long duration, String details) {
        Logger perfLogger = LoggerFactory.getLogger("WebDAV.Performance");
        
        String logMessage = formatMessage("Performance - {}: {}ms - {}");
        
        if (duration > 1000) {
            perfLogger.warn(logMessage, operation, duration, details);
        } else {
            perfLogger.info(logMessage, operation, duration, details);
        }
    }
    
    /**
     * 格式化日誌消息，添加請求上下文信息
     * 
     * @param message 原始消息
     * @return 格式化後的消息
     */
    private static String formatMessage(String message) {
        RequestContextHolder.RequestContext context = RequestContextHolder.getContext();
        
        if (context == null) {
            return message;
        }
        
        // 構建上下文前綴
        StringBuilder prefix = new StringBuilder();
        prefix.append(context.getLogIdentifier()).append(" ");
        
        return prefix + message;
    }
    
    /**
     * 簡化的日誌方法，使用默認的 logger
     */
    public static class WebDAV {
        private static final Logger logger = LoggerFactory.getLogger("WebDAV");
        
        public static void debug(String message, Object... args) {
            LogUtils.debug(logger, message, args);
        }
        
        public static void info(String message, Object... args) {
            LogUtils.info(logger, message, args);
        }
        
        public static void warn(String message, Object... args) {
            LogUtils.warn(logger, message, args);
        }
        
        public static void error(String message, Object... args) {
            LogUtils.error(logger, message, args);
        }
        
        public static void error(String message, Throwable throwable, Object... args) {
            LogUtils.error(logger, message, throwable, args);
        }
    }
}