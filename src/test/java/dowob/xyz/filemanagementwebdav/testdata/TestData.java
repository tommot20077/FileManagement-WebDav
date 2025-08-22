package dowob.xyz.filemanagementwebdav.testdata;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import xyz.dowob.filemanagement.grpc.FileProcessingProto;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 測試數據集中管理類
 * 
 * 提供各種測試場景所需的測試數據，包括正常情況、異常情況和邊界情況。
 * 統一管理測試數據，提高測試的可維護性和一致性。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName TestData
 * @create 2025/8/5
 * @Version 1.0
 **/
public class TestData {
    
    // ===== 正常測試數據 =====
    
    /**
     * 有效的用戶名
     */
    public static final String VALID_USERNAME = "testuser";
    
    /**
     * 有效的密碼
     */
    public static final String VALID_PASSWORD = "testpass123";
    
    /**
     * 有效的用戶 ID
     */
    public static final String VALID_USER_ID = "12345";
    
    /**
     * 有效的客戶端 IP
     */
    public static final String VALID_CLIENT_IP = "192.168.1.100";
    
    /**
     * 有效的 User Agent
     */
    public static final String VALID_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    
    /**
     * 有效的用戶角色列表
     */
    public static final String VALID_ROLE = "USER";
    public static final List<String> VALID_ROLES = Arrays.asList("USER", "ADVANCED_USER"); // 保留用於舊測試的向後相容
    
    // ===== 邊界測試數據 =====
    
    /**
     * 空用戶名
     */
    public static final String EMPTY_USERNAME = "";
    
    /**
     * 空密碼
     */
    public static final String EMPTY_PASSWORD = "";
    
    /**
     * null 用戶名
     */
    public static final String NULL_USERNAME = null;
    
    /**
     * null 密碼
     */
    public static final String NULL_PASSWORD = null;
    
    /**
     * 超長用戶名（1000 個字符）
     */
    public static final String LONG_USERNAME = "a".repeat(1000);
    
    /**
     * 超長密碼（1000 個字符）
     */
    public static final String LONG_PASSWORD = "p".repeat(1000);
    
    /**
     * 包含特殊字符的用戶名
     */
    public static final String SPECIAL_USERNAME = "用戶名🎉@#$%^&*()";
    
    /**
     * 包含特殊字符的密碼
     */
    public static final String SPECIAL_PASSWORD = "密碼!@#$%^&*()_+{}[]|\\:;\"'<>,.?/~`";
    
    /**
     * 包含空格的用戶名
     */
    public static final String USERNAME_WITH_SPACES = "user with spaces";
    
    /**
     * 包含換行符的用戶名
     */
    public static final String USERNAME_WITH_NEWLINE = "user\nname";
    
    // ===== 異常測試數據 =====
    
    /**
     * SQL 注入嘗試的用戶名
     */
    public static final String SQL_INJECTION_USERNAME = "admin'; DROP TABLE users; --";
    
    /**
     * XSS 攻擊嘗試的用戶名
     */
    public static final String XSS_USERNAME = "<script>alert('xss')</script>";
    
    /**
     * 包含控制字符的用戶名
     */
    public static final String CONTROL_CHAR_USERNAME = "user\0\b\t\r\n";
    
    /**
     * 無效的客戶端 IP
     */
    public static final String INVALID_CLIENT_IP = "not.an.ip.address";
    
    // ===== JWT 相關測試數據 =====
    
    /**
     * JWT 測試密鑰
     */
    public static final String JWT_TEST_SECRET = "test-secret-key-for-jwt-testing-very-long-and-secure";
    
    /**
     * JWT 測試發行者
     */
    public static final String JWT_TEST_ISSUER = "FileManagement-System-Test";
    
    /**
     * 無效的 JWT 密鑰
     */
    public static final String JWT_WRONG_SECRET = "wrong-secret-key-should-fail-verification";
    
    /**
     * 無效的 JWT 發行者
     */
    public static final String JWT_WRONG_ISSUER = "wrong-issuer";
    
    /**
     * JWT 測試算法
     */
    public static final Algorithm JWT_TEST_ALGORITHM = Algorithm.HMAC256(JWT_TEST_SECRET);
    
    /**
     * JWT 錯誤算法
     */
    public static final Algorithm JWT_WRONG_ALGORITHM = Algorithm.HMAC256(JWT_WRONG_SECRET);
    
    /**
     * 有效的 JWT Token（未過期）
     */
    public static final String VALID_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, VALID_USERNAME, VALID_ROLE, 3600L, JWT_TEST_ALGORITHM);
    
    /**
     * 過期的 JWT Token
     */
    public static final String EXPIRED_JWT_TOKEN = createExpiredJwtToken(
        VALID_USER_ID, VALID_USERNAME, VALID_ROLE, JWT_TEST_ALGORITHM);
    
    /**
     * 錯誤簽名的 JWT Token
     */
    public static final String WRONG_SIGNATURE_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, VALID_USERNAME, VALID_ROLE, 3600L, JWT_WRONG_ALGORITHM);
    
    /**
     * 錯誤發行者的 JWT Token
     */
    public static final String WRONG_ISSUER_JWT_TOKEN = createJwtTokenWithIssuer(
        VALID_USER_ID, VALID_USERNAME, VALID_ROLE, 3600L, JWT_WRONG_ISSUER, JWT_TEST_ALGORITHM);
    
    /**
     * 缺少用戶名的 JWT Token
     */
    public static final String MISSING_USERNAME_JWT_TOKEN = createJwtTokenWithoutUsername(
        VALID_USER_ID, VALID_ROLE, 3600L, JWT_TEST_ALGORITHM);
    
    /**
     * 缺少用戶 ID 的 JWT Token
     */
    public static final String MISSING_USERID_JWT_TOKEN = createJwtTokenWithoutUserId(
        VALID_USERNAME, VALID_ROLE, 3600L, JWT_TEST_ALGORITHM);
    
    /**
     * 缺少角色的 JWT Token
     */
    public static final String MISSING_ROLES_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, VALID_USERNAME, null, 3600L, JWT_TEST_ALGORITHM);
    
    /**
     * 格式錯誤的 JWT Token
     */
    public static final String MALFORMED_JWT_TOKEN = "this.is.not.a.valid.jwt.token";
    
    /**
     * 無效 Base64 編碼的 JWT Token
     */
    public static final String INVALID_BASE64_JWT_TOKEN = "invalid@base64.encoding$.token";
    
    /**
     * 只有兩部分的字符串（不是有效的 JWT）
     */
    public static final String TWO_PARTS_TOKEN = "header.payload";
    
    /**
     * 只有一部分的字符串
     */
    public static final String ONE_PART_TOKEN = "onlyheader";
    
    /**
     * 空的 JWT Token
     */
    public static final String EMPTY_JWT_TOKEN = "";
    
    /**
     * 包含特殊字符的用戶名的 JWT Token
     */
    public static final String SPECIAL_CHAR_USERNAME_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, SPECIAL_USERNAME, VALID_ROLE, 3600L, JWT_TEST_ALGORITHM);
    
    /**
     * 長期有效的 JWT Token（10年）
     */
    public static final String LONG_TERM_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, VALID_USERNAME, VALID_ROLE, 315360000L, JWT_TEST_ALGORITHM); // 10 years
    
    /**
     * 即將過期的 JWT Token（1分鐘後過期）
     */
    public static final String SOON_EXPIRED_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, VALID_USERNAME, VALID_ROLE, 60L, JWT_TEST_ALGORITHM);
    
    /**
     * 具有管理員角色的 JWT Token
     */
    public static final String ADMIN_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, VALID_USERNAME, "ADMIN", 3600L, JWT_TEST_ALGORITHM);
    
    /**
     * 具有進階用戶角色的 JWT Token
     */
    public static final String MULTI_ROLES_JWT_TOKEN = createTestJwtToken(
        VALID_USER_ID, VALID_USERNAME, "ADVANCED_USER", 3600L, JWT_TEST_ALGORITHM);
    
    /**
     * 沒有過期時間的 JWT Token
     */
    public static final String NO_EXPIRY_JWT_TOKEN = createJwtTokenWithoutExpiry(
        VALID_USER_ID, VALID_USERNAME, VALID_ROLE, JWT_TEST_ALGORITHM);
    
    // ===== 錯誤消息 =====
    
    /**
     * 認證失敗錯誤消息
     */
    public static final String AUTH_FAILED_MESSAGE = "用戶名或密碼錯誤";
    
    /**
     * 服務不可用錯誤消息
     */
    public static final String SERVICE_UNAVAILABLE_MESSAGE = "認證服務暫時不可用，請稍後再試";
    
    /**
     * 超時錯誤消息
     */
    public static final String TIMEOUT_MESSAGE = "認證請求超時，請檢查網絡連接";
    
    /**
     * JWT Token 過期錯誤消息
     */
    public static final String JWT_EXPIRED_MESSAGE = "Token has expired";
    
    /**
     * JWT Token 驗證失敗錯誤消息
     */
    public static final String JWT_VERIFICATION_FAILED_MESSAGE = "Token verification failed";
    
    /**
     * JWT Token 缺少必要聲明錯誤消息
     */
    public static final String JWT_MISSING_CLAIMS_MESSAGE = "Token missing required claims";
    
    /**
     * JWT Token 格式錯誤消息
     */
    public static final String JWT_MALFORMED_MESSAGE = "Token is null or empty";
    
    /**
     * JWT 內部錯誤消息
     */
    public static final String JWT_INTERNAL_ERROR_MESSAGE = "Internal error during token validation";
    
    // ===== 工具方法 =====
    
    /**
     * 創建成功的認證響應
     */
    public static xyz.dowob.filemanagement.grpc.AuthenticationResponse createSuccessResponse() {
        return xyz.dowob.filemanagement.grpc.AuthenticationResponse.newBuilder()
                .setSuccess(true)
                .setUserId(Long.parseLong(VALID_USER_ID))
                .setJwtToken("test.jwt.token")
                .setUsername(VALID_USERNAME)
                .setRole(VALID_ROLE)
                .build();
    }
    
    /**
     * 創建失敗的認證響應
     */
    public static xyz.dowob.filemanagement.grpc.AuthenticationResponse createFailureResponse() {
        return xyz.dowob.filemanagement.grpc.AuthenticationResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(AUTH_FAILED_MESSAGE)
                .build();
    }
    
    /**
     * 創建認證請求
     */
    public static xyz.dowob.filemanagement.grpc.AuthenticationRequest createAuthRequest(
            String username, String password, String clientIp, String userAgent) {
        // Note: clientIp and userAgent are no longer part of AuthenticationRequest in the new proto
        return xyz.dowob.filemanagement.grpc.AuthenticationRequest.newBuilder()
                        .setUsername(username)
                        .setPassword(password)
                        .build();
    }
    
    /**
     * 生成隨機用戶名
     */
    public static String generateRandomUsername() {
        return "user_" + System.currentTimeMillis();
    }
    
    /**
     * 生成隨機密碼
     */
    public static String generateRandomPassword() {
        return "pass_" + System.nanoTime();
    }
    
    // ===== JWT 工具方法 =====
    
    /**
     * 創建標準的 JWT Token
     */
    public static String createTestJwtToken(String userId, String username, String role, 
                                          long expirySeconds, Algorithm algorithm) {
        var builder = JWT.create()
                .withIssuer(JWT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("username", username)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(expirySeconds)));
        
        if (role != null) {
            builder.withClaim("role", role);
        }
        
        return builder.sign(algorithm);
    }
    
    /**
     * 創建過期的 JWT Token
     */
    public static String createExpiredJwtToken(String userId, String username, String role, 
                                             Algorithm algorithm) {
        return JWT.create()
                .withIssuer(JWT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withIssuedAt(Date.from(Instant.now().minusSeconds(7200))) // 2 hours ago
                .withExpiresAt(Date.from(Instant.now().minusSeconds(3600))) // 1 hour ago (expired)
                .sign(algorithm);
    }
    
    /**
     * 創建指定發行者的 JWT Token
     */
    public static String createJwtTokenWithIssuer(String userId, String username, String role, 
                                                 long expirySeconds, String issuer, Algorithm algorithm) {
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(expirySeconds)))
                .sign(algorithm);
    }
    
    /**
     * 創建沒有用戶名的 JWT Token
     */
    public static String createJwtTokenWithoutUsername(String userId, String role, 
                                                     long expirySeconds, Algorithm algorithm) {
        return JWT.create()
                .withIssuer(JWT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("role", role)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(expirySeconds)))
                .sign(algorithm);
    }
    
    /**
     * 創建沒有用戶 ID（subject）的 JWT Token
     */
    public static String createJwtTokenWithoutUserId(String username, String role, 
                                                   long expirySeconds, Algorithm algorithm) {
        return JWT.create()
                .withIssuer(JWT_TEST_ISSUER)
                .withClaim("username", username)
                .withClaim("role", role)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(expirySeconds)))
                .sign(algorithm);
    }
    
    /**
     * 創建沒有過期時間的 JWT Token
     */
    public static String createJwtTokenWithoutExpiry(String userId, String username, String role, 
                                                   Algorithm algorithm) {
        return JWT.create()
                .withIssuer(JWT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withIssuedAt(Date.from(Instant.now()))
                // 沒有設置 withExpiresAt
                .sign(algorithm);
    }
    
    /**
     * 創建自定義聲明的 JWT Token
     */
    public static String createJwtTokenWithCustomClaims(String userId, String username, String role,
                                                      long expirySeconds, Algorithm algorithm,
                                                      String customKey, Object customValue) {
        return JWT.create()
                .withIssuer(JWT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim(customKey, customValue.toString())
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(expirySeconds)))
                .sign(algorithm);
    }
    
    /**
     * 創建具有指定角色的 JWT Token
     */
    public static String createJwtTokenWithRoles(String userId, String username, String role, 
                                                long expirySeconds) {
        return createTestJwtToken(userId, username, role, expirySeconds, JWT_TEST_ALGORITHM);
    }
    
    /**
     * 創建立即過期的 JWT Token（用於測試過期情況）
     */
    public static String createImmediatelyExpiredJwtToken(String userId, String username, String role) {
        return JWT.create()
                .withIssuer(JWT_TEST_ISSUER)
                .withSubject(userId)
                .withClaim("username", username)
                .withClaim("role", role)
                .withIssuedAt(Date.from(Instant.now().minusSeconds(10)))
                .withExpiresAt(Date.from(Instant.now().minusSeconds(5))) // 5 seconds ago
                .sign(JWT_TEST_ALGORITHM);
    }
    
    /**
     * 創建包含 JWT 的認證響應
     */
    public static xyz.dowob.filemanagement.grpc.AuthenticationResponse createJwtAuthResponse(String jwtToken) {
        return xyz.dowob.filemanagement.grpc.AuthenticationResponse.newBuilder()
                .setSuccess(true)
                .setUserId(Long.parseLong(VALID_USER_ID))
                .setJwtToken(jwtToken)
                .build();
    }
    
    /**
     * 檢查 JWT Token 是否為有效格式（僅檢查結構，不驗證簽名）
     */
    public static boolean isValidJwtFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }
    
    /**
     * 生成隨機的 JWT Secret
     */
    public static String generateRandomJwtSecret() {
        return "secret_" + System.currentTimeMillis() + "_" + System.nanoTime();
    }
    
    // ===== 檔案操作測試數據 =====
    
    /**
     * 有效的檔案 ID
     */
    public static final Long VALID_FILE_ID = 123L;
    
    /**
     * 無效的檔案 ID
     */
    public static final Long INVALID_FILE_ID = -1L;
    
    /**
     * 不存在的檔案 ID
     */
    public static final Long NON_EXISTENT_FILE_ID = 99999L;
    
    /**
     * 有效的父資料夾 ID
     */
    public static final Long VALID_PARENT_ID = 456L;
    
    /**
     * 根資料夾 ID
     */
    public static final Long ROOT_FOLDER_ID = 0L;
    
    /**
     * 有效的目標路徑
     */
    public static final String VALID_TARGET_PATH = "/testuser/folder/newfile.txt";
    
    /**
     * 根目錄目標路徑
     */
    public static final String ROOT_TARGET_PATH = "/testuser/rootfile.txt";
    
    /**
     * 深層目錄目標路徑
     */
    public static final String DEEP_TARGET_PATH = "/testuser/a/b/c/d/e/deepfile.txt";
    
    /**
     * 無效的目標路徑（空）
     */
    public static final String EMPTY_TARGET_PATH = "";
    
    /**
     * 無效的目標路徑（null）
     */
    public static final String NULL_TARGET_PATH = null;
    
    /**
     * 無效的目標路徑（只有斜線）
     */
    public static final String SLASH_ONLY_PATH = "/";
    
    /**
     * 包含特殊字符的目標路徑
     */
    public static final String SPECIAL_CHAR_TARGET_PATH = "/testuser/特殊文件夾/檔案名稱🎉.txt";
    
    /**
     * 有效的檔案名稱
     */
    public static final String VALID_FILE_NAME = "newfile.txt";
    
    /**
     * 特殊字符檔案名稱
     */
    public static final String SPECIAL_CHAR_FILE_NAME = "檔案名稱🎉.txt";
    
    /**
     * 有效的父路徑
     */
    public static final String VALID_PARENT_PATH = "/testuser/folder";
    
    /**
     * 根父路徑
     */
    public static final String ROOT_PARENT_PATH = "/";
    
    /**
     * 深層父路徑
     */
    public static final String DEEP_PARENT_PATH = "/testuser/a/b/c/d/e";
    
    /**
     * 超時時間（秒）
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    /**
     * 新檔案 ID（移動/複製操作後的結果）
     */
    public static final Long NEW_FILE_ID = 789L;
    
    // ===== 建構移動/複製相關響應 =====
    
    /**
     * 創建成功的移動檔案響應
     */
    public static xyz.dowob.filemanagement.grpc.MoveFileResponse createSuccessMoveResponse() {
        return xyz.dowob.filemanagement.grpc.MoveFileResponse.newBuilder()
                .setSuccess(true)
                .setNewFileId(NEW_FILE_ID)
                .build();
    }
    
    /**
     * 創建失敗的移動檔案響應
     */
    public static xyz.dowob.filemanagement.grpc.MoveFileResponse createFailureMoveResponse(String errorMessage) {
        return xyz.dowob.filemanagement.grpc.MoveFileResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(errorMessage)
                .build();
    }
    
    /**
     * 創建成功的複製檔案響應
     */
    public static xyz.dowob.filemanagement.grpc.CopyFileResponse createSuccessCopyResponse() {
        return xyz.dowob.filemanagement.grpc.CopyFileResponse.newBuilder()
                .setSuccess(true)
                .setNewFileId(NEW_FILE_ID)
                .build();
    }
    
    /**
     * 創建失敗的複製檔案響應
     */
    public static xyz.dowob.filemanagement.grpc.CopyFileResponse createFailureCopyResponse(String errorMessage) {
        return xyz.dowob.filemanagement.grpc.CopyFileResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(errorMessage)
                .build();
    }
    
    /**
     * 創建移動檔案請求
     */
    public static xyz.dowob.filemanagement.grpc.MoveFileRequest createMoveFileRequest(
            Long fileId, Long newParentId, String newName, String jwtToken, Long userId) {
        return xyz.dowob.filemanagement.grpc.MoveFileRequest.newBuilder()
                .setAuth(xyz.dowob.filemanagement.grpc.AuthRequest.newBuilder()
                        .setJwtToken(jwtToken)
                        .setUserId(userId)
                        .build())
                .setFileId(fileId)
                .setNewParentId(newParentId)
                .setNewName(newName)
                .build();
    }
    
    /**
     * 創建複製檔案請求
     */
    public static xyz.dowob.filemanagement.grpc.CopyFileRequest createCopyFileRequest(
            Long fileId, Long targetParentId, String newName, String jwtToken, Long userId) {
        return xyz.dowob.filemanagement.grpc.CopyFileRequest.newBuilder()
                .setAuth(xyz.dowob.filemanagement.grpc.AuthRequest.newBuilder()
                        .setJwtToken(jwtToken)
                        .setUserId(userId)
                        .build())
                .setFileId(fileId)
                .setTargetParentId(targetParentId)
                .setNewName(newName)
                .build();
    }
}