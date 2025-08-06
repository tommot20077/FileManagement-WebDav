package dowob.xyz.filemanagementwebdav.config;

import dowob.xyz.filemanagementwebdav.component.factory.WebDavResourceFactory;
import dowob.xyz.filemanagementwebdav.component.security.WebDavSecurityManager;
import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebdavConfig
 * @create 2025/6/9
 * @Version 1.0
 **/

@Configuration
public class WebdavConfig {

    @Bean
    public HttpManager httpManager(WebDavResourceFactory webDavResourceFactory, 
                                   WebDavSecurityManager securityManager) {

        HttpManagerBuilder builder = new HttpManagerBuilder();
        builder.setResourceFactory(webDavResourceFactory);
        builder.setSecurityManager(securityManager);
        builder.setEnableBasicAuth(true);
        builder.setFsRealm("FileManagement WebDAV");
        return builder.buildHttpManager();
    }
}
