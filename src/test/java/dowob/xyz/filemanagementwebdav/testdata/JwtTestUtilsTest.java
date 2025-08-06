package dowob.xyz.filemanagementwebdav.testdata;

import dowob.xyz.filemanagementwebdav.component.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtTestUtils 測試類
 * 
 * 驗證 JWT 測試工具類的各種功能是否正常工作。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName JwtTestUtilsTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@DisplayName("JwtTestUtils 測試")
class JwtTestUtilsTest {
    
    @Test
    @DisplayName("測試創建有效的 JWT Token")
    void testCreateValidToken() {
        // When
        String token = JwtTestUtils.createValidToken();
        
        // Then
        assertThat(token).isNotNull();
        assertThat(JwtTestUtils.isValidTokenFormat(token)).isTrue();
        assertThat(JwtTestUtils.extractUsername(token)).isEqualTo(JwtTestUtils.DEFAULT_TEST_USERNAME);
        assertThat(JwtTestUtils.extractUserId(token)).isEqualTo(JwtTestUtils.DEFAULT_TEST_USER_ID);
        assertThat(JwtTestUtils.isTokenExpired(token)).isFalse();
    }
    
    @Test
    @DisplayName("測試創建過期的 JWT Token")
    void testCreateExpiredToken() {
        // When
        String token = JwtTestUtils.createExpiredToken();
        
        // Then
        assertThat(token).isNotNull();
        assertThat(JwtTestUtils.isValidTokenFormat(token)).isTrue();
        assertThat(JwtTestUtils.isTokenExpired(token)).isTrue();
        assertThat(JwtTestUtils.getTokenRemainingSeconds(token)).isEqualTo(0);
    }
    
    @Test
    @DisplayName("測試創建自定義用戶的 Token")
    void testCreateCustomToken() {
        // Given
        String customUserId = "custom-user-123";
        String customUsername = "customuser";
        List<String> customRoles = Arrays.asList("ADMIN", "MODERATOR");
        
        // When
        String token = JwtTestUtils.createToken(customUserId, customUsername, customRoles, 7200);
        
        // Then
        assertThat(JwtTestUtils.extractUserId(token)).isEqualTo(customUserId);
        assertThat(JwtTestUtils.extractUsername(token)).isEqualTo(customUsername);
        assertThat(JwtTestUtils.extractRoles(token)).containsExactlyElementsOf(customRoles);
        assertThat(JwtTestUtils.getTokenRemainingSeconds(token)).isGreaterThan(7000);
    }
    
    @Test
    @DisplayName("測試 JWT 驗證功能")
    void testTokenValidation() {
        // Given
        String validToken = JwtTestUtils.createValidToken();
        String expiredToken = JwtTestUtils.createExpiredToken();
        String wrongSignatureToken = JwtTestUtils.createWrongSignatureToken();
        
        // When & Then - Valid token
        JwtService.JwtValidationResult validResult = JwtTestUtils.validateToken(validToken);
        JwtTestUtils.assertValidResult(validResult);
        JwtTestUtils.assertUserInfo(validResult, 
            JwtTestUtils.DEFAULT_TEST_USER_ID, 
            JwtTestUtils.DEFAULT_TEST_USERNAME, 
            JwtTestUtils.DEFAULT_TEST_ROLES);
        
        // When & Then - Expired token
        JwtService.JwtValidationResult expiredResult = JwtTestUtils.validateToken(expiredToken);
        JwtTestUtils.assertInvalidResult(expiredResult);
        JwtTestUtils.assertErrorMessageContains(expiredResult, "expired");
        
        // When & Then - Wrong signature
        JwtService.JwtValidationResult wrongSigResult = JwtTestUtils.validateToken(wrongSignatureToken);
        JwtTestUtils.assertInvalidResult(wrongSigResult);
        JwtTestUtils.assertErrorMessageContains(wrongSigResult, "verification failed");
    }
    
    @Test
    @DisplayName("測試缺少聲明的 Token")
    void testTokenMissingClaims() {
        // Given
        String tokenMissingUsername = JwtTestUtils.createTokenMissingClaims(true, false, false);
        String tokenMissingUserId = JwtTestUtils.createTokenMissingClaims(false, true, false);
        
        // When & Then
        JwtService.JwtValidationResult missingUsernameResult = JwtTestUtils.validateToken(tokenMissingUsername);
        JwtTestUtils.assertInvalidResult(missingUsernameResult);
        JwtTestUtils.assertErrorMessageContains(missingUsernameResult, "missing required claims");
        
        JwtService.JwtValidationResult missingUserIdResult = JwtTestUtils.validateToken(tokenMissingUserId);
        JwtTestUtils.assertInvalidResult(missingUserIdResult);
        JwtTestUtils.assertErrorMessageContains(missingUserIdResult, "missing required claims");
    }
    
    @Test
    @DisplayName("測試自定義聲明的 Token")
    void testTokenWithCustomClaim() {
        // Given
        String customKey = "department";
        String customValue = "Engineering";
        String token = JwtTestUtils.createTokenWithCustomClaim(customKey, customValue);
        
        // When
        var decodedJWT = JwtTestUtils.decodeTokenWithoutVerification(token);
        
        // Then
        assertThat(decodedJWT.getClaim(customKey).asString()).isEqualTo(customValue);
        assertThat(JwtTestUtils.extractUsername(token)).isEqualTo(JwtTestUtils.DEFAULT_TEST_USERNAME);
    }
    
    @Test
    @DisplayName("測試批量生成 Token")
    void testBatchTokenGeneration() {
        // When
        List<String> multipleTokens = JwtTestUtils.createMultipleUserTokens(5);
        List<String> differentExpiryTokens = JwtTestUtils.createTokensWithDifferentExpiry();
        List<String> differentRolesTokens = JwtTestUtils.createTokensWithDifferentRoles();
        
        // Then
        assertThat(multipleTokens).hasSize(5);
        assertThat(differentExpiryTokens).hasSize(4);
        assertThat(differentRolesTokens).hasSize(4);
        
        // 驗證所有 tokens 都是有效格式
        multipleTokens.forEach(token -> 
            assertThat(JwtTestUtils.isValidTokenFormat(token)).isTrue());
        differentExpiryTokens.forEach(token -> 
            assertThat(JwtTestUtils.isValidTokenFormat(token)).isTrue());
        differentRolesTokens.forEach(token -> 
            assertThat(JwtTestUtils.isValidTokenFormat(token)).isTrue());
    }
    
    @Test
    @DisplayName("測試無過期時間的 Token")
    void testTokenWithoutExpiry() {
        // When
        String token = JwtTestUtils.createTokenWithoutExpiry();
        
        // Then
        assertThat(JwtTestUtils.isValidTokenFormat(token)).isTrue();
        assertThat(JwtTestUtils.isTokenExpired(token)).isFalse();
        assertThat(JwtTestUtils.getTokenRemainingSeconds(token)).isEqualTo(Long.MAX_VALUE);
    }
    
    @Test
    @DisplayName("測試創建測試用 JwtService")
    void testCreateTestJwtService() {
        // When
        JwtService jwtService = JwtTestUtils.createTestJwtService();
        String token = JwtTestUtils.createValidToken();
        
        // Then
        assertThat(jwtService).isNotNull();
        JwtService.JwtValidationResult result = jwtService.validateToken(token);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getUsername()).isEqualTo(JwtTestUtils.DEFAULT_TEST_USERNAME);
    }
    
    @Test
    @DisplayName("測試 Token 格式檢查")
    void testTokenFormatValidation() {
        // Given
        String validToken = JwtTestUtils.createValidToken();
        String invalidToken = "not.a.jwt.token.with.five.parts";  // 5 parts instead of 3
        String twoPartsToken = "header.payload";  // Only 2 parts
        String emptyToken = "";
        String nullToken = null;
        
        // Then
        assertThat(JwtTestUtils.isValidTokenFormat(validToken)).isTrue();
        assertThat(JwtTestUtils.isValidTokenFormat(invalidToken)).isFalse();
        assertThat(JwtTestUtils.isValidTokenFormat(twoPartsToken)).isFalse();
        assertThat(JwtTestUtils.isValidTokenFormat(emptyToken)).isFalse();
        assertThat(JwtTestUtils.isValidTokenFormat(nullToken)).isFalse();
    }
    
    @Test
    @DisplayName("測試性能測量方法")
    void testPerformanceMeasurement() {
        // Given
        String token = JwtTestUtils.createValidToken();
        
        // When
        long validationTime = JwtTestUtils.measureValidationTime(token, 100);
        long creationTime = JwtTestUtils.measureTokenCreationTime(100);
        
        // Then
        assertThat(validationTime).isGreaterThan(0);
        assertThat(creationTime).isGreaterThan(0);
        // 性能測試結果應該是合理的時間範圍（通常不超過幾秒）
        assertThat(validationTime).isLessThan(10000); // 10秒
        assertThat(creationTime).isLessThan(10000); // 10秒
    }
}