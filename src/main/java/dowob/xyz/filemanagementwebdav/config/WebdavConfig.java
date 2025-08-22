package dowob.xyz.filemanagementwebdav.config;

import dowob.xyz.filemanagementwebdav.component.factory.WebDavResourceFactory;
import dowob.xyz.filemanagementwebdav.component.filter.MiltonAuthenticationFilter;
import dowob.xyz.filemanagementwebdav.component.security.WebDavSecurityManager;
import dowob.xyz.filemanagementwebdav.config.properties.WebDavSecurityProperties;
import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * WebDAV 配置類
 * 
 * 配置 Milton WebDAV 框架的 HttpManager，整合自定義的資源工廠和安全管理器。
 * 從配置文件讀取 realm 設定，支援基本身份驗證。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebdavConfig
 * @create 2025/6/9
 * @Version 1.0
 **/
@Configuration
@RequiredArgsConstructor
public class WebdavConfig {
    
    private final WebDavSecurityProperties securityProperties;

    @Bean
    public HttpManager httpManager(WebDavResourceFactory webDavResourceFactory, 
                                   WebDavSecurityManager securityManager,
                                   MiltonAuthenticationFilter miltonAuthFilter) {

        HttpManagerBuilder builder = new HttpManagerBuilder();
        builder.setResourceFactory(webDavResourceFactory);
        builder.setSecurityManager(securityManager);
        builder.setEnableBasicAuth(true);
        
        // 從配置文件讀取 realm
        String realm = securityProperties.getRealm();
        builder.setFsRealm(realm);
        
        // 添加自定義過濾器來捕獲認證信息
        List<io.milton.http.Filter> filters = new ArrayList<>();
        filters.add(miltonAuthFilter);
        builder.setFilters(filters);
        
        return builder.buildHttpManager();
    }
}
