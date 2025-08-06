package dowob.xyz.filemanagementwebdav.component.security;

import dowob.xyz.filemanagementwebdav.service.GrpcClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtRevocationService 單元測試
 * 
 * 測試 JWT 撤銷檢查服務的核心功能，包括快取機制和 gRPC 調用。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName JwtRevocationServiceTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtRevocationService 測試")
class JwtRevocationServiceTest {
    
    @Mock
    private GrpcClientService mockGrpcClientService;
    
    private JwtRevocationService revocationService;
    
    private static final String TEST_JWT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSIsInVzZXJuYW1lIjoidGVzdHVzZXIifQ.test";
    private static final String TEST_TOKEN_ID = "token123";
    private static final String TEST_USER_ID = "user123";
    
    @BeforeEach
    void setUp() {
        // 創建帶快取的服務
        revocationService = new JwtRevocationService(mockGrpcClientService, 5, 1000);
        reset(mockGrpcClientService);
    }
    
    // ===== 基本撤銷檢查測試 =====
    
    @Test
    @DisplayName("測試 token 未撤銷的情況")
    void testTokenNotRevoked() {
        // Given
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, false, "Token is valid");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenReturn(grpcResult);
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Token is valid");
        
        verify(mockGrpcClientService).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    @Test
    @DisplayName("測試 token 已撤銷的情況")
    void testTokenRevoked() {
        // Given
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, true, "Token has been revoked");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenReturn(grpcResult);
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isTrue();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Token has been revoked");
        
        verify(mockGrpcClientService).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    @Test
    @DisplayName("測試 gRPC 服務失敗")
    void testGrpcServiceFailure() {
        // Given
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(false, false, "Service unavailable");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenReturn(grpcResult);
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Service unavailable");
        
        verify(mockGrpcClientService).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    @Test
    @DisplayName("測試 gRPC 調用拋出異常")
    void testGrpcException() {
        // Given
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenThrow(new RuntimeException("Network error"));
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to check revocation status");
        
        verify(mockGrpcClientService).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    // ===== 輸入驗證測試 =====
    
    @Test
    @DisplayName("測試空 token")
    void testNullToken() {
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(null, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("null or empty");
        
        verify(mockGrpcClientService, never()).checkJwtRevocation(anyString(), anyString(), anyString());
    }
    
    @Test
    @DisplayName("測試空字符串 token")
    void testEmptyToken() {
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked("", TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("null or empty");
        
        verify(mockGrpcClientService, never()).checkJwtRevocation(anyString(), anyString(), anyString());
    }
    
    @Test
    @DisplayName("測試只有空格的 token")
    void testBlankToken() {
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked("   ", TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("null or empty");
        
        verify(mockGrpcClientService, never()).checkJwtRevocation(anyString(), anyString(), anyString());
    }
    
    @Test
    @DisplayName("測試可選參數為 null")
    void testOptionalParametersNull() {
        // Given
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, false, "Token is valid");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, null, null))
                .thenReturn(grpcResult);
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, null, null);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isFalse();
        
        verify(mockGrpcClientService).checkJwtRevocation(TEST_JWT_TOKEN, null, null);
    }
    
    // ===== 快取機制測試 =====
    
    @Test
    @DisplayName("測試快取命中 - 有效 token")
    void testCacheHitValid() {
        // Given
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, false, "Token is valid");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenReturn(grpcResult);
        
        // When - 第一次調用
        JwtRevocationService.RevocationCheckResult result1 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // When - 第二次調用（應該從快取中獲取）
        JwtRevocationService.RevocationCheckResult result2 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result1.isValid()).isTrue();
        assertThat(result2.isValid()).isTrue();
        
        // 驗證只調用了一次 gRPC 服務
        verify(mockGrpcClientService, times(1)).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    @Test
    @DisplayName("測試快取命中 - 撤銷的 token")
    void testCacheHitRevoked() {
        // Given
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, true, "Token has been revoked");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenReturn(grpcResult);
        
        // When - 第一次調用
        JwtRevocationService.RevocationCheckResult result1 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // When - 第二次調用（應該從快取中獲取）
        JwtRevocationService.RevocationCheckResult result2 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result1.isRevoked()).isTrue();
        assertThat(result2.isRevoked()).isTrue();
        
        // 驗證只調用了一次 gRPC 服務
        verify(mockGrpcClientService, times(1)).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    @Test
    @DisplayName("測試快取失效")
    void testCacheInvalidation() {
        // Given
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, false, "Token is valid");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenReturn(grpcResult);
        
        // When - 第一次調用
        revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // 手動失效快取
        revocationService.invalidateCache(TEST_JWT_TOKEN);
        
        // When - 第二次調用（應該重新查詢）
        revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        // 驗證調用了兩次 gRPC 服務
        verify(mockGrpcClientService, times(2)).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    @Test
    @DisplayName("測試清除所有快取")
    void testClearAllCache() {
        // Given
        String token1 = "token1";
        String token2 = "token2";
        
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, false, "Token is valid");
        when(mockGrpcClientService.checkJwtRevocation(token1, null, null))
                .thenReturn(grpcResult);
        when(mockGrpcClientService.checkJwtRevocation(token2, null, null))
                .thenReturn(grpcResult);
        
        // When - 調用兩個不同的 token
        revocationService.isTokenRevoked(token1, null, null);
        revocationService.isTokenRevoked(token2, null, null);
        
        // 清除所有快取
        revocationService.clearCache();
        
        // 再次調用相同的 token
        revocationService.isTokenRevoked(token1, null, null);
        revocationService.isTokenRevoked(token2, null, null);
        
        // Then
        // 驗證每個 token 都調用了兩次 gRPC 服務（快取被清除）
        verify(mockGrpcClientService, times(2)).checkJwtRevocation(token1, null, null);
        verify(mockGrpcClientService, times(2)).checkJwtRevocation(token2, null, null);
    }
    
    // ===== 禁用快取測試 =====
    
    @Test
    @DisplayName("測試快取的服務")
    void testCacheEnabled() {
        // Given - 在 WebDAV 子服務中快取永遠啟用
        JwtRevocationService cacheService = new JwtRevocationService(mockGrpcClientService, 5, 1000);
        
        GrpcClientService.JwtRevocationCheckResult grpcResult = 
                new GrpcClientService.JwtRevocationCheckResult(true, false, "Token is valid");
        when(mockGrpcClientService.checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID))
                .thenReturn(grpcResult);
        
        // When - 調用兩次相同的檢查
        cacheService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        cacheService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        // 驗證只調用了一次 gRPC 服務（有快取）
        verify(mockGrpcClientService, times(1)).checkJwtRevocation(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
    }
    
    // ===== 統計信息測試 =====
    
    @Test
    @DisplayName("測試獲取統計信息")
    void testGetStats() {
        // When
        String stats = revocationService.getStats();
        
        // Then
        assertThat(stats).contains("JWT Revocation Cache");
        assertThat(stats).contains("Size:");
        assertThat(stats).contains("Hit Rate:");
    }
    
    @Test
    @DisplayName("測試啟用快取時的統計信息")
    void testGetStatsWithEnabledCache() {
        // Given - 在 WebDAV 子服務中快取永遠啟用
        JwtRevocationService cacheService = new JwtRevocationService(mockGrpcClientService, 5, 1000);
        
        // When
        String stats = cacheService.getStats();
        
        // Then
        assertThat(stats).contains("JWT Revocation Cache");
        assertThat(stats).contains("Size:");
        assertThat(stats).contains("Hit Rate:");
    }
    
    // ===== RevocationCheckResult 測試 =====
    
    @Test
    @DisplayName("測試 RevocationCheckResult.valid() 創建")
    void testRevocationCheckResultValid() {
        // When
        JwtRevocationService.RevocationCheckResult result = 
                JwtRevocationService.RevocationCheckResult.valid("Token is valid");
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Token is valid");
    }
    
    @Test
    @DisplayName("測試 RevocationCheckResult.revoked() 創建")
    void testRevocationCheckResultRevoked() {
        // When
        JwtRevocationService.RevocationCheckResult result = 
                JwtRevocationService.RevocationCheckResult.revoked("Token has been revoked");
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isTrue();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Token has been revoked");
    }
    
    @Test
    @DisplayName("測試 RevocationCheckResult.error() 創建")
    void testRevocationCheckResultError() {
        // When
        JwtRevocationService.RevocationCheckResult result = 
                JwtRevocationService.RevocationCheckResult.error("Service error");
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Service error");
    }
}