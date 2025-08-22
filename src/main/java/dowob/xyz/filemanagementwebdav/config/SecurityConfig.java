package dowob.xyz.filemanagementwebdav.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置類
 * 
 * 配置 WebDAV 服務的安全策略，允許 Milton 框架處理 /dav/** 路徑的認證。
 * 禁用 CSRF 和 Session 以適應 WebDAV 協議要求。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName SecurityConfig
 * @create 2025/6/9
 * @Version 1.0
 **/
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（WebDAV 需要）
            .csrf(AbstractHttpConfigurer::disable)
            
            // 配置授權規則
            .authorizeHttpRequests(auth -> auth
                // 允許 /dav/** 路徑（讓 Milton 處理認證）
                .requestMatchers("/dav/**").permitAll()
                
                // 允許健康檢查端點
                .requestMatchers("/actuator/**").permitAll()
                
                // 允許管理端點（可選）
                .requestMatchers("/api/**").authenticated()
                
                // 其他請求需要認證
                .anyRequest().authenticated()
            )
            
            // 禁用 Session（使用無狀態認證）
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 禁用表單登錄（使用 Basic Auth）
            .formLogin(AbstractHttpConfigurer::disable)
            
            // 禁用登出頁面
            .logout(AbstractHttpConfigurer::disable);
        
        return http.build();
    }
}
