package dowob.xyz.filemanagementwebdav.controller;

import dowob.xyz.filemanagementwebdav.component.service.WebDavToggleService;
import io.milton.http.HttpManager;
import io.milton.servlet.ServletRequest;
import io.milton.servlet.ServletResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavController
 * @create 2025/6/9
 * @Version 1.0
 **/
@Slf4j
@Controller
public class WebDavController {

    private final HttpManager httpManager;
    private final WebDavToggleService toggleService;

    public WebDavController(HttpManager httpManager, WebDavToggleService toggleService) {
        this.httpManager = httpManager;
        this.toggleService = toggleService;
    }


    @RequestMapping(value = "/webdav/**")
    public void handleWebDav(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 檢查 WebDAV 服務是否啟用
        if (!toggleService.isServiceAvailable()) {
            log.debug("WebDAV request rejected - service is disabled: {} {}", 
                     request.getMethod(), request.getRequestURI());
            
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write(toggleService.getServiceUnavailableMessage());
            response.getWriter().flush();
            return;
        }
        
        try {
            ServletRequest miltonRequest = new ServletRequest(request, request.getServletContext());
            ServletResponse miltonResponse = new ServletResponse(response);

            httpManager.process(miltonRequest, miltonResponse);
        } catch (Exception e) {
            log.error("WebDAV processing failed for {} {}", request.getMethod(), request.getRequestURI(), e);
            throw new ServletException("WebDAV 處理失敗", e);
        }
    }
}
