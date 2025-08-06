package dowob.xyz.filemanagementwebdav.testdata;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import dowob.xyz.filemanagementwebdav.component.security.JwtService;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * JWT 測試工具類
 * 
 * 提供 JWT 相關的測試輔助方法，包括 token 生成、驗證、解析等功能。
 * 這個工具類簡化了測試中對 JWT 的操作，避免重複編寫相同的 JWT 處理邏輯。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName JwtTestUtils
 * @create 2025/8/5
 * @Version 1.0
 **/
public class JwtTestUtils {
    
    // ===== 常用測試配置 =====
    
    public static final String DEFAULT_TEST_SECRET = "test-jwt-secret-key-for-unit-testing-very-secure";
    public static final String DEFAULT_TEST_ISSUER = "FileManagement-Test-System";
    public static final String DEFAULT_TEST_USER_ID = "test-user-12345";
    public static final String DEFAULT_TEST_USERNAME = "testuser";
    public static final List<String> DEFAULT_TEST_ROLES = Arrays.asList("USER", "ADVANCED_USER");
    public static final long DEFAULT_EXPIRY_SECONDS = 3600L; // 1 hour
    
    private static final Algorithm DEFAULT_ALGORITHM = Algorithm.HMAC256(DEFAULT_TEST_SECRET);
    
    // ===== JWT Token 生成方法 =====
    
    /**
     * 創建標準的測試 JWT Token
     */
    public static String createValidToken() {
        return createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, DEFAULT_TEST_ROLES, DEFAULT_EXPIRY_SECONDS);
    }
    
    /**
     * 創建自定義的 JWT Token
     */
    public static String createToken(String userId, String username, List<String> roles, long expirySeconds) {
        return createTokenWithAlgorithm(userId, username, roles, expirySeconds, DEFAULT_ALGORITHM);
    }
    
    /**
     * 創建指定算法的 JWT Token
     */
    public static String createTokenWithAlgorithm(String userId, String username, List<String> roles, 
                                                 long expirySeconds, Algorithm algorithm) {
        var builder = JWT.create()
                .withIssuer(DEFAULT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("username", username)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(expirySeconds)));
        
        if (roles != null && !roles.isEmpty()) {
            builder.withClaim("roles", roles);
        }
        
        return builder.sign(algorithm);
    }
    
    /**
     * 創建過期的 JWT Token
     */
    public static String createExpiredToken() {
        return createExpiredToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, DEFAULT_TEST_ROLES);
    }
    
    /**
     * 創建指定用戶的過期 JWT Token
     */
    public static String createExpiredToken(String userId, String username, List<String> roles) {
        return JWT.create()
                .withIssuer(DEFAULT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("roles", roles)
                .withIssuedAt(Date.from(Instant.now().minusSeconds(7200))) // 2 hours ago
                .withExpiresAt(Date.from(Instant.now().minusSeconds(3600))) // 1 hour ago (expired)
                .sign(DEFAULT_ALGORITHM);
    }
    
    /**
     * 創建錯誤簽名的 JWT Token
     */
    public static String createWrongSignatureToken() {
        Algorithm wrongAlgorithm = Algorithm.HMAC256("wrong-secret-key");
        return createTokenWithAlgorithm(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, 
                                      DEFAULT_TEST_ROLES, DEFAULT_EXPIRY_SECONDS, wrongAlgorithm);
    }
    
    /**
     * 創建錯誤發行者的 JWT Token
     */
    public static String createWrongIssuerToken() {
        return JWT.create()
                .withIssuer("wrong-issuer")
                .withSubject(DEFAULT_TEST_USER_ID)
                .withClaim("username", DEFAULT_TEST_USERNAME)
                .withClaim("roles", DEFAULT_TEST_ROLES)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(DEFAULT_EXPIRY_SECONDS)))
                .sign(DEFAULT_ALGORITHM);
    }
    
    /**
     * 創建缺少必要聲明的 JWT Token
     */
    public static String createTokenMissingClaims(boolean missingUsername, boolean missingUserId, boolean missingRoles) {
        var builder = JWT.create()
                .withIssuer(DEFAULT_TEST_ISSUER)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(DEFAULT_EXPIRY_SECONDS)));
        
        if (!missingUserId) {
            builder.withSubject(DEFAULT_TEST_USER_ID);
        }
        if (!missingUsername) {
            builder.withClaim("username", DEFAULT_TEST_USERNAME);
        }
        if (!missingRoles) {
            builder.withClaim("roles", DEFAULT_TEST_ROLES);
        }
        
        return builder.sign(DEFAULT_ALGORITHM);
    }
    
    /**
     * 創建無過期時間的 JWT Token
     */
    public static String createTokenWithoutExpiry() {
        return JWT.create()
                .withIssuer(DEFAULT_TEST_ISSUER)
                .withSubject(DEFAULT_TEST_USER_ID)
                .withClaim("username", DEFAULT_TEST_USERNAME)
                .withClaim("roles", DEFAULT_TEST_ROLES)
                .withIssuedAt(Date.from(Instant.now()))
                // 沒有設置過期時間
                .sign(DEFAULT_ALGORITHM);
    }
    
    /**
     * 創建具有自定義聲明的 JWT Token
     */
    public static String createTokenWithCustomClaim(String claimKey, Object claimValue) {
        return JWT.create()
                .withIssuer(DEFAULT_TEST_ISSUER)
                .withSubject(DEFAULT_TEST_USER_ID)
                .withClaim("username", DEFAULT_TEST_USERNAME)
                .withClaim("roles", DEFAULT_TEST_ROLES)
                .withClaim(claimKey, claimValue.toString())
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(DEFAULT_EXPIRY_SECONDS)))
                .sign(DEFAULT_ALGORITHM);
    }
    
    // ===== JWT Service 測試輔助方法 =====
    
    /**
     * 創建測試用的 JwtService 實例
     */
    public static JwtService createTestJwtService() {
        return new JwtService(DEFAULT_TEST_SECRET, DEFAULT_TEST_ISSUER);
    }
    
    /**
     * 創建指定配置的 JwtService 實例
     */
    public static JwtService createJwtService(String secret, String issuer) {
        return new JwtService(secret, issuer);
    }
    
    /**
     * 使用反射設置 JwtService 的內部字段（用於測試特殊情況）
     */
    public static void setJwtServiceField(JwtService jwtService, String fieldName, Object value) {
        try {
            Field field = JwtService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(jwtService, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
    
    // ===== JWT Token 驗證和解析輔助方法 =====
    
    /**
     * 驗證 token 並返回結果（使用默認配置）
     */
    public static JwtService.JwtValidationResult validateToken(String token) {
        JwtService jwtService = createTestJwtService();
        return jwtService.validateToken(token);
    }
    
    /**
     * 檢查 token 格式是否正確
     */
    public static boolean isValidTokenFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }
    
    /**
     * 解析 token 但不驗證簽名
     */
    public static DecodedJWT decodeTokenWithoutVerification(String token) {
        return JWT.decode(token);
    }
    
    /**
     * 從 token 中提取用戶名（不驗證簽名）
     */
    public static String extractUsername(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            return decodedJWT.getClaim("username").asString();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 從 token 中提取用戶 ID（不驗證簽名）
     */
    public static String extractUserId(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            return decodedJWT.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 從 token 中提取角色列表（不驗證簽名）
     */
    @SuppressWarnings("unchecked")
    public static List<String> extractRoles(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            return decodedJWT.getClaim("roles").asList(String.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 檢查 token 是否已過期（不驗證簽名）
     */
    public static boolean isTokenExpired(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            Date expiresAt = decodedJWT.getExpiresAt();
            return expiresAt != null && expiresAt.before(new Date());
        } catch (Exception e) {
            return true; // 如果無法解析，視為過期
        }
    }
    
    /**
     * 獲取 token 的剩餘有效時間（秒）
     */
    public static long getTokenRemainingSeconds(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            Date expiresAt = decodedJWT.getExpiresAt();
            if (expiresAt == null) {
                return Long.MAX_VALUE; // 永久有效
            }
            
            long remainingMs = expiresAt.getTime() - System.currentTimeMillis();
            return Math.max(0, remainingMs / 1000);
        } catch (Exception e) {
            return 0;
        }
    }
    
    // ===== 測試斷言輔助方法 =====
    
    /**
     * 驗證 JwtValidationResult 是有效的
     */
    public static void assertValidResult(JwtService.JwtValidationResult result) {
        if (!result.isValid()) {
            throw new AssertionError("Expected valid JWT result, but got: " + result.getErrorMessage());
        }
    }
    
    /**
     * 驗證 JwtValidationResult 是無效的
     */
    public static void assertInvalidResult(JwtService.JwtValidationResult result) {
        if (result.isValid()) {
            throw new AssertionError("Expected invalid JWT result, but it was valid");
        }
    }
    
    /**
     * 驗證 JwtValidationResult 包含指定的用戶信息
     */
    public static void assertUserInfo(JwtService.JwtValidationResult result, String expectedUserId, 
                                    String expectedUsername, List<String> expectedRoles) {
        assertValidResult(result);
        
        if (!expectedUserId.equals(result.getUserId())) {
            throw new AssertionError("Expected userId: " + expectedUserId + ", but got: " + result.getUserId());
        }
        
        if (!expectedUsername.equals(result.getUsername())) {
            throw new AssertionError("Expected username: " + expectedUsername + ", but got: " + result.getUsername());
        }
        
        if (expectedRoles != null && !expectedRoles.equals(result.getRoles())) {
            throw new AssertionError("Expected roles: " + expectedRoles + ", but got: " + result.getRoles());
        }
    }
    
    /**
     * 驗證錯誤消息包含指定文本
     */
    public static void assertErrorMessageContains(JwtService.JwtValidationResult result, String expectedText) {
        assertInvalidResult(result);
        
        String errorMessage = result.getErrorMessage();
        if (errorMessage == null || !errorMessage.contains(expectedText)) {
            throw new AssertionError("Expected error message to contain: " + expectedText + 
                                   ", but got: " + errorMessage);
        }
    }
    
    // ===== 性能測試輔助方法 =====
    
    /**
     * 測量 token 驗證的性能
     */
    public static long measureValidationTime(String token, int iterations) {
        JwtService jwtService = createTestJwtService();
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            jwtService.validateToken(token);
        }
        long endTime = System.nanoTime();
        
        return (endTime - startTime) / 1_000_000; // 返回毫秒
    }
    
    /**
     * 測量 token 生成的性能
     */
    public static long measureTokenCreationTime(int iterations) {
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            createValidToken();
        }
        long endTime = System.nanoTime();
        
        return (endTime - startTime) / 1_000_000; // 返回毫秒
    }
    
    // ===== 批量測試數據生成 =====
    
    /**
     * 生成多個測試用戶的 JWT tokens
     */
    public static List<String> createMultipleUserTokens(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> createToken("user-" + i, "testuser" + i, 
                                        Arrays.asList("USER"), DEFAULT_EXPIRY_SECONDS))
                .toList();
    }
    
    /**
     * 生成不同過期時間的 tokens
     */
    public static List<String> createTokensWithDifferentExpiry() {
        return Arrays.asList(
            createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, DEFAULT_TEST_ROLES, 60),    // 1 minute
            createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, DEFAULT_TEST_ROLES, 3600),  // 1 hour
            createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, DEFAULT_TEST_ROLES, 86400), // 1 day
            createExpiredToken() // expired
        );
    }
    
    /**
     * 生成不同角色的 tokens
     */
    public static List<String> createTokensWithDifferentRoles() {
        return Arrays.asList(
            createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, Arrays.asList("USER"), DEFAULT_EXPIRY_SECONDS),
            createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, Arrays.asList("ADMIN"), DEFAULT_EXPIRY_SECONDS),
            createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, Arrays.asList("USER", "ADMIN"), DEFAULT_EXPIRY_SECONDS),
            createToken(DEFAULT_TEST_USER_ID, DEFAULT_TEST_USERNAME, Arrays.asList("MODERATOR", "USER"), DEFAULT_EXPIRY_SECONDS)
        );
    }
}