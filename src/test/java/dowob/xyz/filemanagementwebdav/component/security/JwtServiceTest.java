package dowob.xyz.filemanagementwebdav.component.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtService 單元測試
 * 
 * 測試 JWT 服務的 token 驗證、解析和格式檢查功能。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName JwtServiceTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService 測試")
class JwtServiceTest {
    
    private JwtService jwtService;
    private Algorithm algorithm;
    
    private static final String TEST_SECRET = "test-secret-key-for-jwt-testing";
    private static final String TEST_ISSUER = "test-issuer";
    private static final String TEST_USER_ID = "12345";
    private static final String TEST_USERNAME = "testuser";
    private static final List<String> TEST_ROLES = Arrays.asList("USER", "ADVANCED_USER");
    
    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, TEST_ISSUER);
        algorithm = Algorithm.HMAC256(TEST_SECRET);
    }
    
    // ===== JWT 格式檢查測試 =====
    
    @Test
    @DisplayName("測試有效的 JWT 格式")
    void testIsJwtFormatValid() {
        // Given
        String validJwt = createValidJwtToken();
        
        // When
        boolean result = jwtService.isJwtFormat(validJwt);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("測試無效的 JWT 格式")
    void testIsJwtFormatInvalid() {
        // Given
        String invalidJwt = "not.a.valid.jwt.token";
        
        // When
        boolean result = jwtService.isJwtFormat(invalidJwt);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("測試 null JWT")
    void testIsJwtFormatNull() {
        // When
        boolean result = jwtService.isJwtFormat(null);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("測試空字符串 JWT")
    void testIsJwtFormatEmpty() {
        // When
        boolean result = jwtService.isJwtFormat("");
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("測試只有兩部分的字符串")
    void testIsJwtFormatTwoParts() {
        // Given
        String twoParts = "header.payload";
        
        // When
        boolean result = jwtService.isJwtFormat(twoParts);
        
        // Then
        assertThat(result).isFalse();
    }
    
    // ===== JWT 驗證測試 =====
    
    @Test
    @DisplayName("測試有效 JWT 驗證成功")
    void testValidateTokenSuccess() {
        // Given
        String validJwt = createValidJwtToken();
        
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(validJwt);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(result.getRoles()).containsExactlyElementsOf(TEST_ROLES);
        assertThat(result.getErrorMessage()).isNull();
    }
    
    @Test
    @DisplayName("測試過期的 JWT")
    void testValidateTokenExpired() {
        // Given
        String expiredJwt = createExpiredJwtToken();
        
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(expiredJwt);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("has expired");
    }
    
    @Test
    @DisplayName("測試錯誤簽名的 JWT")
    void testValidateTokenWrongSignature() {
        // Given
        Algorithm wrongAlgorithm = Algorithm.HMAC256("wrong-secret");
        String wrongSignatureJwt = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_USER_ID)
                .withClaim("username", TEST_USERNAME)
                .withClaim("roles", TEST_ROLES)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(wrongAlgorithm);
        
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(wrongSignatureJwt);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("verification failed");
    }
    
    @Test
    @DisplayName("測試錯誤發行者的 JWT")
    void testValidateTokenWrongIssuer() {
        // Given
        String wrongIssuerJwt = JWT.create()
                .withIssuer("wrong-issuer")
                .withSubject(TEST_USER_ID)
                .withClaim("username", TEST_USERNAME)
                .withClaim("roles", TEST_ROLES)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);
        
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(wrongIssuerJwt);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("verification failed");
    }
    
    @Test
    @DisplayName("測試缺少必要聲明的 JWT")
    void testValidateTokenMissingClaims() {
        // Given - 沒有 username 聲明
        String missingClaimsJwt = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_USER_ID)
                .withClaim("roles", TEST_ROLES)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);
        
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(missingClaimsJwt);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("missing required claims");
    }
    
    @Test
    @DisplayName("測試缺少用戶 ID 的 JWT")
    void testValidateTokenMissingUserId() {
        // Given - 沒有 subject (用戶ID)
        String missingUserIdJwt = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withClaim("username", TEST_USERNAME)
                .withClaim("roles", TEST_ROLES)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(algorithm);
        
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(missingUserIdJwt);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("missing required claims");
    }
    
    @Test
    @DisplayName("測試 null token 驗證")
    void testValidateTokenNull() {
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(null);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("null or empty");
    }
    
    @Test
    @DisplayName("測試空 token 驗證")
    void testValidateTokenEmpty() {
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken("");
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("null or empty");
    }
    
    @Test
    @DisplayName("測試格式錯誤的 token")
    void testValidateTokenMalformed() {
        // Given
        String malformedToken = "this.is.not.a.jwt";
        
        // When
        JwtService.JwtValidationResult result = jwtService.validateToken(malformedToken);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("verification failed");
    }
    
    // ===== 用戶名提取測試 =====
    
    @Test
    @DisplayName("測試從有效 JWT 提取用戶名")
    void testExtractUsernameSuccess() {
        // Given
        String validJwt = createValidJwtToken();
        
        // When
        String username = jwtService.extractUsernameWithoutVerification(validJwt);
        
        // Then
        assertThat(username).isEqualTo(TEST_USERNAME);
    }
    
    @Test
    @DisplayName("測試從無效 JWT 提取用戶名")
    void testExtractUsernameFromInvalidToken() {
        // Given
        String invalidJwt = "invalid.jwt.token";
        
        // When
        String username = jwtService.extractUsernameWithoutVerification(invalidJwt);
        
        // Then
        assertThat(username).isNull();
    }
    
    @Test
    @DisplayName("測試從 null token 提取用戶名")
    void testExtractUsernameFromNullToken() {
        // When
        String username = jwtService.extractUsernameWithoutVerification(null);
        
        // Then
        assertThat(username).isNull();
    }
    
    // ===== JwtValidationResult 測試 =====
    
    @Test
    @DisplayName("測試 JwtValidationResult.valid() 創建")
    void testJwtValidationResultValid() {
        // When
        JwtService.JwtValidationResult result = JwtService.JwtValidationResult.valid(
            TEST_USER_ID, TEST_USERNAME, TEST_ROLES, null);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(result.getRoles()).containsExactlyElementsOf(TEST_ROLES);
        assertThat(result.getErrorMessage()).isNull();
    }
    
    @Test
    @DisplayName("測試 JwtValidationResult.invalid() 創建")
    void testJwtValidationResultInvalid() {
        // Given
        String errorMessage = "Test error message";
        
        // When
        JwtService.JwtValidationResult result = JwtService.JwtValidationResult.invalid(errorMessage);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(result.getUserId()).isNull();
        assertThat(result.getUsername()).isNull();
        assertThat(result.getRoles()).isNull();
    }
    
    @Test
    @DisplayName("測試 hasRole 方法")
    void testJwtValidationResultHasRole() {
        // Given
        JwtService.JwtValidationResult result = JwtService.JwtValidationResult.valid(
            TEST_USER_ID, TEST_USERNAME, TEST_ROLES, null);
        
        // Then
        assertThat(result.hasRole("USER")).isTrue();
        assertThat(result.hasRole("ADVANCED_USER")).isTrue();
        assertThat(result.hasRole("ADMIN")).isFalse();
    }
    
    @Test
    @DisplayName("測試 hasRole 方法對 null roles")
    void testJwtValidationResultHasRoleWithNullRoles() {
        // Given
        JwtService.JwtValidationResult result = JwtService.JwtValidationResult.valid(
            TEST_USER_ID, TEST_USERNAME, null, null);
        
        // Then
        assertThat(result.hasRole("USER")).isFalse();
    }
    
    // ===== 輔助方法 =====
    
    /**
     * 創建有效的 JWT token
     */
    private String createValidJwtToken() {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_USER_ID)
                .withClaim("username", TEST_USERNAME)
                .withClaim("roles", TEST_ROLES)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .withIssuedAt(Date.from(Instant.now()))
                .sign(algorithm);
    }
    
    /**
     * 創建過期的 JWT token
     */
    private String createExpiredJwtToken() {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_USER_ID)
                .withClaim("username", TEST_USERNAME)
                .withClaim("roles", TEST_ROLES)
                .withExpiresAt(Date.from(Instant.now().minusSeconds(3600))) // 已過期
                .withIssuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .sign(algorithm);
    }
}