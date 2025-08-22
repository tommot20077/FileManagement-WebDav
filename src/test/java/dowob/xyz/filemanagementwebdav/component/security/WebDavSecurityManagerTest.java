package dowob.xyz.filemanagementwebdav.component.security;

import dowob.xyz.filemanagementwebdav.component.cache.AuthenticationCache;
import xyz.dowob.filemanagement.grpc.FileProcessingProto;
import dowob.xyz.filemanagementwebdav.service.GrpcClientService;
import dowob.xyz.filemanagementwebdav.testdata.TestData;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * WebDavSecurityManager 單元測試
 * 
 * 測試 WebDAV 安全管理器的身份驗證和授權功能。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavSecurityManagerTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("WebDavSecurityManager 測試")
class WebDavSecurityManagerTest {
    
    @Mock
    private GrpcClientService mockGrpcClientService;
    
    @Mock
    private AuthenticationCache mockAuthCache;
    
    @Mock
    private JwtService mockJwtService;
    
    @Mock 
    private JwtRevocationService mockJwtRevocationService;
    
    @Mock
    private dowob.xyz.filemanagementwebdav.context.AuthenticationContextManager mockAuthContextManager;
    
    @Mock
    private Request mockRequest;
    
    @Mock
    private Resource mockResource;
    
    @Mock
    private Auth mockAuth;
    
    @InjectMocks
    private WebDavSecurityManager securityManager;
    
    @BeforeEach
    void setUp() {
        // 重置 mocks
        reset(mockGrpcClientService, mockAuthCache, mockJwtService, mockJwtRevocationService, 
              mockAuthContextManager, mockRequest, mockResource, mockAuth);
    }
    
    // ===== authenticate(String, String) 測試 =====
    
    @Test
    @DisplayName("測試從快取成功獲取認證")
    void testAuthenticateWithCacheHit() {
        // Given
        AuthenticationCache.AuthCacheEntry cacheEntry = 
                new AuthenticationCache.AuthCacheEntry(TestData.VALID_USER_ID, TestData.VALID_USERNAME, TestData.VALID_ROLE, true);
        
        when(mockAuthCache.get(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(cacheEntry);
        when(mockJwtService.isJwtFormat(TestData.VALID_PASSWORD)).thenReturn(false);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(WebDavSecurityManager.AuthenticatedUser.class);
        
        WebDavSecurityManager.AuthenticatedUser user = (WebDavSecurityManager.AuthenticatedUser) result;
        assertThat(user.getUserId()).isEqualTo(TestData.VALID_USER_ID);
        assertThat(user.getUsername()).isEqualTo(TestData.VALID_USERNAME);
        
        // 驗證沒有調用 gRPC 服務
        verify(mockGrpcClientService, never()).authenticate(anyString(), anyString());
        // 驗證沒有更新快取
        verify(mockAuthCache, never()).put(anyString(), anyString(), any(), any(), anyBoolean());
        // 驗證檢查了JWT格式
        verify(mockJwtService).isJwtFormat(TestData.VALID_PASSWORD);
    }
    
    @Test
    @DisplayName("測試從快取獲取失敗的認證結果")
    void testAuthenticateWithCacheHitFailure() {
        // Given
        AuthenticationCache.AuthCacheEntry cacheEntry = 
                new AuthenticationCache.AuthCacheEntry(null, TestData.VALID_USERNAME, null, false);
        
        when(mockAuthCache.get(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(cacheEntry);
        when(mockJwtService.isJwtFormat(TestData.VALID_PASSWORD)).thenReturn(false);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then
        assertThat(result).isNull();
        
        // 驗證沒有調用 gRPC 服務
        verify(mockGrpcClientService, never()).authenticate(anyString(), anyString());
        // 驗證檢查了JWT格式
        verify(mockJwtService).isJwtFormat(TestData.VALID_PASSWORD);
    }
    
    @Test
    @DisplayName("測試快取未命中，調用 gRPC 成功")
    void testAuthenticateWithCacheMissSuccess() {
        // Given
        when(mockJwtService.isJwtFormat(TestData.VALID_PASSWORD)).thenReturn(false);
        when(mockAuthCache.get(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(null);
        
        xyz.dowob.filemanagement.grpc.AuthenticationResponse successResponse = TestData.createSuccessResponse();
        when(mockGrpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(successResponse);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(WebDavSecurityManager.AuthenticatedUser.class);
        
        WebDavSecurityManager.AuthenticatedUser user = (WebDavSecurityManager.AuthenticatedUser) result;
        assertThat(user.getUserId()).isEqualTo(TestData.VALID_USER_ID);
        // AuthenticationResponse 現在包含 role 欄位
        assertThat(user.getRoles()).containsExactly("USER");
        
        // 驗證檢查了JWT格式
        verify(mockJwtService).isJwtFormat(TestData.VALID_PASSWORD);
        // 驗證調用了 gRPC 服務
        verify(mockGrpcClientService).authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        // 驗證更新了快取（包含角色）
        verify(mockAuthCache).put(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, TestData.VALID_USER_ID, "USER", true);
    }
    
    @Test
    @DisplayName("測試快取未命中，調用 gRPC 失敗")
    void testAuthenticateWithCacheMissFailure() {
        // Given
        when(mockJwtService.isJwtFormat(TestData.VALID_PASSWORD)).thenReturn(false);
        when(mockAuthCache.get(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(null);
        
        xyz.dowob.filemanagement.grpc.AuthenticationResponse failureResponse = TestData.createFailureResponse();
        when(mockGrpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(failureResponse);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then
        assertThat(result).isNull();
        
        // 驗證檢查了JWT格式
        verify(mockJwtService).isJwtFormat(TestData.VALID_PASSWORD);
        // 驗證調用了 gRPC 服務
        verify(mockGrpcClientService).authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        // 驗證更新了快取（失敗結果）
        verify(mockAuthCache).put(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, null, "USER", false);
    }
    
    @Test
    @DisplayName("測試 gRPC 調用拋出異常")
    void testAuthenticateWithGrpcException() {
        // Given
        when(mockJwtService.isJwtFormat(TestData.VALID_PASSWORD)).thenReturn(false);
        when(mockAuthCache.get(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(null);
        when(mockGrpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenThrow(new RuntimeException("gRPC error"));
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then
        assertThat(result).isNull();
        
        // 驗證檢查了JWT格式
        verify(mockJwtService).isJwtFormat(TestData.VALID_PASSWORD);
        // 驗證沒有更新快取
        verify(mockAuthCache, never()).put(anyString(), anyString(), any(), any(), anyBoolean());
    }
    
    @Test
    @DisplayName("測試沒有 userId 的響應")
    void testAuthenticateWithoutUserId() {
        // Given
        when(mockJwtService.isJwtFormat(TestData.VALID_PASSWORD)).thenReturn(false);
        when(mockAuthCache.get(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(null);
        
        xyz.dowob.filemanagement.grpc.AuthenticationResponse response = xyz.dowob.filemanagement.grpc.AuthenticationResponse.newBuilder()
                .setSuccess(true)
                // 沒有設置 userId (默認為 0)
                .setUsername(TestData.VALID_USERNAME) // 設置 username
                .setRole("USER") // 設置默認 role
                .build();
        
        when(mockGrpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD))
                .thenReturn(response);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then
        assertThat(result).isNotNull();
        
        // 驗證檢查了JWT格式
        verify(mockJwtService).isJwtFormat(TestData.VALID_PASSWORD);
        // 驗證快取存儲時處理了 userId 為 0 的情況
        verify(mockAuthCache).put(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, "0", "USER", true);
    }
    
    // ===== authorise 測試 =====
    
    @Test
    @DisplayName("測試授權：有效的認證")
    void testAuthoriseWithValidAuth() {
        // Given
        WebDavSecurityManager.AuthenticatedUser user = 
                new WebDavSecurityManager.AuthenticatedUser(TestData.VALID_USER_ID, TestData.VALID_USERNAME, TestData.VALID_ROLE);
        
        when(mockAuth.getTag()).thenReturn(user);
        when(mockResource.getName()).thenReturn("test.txt");
        
        // When
        boolean result = securityManager.authorise(mockRequest, Request.Method.GET, mockAuth, mockResource);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("測試授權：無認證信息")
    void testAuthoriseWithNullAuth() {
        // When
        boolean result = securityManager.authorise(mockRequest, Request.Method.GET, null, mockResource);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("測試授權：錯誤的認證標籤類型")
    void testAuthoriseWithInvalidAuthTag() {
        // Given
        when(mockAuth.getTag()).thenReturn("Invalid tag type");
        
        // When
        boolean result = securityManager.authorise(mockRequest, Request.Method.GET, mockAuth, mockResource);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("測試授權：null 認證標籤")
    void testAuthoriseWithNullAuthTag() {
        // Given
        when(mockAuth.getTag()).thenReturn(null);
        
        // When
        boolean result = securityManager.authorise(mockRequest, Request.Method.GET, mockAuth, mockResource);
        
        // Then
        assertThat(result).isFalse();
    }
    
    // ===== 其他方法測試 =====
    
    @Test
    @DisplayName("測試獲取領域名稱")
    void testGetRealm() {
        // When
        String realm = securityManager.getRealm("localhost");
        
        // Then
        assertThat(realm).isEqualTo("FileManagement WebDAV");
    }
    
    @Test
    @DisplayName("測試摘要認證支持")
    void testIsDigestAllowed() {
        // When
        boolean allowed = securityManager.isDigestAllowed();
        
        // Then
        assertThat(allowed).isFalse();
    }
    
    @Test
    @DisplayName("測試摘要認證方法")
    void testAuthenticateDigest() {
        // Given
        DigestResponse digestResponse = mock(DigestResponse.class);
        
        // When
        Object result = securityManager.authenticate(digestResponse);
        
        // Then
        assertThat(result).isNull();
    }
    
    // ===== AuthenticatedUser 內部類測試 =====
    
    @Test
    @DisplayName("測試 AuthenticatedUser 功能")
    void testAuthenticatedUser() {
        // Given
        WebDavSecurityManager.AuthenticatedUser user = 
                new WebDavSecurityManager.AuthenticatedUser(
                        TestData.VALID_USER_ID, 
                        TestData.VALID_USERNAME, 
                        TestData.VALID_ROLE);
        
        // Then
        assertThat(user.getUserId()).isEqualTo(TestData.VALID_USER_ID);
        assertThat(user.getUsername()).isEqualTo(TestData.VALID_USERNAME);
        assertThat(user.getRole()).isEqualTo(TestData.VALID_ROLE);
        assertThat(user.hasRole("USER")).isTrue();
        assertThat(user.hasRole("ADMIN")).isFalse();
    }
    
    @Test
    @DisplayName("測試 AuthenticatedUser 空角色列表")
    void testAuthenticatedUserWithNullRoles() {
        // Given
        WebDavSecurityManager.AuthenticatedUser user = 
                new WebDavSecurityManager.AuthenticatedUser(
                        TestData.VALID_USER_ID, 
                        TestData.VALID_USERNAME, 
                        (String) null);
        
        // Then
        assertThat(user.getRoles()).isEmpty(); // null 角色會返回空列表
        assertThat(user.hasRole("USER")).isFalse();
    }
    
    @Test
    @DisplayName("測試 AuthenticatedUser 空角色列表")
    void testAuthenticatedUserWithEmptyRoles() {
        // Given
        WebDavSecurityManager.AuthenticatedUser user = 
                new WebDavSecurityManager.AuthenticatedUser(
                        TestData.VALID_USER_ID, 
                        TestData.VALID_USERNAME, 
                        (String) null);
        
        // Then
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.hasRole("USER")).isFalse();
    }
    
    // ===== 邊界和異常情況測試 =====
    
    @Test
    @DisplayName("測試特殊字符用戶名密碼")
    void testAuthenticateWithSpecialCharacters() {
        // Given
        when(mockJwtService.isJwtFormat(TestData.SPECIAL_PASSWORD)).thenReturn(false);
        when(mockAuthCache.get(TestData.SPECIAL_USERNAME, TestData.SPECIAL_PASSWORD))
                .thenReturn(null);
        
        xyz.dowob.filemanagement.grpc.AuthenticationResponse successResponse = TestData.createSuccessResponse();
        when(mockGrpcClientService.authenticate(TestData.SPECIAL_USERNAME, TestData.SPECIAL_PASSWORD))
                .thenReturn(successResponse);
        
        // When
        Object result = securityManager.authenticate(TestData.SPECIAL_USERNAME, TestData.SPECIAL_PASSWORD);
        
        // Then
        assertThat(result).isNotNull();
        verify(mockJwtService).isJwtFormat(TestData.SPECIAL_PASSWORD);
        verify(mockAuthCache).put(TestData.VALID_USERNAME, TestData.SPECIAL_PASSWORD, TestData.VALID_USER_ID, "USER", true);
    }
    
    @Test
    @DisplayName("測試空用戶名密碼")
    void testAuthenticateWithEmptyCredentials() {
        // Given
        when(mockJwtService.isJwtFormat(TestData.EMPTY_PASSWORD)).thenReturn(false);
        when(mockAuthCache.get(TestData.EMPTY_USERNAME, TestData.EMPTY_PASSWORD))
                .thenReturn(null);
        
        xyz.dowob.filemanagement.grpc.AuthenticationResponse failureResponse = TestData.createFailureResponse();
        when(mockGrpcClientService.authenticate(TestData.EMPTY_USERNAME, TestData.EMPTY_PASSWORD))
                .thenReturn(failureResponse);
        
        // When & Then - 應該正常處理，不拋出異常
        assertThatCode(() -> 
                securityManager.authenticate(TestData.EMPTY_USERNAME, TestData.EMPTY_PASSWORD)
        ).doesNotThrowAnyException();
        
        // 驗證檢查了JWT格式
        verify(mockJwtService).isJwtFormat(TestData.EMPTY_PASSWORD);
    }
    
    // ===== JWT 認證測試 =====
    
    @Test
    @DisplayName("測試 JWT 認證成功")
    void testAuthenticateWithJwtSuccess() {
        // Given
        String jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSIsInVzZXJuYW1lIjoidGVzdHVzZXIiLCJyb2xlcyI6WyJVU0VSIl19.test";
        
        when(mockJwtService.isJwtFormat(jwtToken)).thenReturn(true);
        
        JwtService.JwtValidationResult validResult = JwtService.JwtValidationResult.valid(
            TestData.VALID_USER_ID, TestData.VALID_USERNAME, TestData.VALID_ROLE, null);
        when(mockJwtService.validateToken(jwtToken)).thenReturn(validResult);
        
        JwtRevocationService.RevocationCheckResult notRevokedResult = 
            JwtRevocationService.RevocationCheckResult.valid("Token is valid");
        when(mockJwtRevocationService.isTokenRevoked(jwtToken, null, TestData.VALID_USER_ID))
            .thenReturn(notRevokedResult);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, jwtToken);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(WebDavSecurityManager.AuthenticatedUser.class);
        
        WebDavSecurityManager.AuthenticatedUser user = (WebDavSecurityManager.AuthenticatedUser) result;
        assertThat(user.getUserId()).isEqualTo(TestData.VALID_USER_ID);
        assertThat(user.getUsername()).isEqualTo(TestData.VALID_USERNAME);
        assertThat(user.getRole()).isEqualTo(TestData.VALID_ROLE);
        
        // 驗證調用了必要的方法
        verify(mockJwtService).isJwtFormat(jwtToken);
        verify(mockJwtService).validateToken(jwtToken);
        verify(mockJwtRevocationService).isTokenRevoked(jwtToken, null, TestData.VALID_USER_ID);
        
        // 驗證沒有調用 gRPC 服務和緩存
        verify(mockGrpcClientService, never()).authenticate(anyString(), anyString());
        verify(mockAuthCache, never()).get(anyString(), anyString());
    }
    
    @Test
    @DisplayName("測試 JWT 驗證失敗")
    void testAuthenticateWithJwtValidationFailure() {
        // Given
        String invalidJwtToken = "invalid.jwt.token";
        
        when(mockJwtService.isJwtFormat(invalidJwtToken)).thenReturn(true);
        
        JwtService.JwtValidationResult invalidResult = JwtService.JwtValidationResult.invalid("Token is expired");
        when(mockJwtService.validateToken(invalidJwtToken)).thenReturn(invalidResult);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, invalidJwtToken);
        
        // Then
        assertThat(result).isNull();
        
        // 驗證調用了JWT驗證
        verify(mockJwtService).isJwtFormat(invalidJwtToken);
        verify(mockJwtService).validateToken(invalidJwtToken);
        
        // 驗證沒有檢查撤銷（因為驗證就失敗了）
        verify(mockJwtRevocationService, never()).isTokenRevoked(anyString(), any(), any());
    }
    
    @Test
    @DisplayName("測試 JWT 用戶名不匹配")
    void testAuthenticateWithJwtUsernameMismatch() {
        // Given
        String jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSIsInVzZXJuYW1lIjoidGVzdHVzZXIyIiwicm9sZXMiOlsiVVNFUiJdfQ.test";
        
        when(mockJwtService.isJwtFormat(jwtToken)).thenReturn(true);
        
        JwtService.JwtValidationResult validResult = JwtService.JwtValidationResult.valid(
            TestData.VALID_USER_ID, "testuser2", TestData.VALID_ROLE, null);
        when(mockJwtService.validateToken(jwtToken)).thenReturn(validResult);
        
        // When - 請求用戶名與JWT中的用戶名不匹配
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, jwtToken);
        
        // Then
        assertThat(result).isNull();
        
        // 驗證調用了JWT驗證
        verify(mockJwtService).isJwtFormat(jwtToken);
        verify(mockJwtService).validateToken(jwtToken);
        
        // 驗證沒有檢查撤銷（因為用戶名不匹配就被拒絕了）
        verify(mockJwtRevocationService, never()).isTokenRevoked(anyString(), any(), any());
    }
    
    @Test
    @DisplayName("測試 JWT 被撤銷")
    void testAuthenticateWithRevokedJwt() {
        // Given
        String revokedJwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSIsInVzZXJuYW1lIjoidGVzdHVzZXIiLCJyb2xlcyI6WyJVU0VSIl19.test";
        
        when(mockJwtService.isJwtFormat(revokedJwtToken)).thenReturn(true);
        
        JwtService.JwtValidationResult validResult = JwtService.JwtValidationResult.valid(
            TestData.VALID_USER_ID, TestData.VALID_USERNAME, TestData.VALID_ROLE, null);
        when(mockJwtService.validateToken(revokedJwtToken)).thenReturn(validResult);
        
        JwtRevocationService.RevocationCheckResult revokedResult = 
            JwtRevocationService.RevocationCheckResult.revoked("Token has been revoked");
        when(mockJwtRevocationService.isTokenRevoked(revokedJwtToken, null, TestData.VALID_USER_ID))
            .thenReturn(revokedResult);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, revokedJwtToken);
        
        // Then
        assertThat(result).isNull();
        
        // 驗證調用了所有必要的方法
        verify(mockJwtService).isJwtFormat(revokedJwtToken);
        verify(mockJwtService).validateToken(revokedJwtToken);
        verify(mockJwtRevocationService).isTokenRevoked(revokedJwtToken, null, TestData.VALID_USER_ID);
    }
    
    @Test
    @DisplayName("測試撤銷檢查服務失敗")
    void testAuthenticateWithRevocationCheckFailure() {
        // Given
        String jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSIsInVzZXJuYW1lIjoidGVzdHVzZXIiLCJyb2xlcyI6WyJVU0VSIl19.test";
        
        when(mockJwtService.isJwtFormat(jwtToken)).thenReturn(true);
        
        JwtService.JwtValidationResult validResult = JwtService.JwtValidationResult.valid(
            TestData.VALID_USER_ID, TestData.VALID_USERNAME, TestData.VALID_ROLE, null);
        when(mockJwtService.validateToken(jwtToken)).thenReturn(validResult);
        
        JwtRevocationService.RevocationCheckResult errorResult = 
            JwtRevocationService.RevocationCheckResult.error("Service unavailable");
        when(mockJwtRevocationService.isTokenRevoked(jwtToken, null, TestData.VALID_USER_ID))
            .thenReturn(errorResult);
        
        // When
        Object result = securityManager.authenticate(TestData.VALID_USERNAME, jwtToken);
        
        // Then
        assertThat(result).isNull();
        
        // 驗證調用了所有必要的方法
        verify(mockJwtService).isJwtFormat(jwtToken);
        verify(mockJwtService).validateToken(jwtToken);
        verify(mockJwtRevocationService).isTokenRevoked(jwtToken, null, TestData.VALID_USER_ID);
    }
}