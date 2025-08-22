package dowob.xyz.filemanagementwebdav.component.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * JWT 服務
 * 
 * 負責 JWT token 的驗證、解析和處理。
 * 支持將 JWT 作為 WebDAV 密碼進行身份驗證。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName JwtService
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Service
public class JwtService {
    
    private final String jwtSecret;
    private final String jwtIssuer;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    
    public JwtService(
            @Value("${webdav.security.jwt.secret:default-secret-key-change-in-production}") String jwtSecret,
            @Value("${webdav.security.jwt.issuer:FileManagement-System}") String jwtIssuer) {
        
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
        this.algorithm = Algorithm.HMAC256(jwtSecret);
        this.verifier = JWT.require(algorithm)
                .withIssuer(jwtIssuer)
                .build();
        
        log.info("JwtService initialized with issuer: {}", jwtIssuer);
    }
    
    /**
     * 驗證 JWT token
     * 
     * @param token JWT token 字符串
     * @return JwtValidationResult 包含驗證結果和用戶信息
     */
    public JwtValidationResult validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return JwtValidationResult.invalid("Token is null or empty");
        }
        
        try {
            // 驗證 token
            DecodedJWT decodedJWT = verifier.verify(token);
            
            // 檢查是否已過期
            Date expiresAt = decodedJWT.getExpiresAt();
            if (expiresAt != null && expiresAt.before(new Date())) {
                return JwtValidationResult.invalid("Token has expired");
            }
            
            // 提取用戶信息
            String userId = decodedJWT.getSubject();
            String username = decodedJWT.getClaim("username").asString();
            String role = decodedJWT.getClaim("role").asString();
            
            // 檢查必要字段
            if (userId == null || username == null) {
                return JwtValidationResult.invalid("Token missing required claims");
            }
            
            log.debug("JWT validation successful for user: {}", username);
            
            return JwtValidationResult.valid(userId, username, role, decodedJWT);
            
        } catch (JWTVerificationException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return JwtValidationResult.invalid("Token verification failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation", e);
            return JwtValidationResult.invalid("Internal error during token validation");
        }
    }
    
    /**
     * 檢查 token 是否為有效的 JWT 格式
     * 
     * @param token 待檢查的 token
     * @return true 如果是有效的 JWT 格式
     */
    public boolean isJwtFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 只解碼，不驗證簽名
            JWT.decode(token);
            return true;
            
        } catch (JWTDecodeException e) {
            log.debug("Token is not in JWT format: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 從 token 中提取用戶名（不驗證簽名）
     * 
     * @param token JWT token
     * @return 用戶名，如果提取失敗返回 null
     */
    public String extractUsernameWithoutVerification(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            return decodedJWT.getClaim("username").asString();
        } catch (Exception e) {
            log.debug("Failed to extract username from token", e);
            return null;
        }
    }
    
    /**
     * JWT 驗證結果
     */
    public static class JwtValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String userId;
        private final String username;
        private final String role;
        private final DecodedJWT decodedJWT;
        
        private JwtValidationResult(boolean valid, String errorMessage, String userId, 
                                  String username, String role, DecodedJWT decodedJWT) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.decodedJWT = decodedJWT;
        }
        
        public static JwtValidationResult valid(String userId, String username, 
                                              String role, DecodedJWT decodedJWT) {
            return new JwtValidationResult(true, null, userId, username, role, decodedJWT);
        }
        
        public static JwtValidationResult invalid(String errorMessage) {
            return new JwtValidationResult(false, errorMessage, null, null, null, null);
        }
        
        // Getters
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getRole() {
            return role;
        }
        
        public DecodedJWT getDecodedJWT() {
            return decodedJWT;
        }
        
        /**
         * 檢查用戶是否有指定角色
         */
        public boolean hasRole(String expectedRole) {
            return role != null && role.equals(expectedRole);
        }
        
        /**
         * 獲取 token 的過期時間
         */
        public Date getExpiresAt() {
            return decodedJWT != null ? decodedJWT.getExpiresAt() : null;
        }
        
        /**
         * 獲取 token 的簽發時間
         */
        public Date getIssuedAt() {
            return decodedJWT != null ? decodedJWT.getIssuedAt() : null;
        }
    }
}