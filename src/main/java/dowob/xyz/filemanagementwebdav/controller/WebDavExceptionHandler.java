package dowob.xyz.filemanagementwebdav.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavExceptionHandler
 * @create 2025/6/9
 * @Version 1.0
 **/

@RestControllerAdvice
public class WebDavExceptionHandler {
    Logger log = LoggerFactory.getLogger(WebDavExceptionHandler.class);


    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        log.error("無法處理請求: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("內部服務器錯誤");
    }
}