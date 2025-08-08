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
        // Given - 當前實現暫時總是返回未撤銷
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Remote revocation check not yet implemented");
        
        // 當前實現不會調用 gRPC 服務
        verifyNoInteractions(mockGrpcClientService);
    }
    
    @Test
    @DisplayName("測試 token 已撤銷的情況 - 暫時跳過")
    void testTokenRevoked() {
        // TODO: 當主服務實現撤銷檢查後再更新此測試
        // 當前實現暫時總是返回未撤銷
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then - 當前總是返回未撤銷
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.isValid()).isTrue();
        
        verifyNoInteractions(mockGrpcClientService);
    }
    
    @Test
    @DisplayName("測試 gRPC 服務失敗 - 暫時跳過")
    void testGrpcServiceFailure() {
        // TODO: 當主服務實現撤銷檢查後再更新此測試
        // 當前實現暫時總是返回未撤銷
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isValid()).isTrue();
        
        verifyNoInteractions(mockGrpcClientService);
    }
    
    @Test
    @DisplayName("測試 gRPC 調用拋出異常 - 暫時跳過")
    void testGrpcException() {
        // TODO: 當主服務實現撤銷檢查後再更新此測試
        // 當前實現不會拋出異常
        
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isValid()).isTrue();
        
        verifyNoInteractions(mockGrpcClientService);
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
        
        verifyNoInteractions(mockGrpcClientService);
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
        
        verifyNoInteractions(mockGrpcClientService);
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
        
        verifyNoInteractions(mockGrpcClientService);
    }
    
    @Test
    @DisplayName("測試可選參數為 null")
    void testOptionalParametersNull() {
        // When
        JwtRevocationService.RevocationCheckResult result = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, null, null);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.isValid()).isTrue();
        
        verifyNoInteractions(mockGrpcClientService);
    }
    
    // ===== 快取機制測試 =====
    
    @Test
    @DisplayName("測試快取命中 - 有效 token")
    void testCacheHitValid() {
        // When - 第一次調用
        JwtRevocationService.RevocationCheckResult result1 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // When - 第二次調用（應該從快取中獲取）
        JwtRevocationService.RevocationCheckResult result2 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result1.isValid()).isTrue();
        assertThat(result2.isValid()).isTrue();
        
        // 當前實現不會調用 gRPC 服務
        verifyNoInteractions(mockGrpcClientService);
    }
    
    @Test
    @DisplayName("測試快取命中 - 暫時跳過")
    void testCacheHitRevoked() {
        // TODO: 當主服務實現撤銷檢查後再更新此測試
        // 當前實現暫時總是返回未撤銷
        
        // When - 第一次調用
        JwtRevocationService.RevocationCheckResult result1 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // When - 第二次調用（應該從快取中獲取）
        JwtRevocationService.RevocationCheckResult result2 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then - 當前總是返回未撤銷
        assertThat(result1.isRevoked()).isFalse();
        assertThat(result2.isRevoked()).isFalse();
        
        verifyNoInteractions(mockGrpcClientService);
    }
    
    @Test
    @DisplayName("測試快取失效")
    void testCacheInvalidation() {
        // When - 第一次調用
        JwtRevocationService.RevocationCheckResult result1 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // 手動失效快取
        revocationService.invalidateCache(TEST_JWT_TOKEN);
        
        // When - 第二次調用（應該重新查詢）
        JwtRevocationService.RevocationCheckResult result2 = 
                revocationService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result1.isValid()).isTrue();
        assertThat(result2.isValid()).isTrue();
        
        // 當前實現不會調用 gRPC 服務
        verifyNoInteractions(mockGrpcClientService);
    }
    
    @Test
    @DisplayName("測試清除所有快取")
    void testClearAllCache() {
        // Given
        String token1 = "token1";
        String token2 = "token2";
        
        // When - 調用兩個不同的 token
        JwtRevocationService.RevocationCheckResult result1 = 
                revocationService.isTokenRevoked(token1, null, null);
        JwtRevocationService.RevocationCheckResult result2 = 
                revocationService.isTokenRevoked(token2, null, null);
        
        // 清除所有快取
        revocationService.clearCache();
        
        // 再次調用相同的 token
        JwtRevocationService.RevocationCheckResult result3 = 
                revocationService.isTokenRevoked(token1, null, null);
        JwtRevocationService.RevocationCheckResult result4 = 
                revocationService.isTokenRevoked(token2, null, null);
        
        // Then
        assertThat(result1.isValid()).isTrue();
        assertThat(result2.isValid()).isTrue();
        assertThat(result3.isValid()).isTrue();
        assertThat(result4.isValid()).isTrue();
        
        // 當前實現不會調用 gRPC 服務
        verifyNoInteractions(mockGrpcClientService);
    }
    
    // ===== 禁用快取測試 =====
    
    @Test
    @DisplayName("測試快取的服務")
    void testCacheEnabled() {
        // Given - 在 WebDAV 子服務中快取永遠啟用
        JwtRevocationService cacheService = new JwtRevocationService(mockGrpcClientService, 5, 1000);
        
        // When - 調用兩次相同的檢查
        JwtRevocationService.RevocationCheckResult result1 = 
                cacheService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        JwtRevocationService.RevocationCheckResult result2 = 
                cacheService.isTokenRevoked(TEST_JWT_TOKEN, TEST_TOKEN_ID, TEST_USER_ID);
        
        // Then
        assertThat(result1.isValid()).isTrue();
        assertThat(result2.isValid()).isTrue();
        
        // 當前實現不會調用 gRPC 服務
        verifyNoInteractions(mockGrpcClientService);
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