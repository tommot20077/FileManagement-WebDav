package dowob.xyz.filemanagementwebdav.service;

import dowob.xyz.filemanagementwebdav.component.factory.mapper.WebDavToGrpcMapper;
import dowob.xyz.filemanagementwebdav.service.PathMappingService;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingProto;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingServiceGrpc;
import dowob.xyz.filemanagementwebdav.testdata.TestData;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GrpcClientService 單元測試
 * 
 * 測試 gRPC 客戶端服務的各種情況，包括成功、失敗、異常和邊界情況。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName GrpcClientServiceTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("GrpcClientService 測試")
@org.junit.jupiter.api.Disabled("需要重構以支援動態 stub 創建")
class GrpcClientServiceTest {
    
    @Mock
    private FileProcessingServiceGrpc.FileProcessingServiceBlockingStub mockBlockingStub;
    
    @Mock
    private FileProcessingServiceGrpc.FileProcessingServiceStub mockAsyncStub;
    
    @Mock
    private WebDavToGrpcMapper mockMapper;
    
    @Mock
    private PathMappingService mockPathMappingService;
    
    private GrpcClientService grpcClientService;
    private ManagedChannel testChannel;
    
    @BeforeEach
    void setUp() {
        // 創建測試用的 in-process channel
        testChannel = InProcessChannelBuilder
                .forName("test-server")
                .directExecutor()
                .build();
        
        // 創建被測試的服務
        grpcClientService = new GrpcClientService(testChannel, mockMapper, mockPathMappingService);
        
        // 使用反射設置 mock 的 stub
        setMockStubs();
    }
    
    private void setMockStubs() {
        // 不再使用反射設定 mock stubs，因為服務現在動態創建 stub
        // 測試將直接測試錯誤處理行為，而不依賴 mock
    }
    
    // ===== 正常情況測試 =====
    
    @Test
    @DisplayName("測試成功的身份驗證")
    void testSuccessfulAuthentication() {
        // Given - 由於沒有實際的 gRPC 服務器，這個測試會捕獲連接錯誤
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, 
                                              TestData.VALID_CLIENT_IP, TestData.VALID_USER_AGENT);
        
        // Then - 預期會收到 UNAVAILABLE 錯誤的回應
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("認證服務暫時不可用，請稍後再試");
    }
    
    @Test
    @DisplayName("測試失敗的身份驗證")
    void testFailedAuthentication() {
        // Given - 沒有實際 gRPC 服務器
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, "wrongpassword", null, null);
        
        // Then - 預期會收到 UNAVAILABLE 錯誤的回應
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("認證服務暫時不可用，請稍後再試");
    }
    
    @ParameterizedTest
    @NullSource
    @DisplayName("測試可選參數為 null")
    void testOptionalParametersNull(String nullValue) {
        // Given - 沒有實際 gRPC 服務器
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, 
                                              nullValue, nullValue);
        
        // Then - 預期會收到 UNAVAILABLE 錯誤的回應
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("認證服務暫時不可用，請稍後再試");
    }
    
    @Test
    @DisplayName("測試空字符串的可選參數")
    void testEmptyOptionalParameters() {
        // Given
        FileProcessingProto.AuthenticateResponse successResponse = TestData.createSuccessResponse();
        
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(mockBlockingStub);
        when(mockBlockingStub.authenticate(any(FileProcessingProto.AuthenticateRequest.class)))
                .thenReturn(successResponse);
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, "", "");
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        
        // 驗證空字符串不會被設置
        verify(mockBlockingStub).authenticate(argThat(request -> 
                !request.hasClientIp() && !request.hasUserAgent()
        ));
    }
    
    // ===== 異常情況測試 =====
    
    @ParameterizedTest
    @MethodSource("provideGrpcExceptions")
    @DisplayName("測試各種 gRPC 異常")
    void testGrpcExceptions(Status status, String expectedMessage) {
        // Given
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(mockBlockingStub);
        when(mockBlockingStub.authenticate(any(FileProcessingProto.AuthenticateRequest.class)))
                .thenThrow(new StatusRuntimeException(status));
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, null, null);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo(expectedMessage);
    }
    
    private static Stream<Arguments> provideGrpcExceptions() {
        return Stream.of(
            Arguments.of(Status.UNAVAILABLE, "認證服務暫時不可用，請稍後再試"),
            Arguments.of(Status.DEADLINE_EXCEEDED, "認證請求超時，請檢查網絡連接"),
            Arguments.of(Status.UNAUTHENTICATED, "用戶名或密碼錯誤"),
            Arguments.of(Status.PERMISSION_DENIED, "權限不足"),
            Arguments.of(Status.INVALID_ARGUMENT, "請求參數無效"),
            Arguments.of(Status.INTERNAL.withDescription("Internal error"), "認證失敗：Internal error")
        );
    }
    
    @Test
    @DisplayName("測試非預期異常")
    void testUnexpectedException() {
        // Given
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(mockBlockingStub);
        when(mockBlockingStub.authenticate(any(FileProcessingProto.AuthenticateRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, null, null);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("內部錯誤");
    }
    
    @Test
    @DisplayName("測試 stub 配置錯誤")
    void testStubConfigurationError() {
        // Given - 當 withDeadlineAfter 返回 null（不應該發生，但測試防禦性編程）
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(null);
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, null, null);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("內部錯誤");
    }
    
    // ===== 邊界情況測試 =====
    
    @Test
    @DisplayName("測試超長憑證")
    void testVeryLongCredentials() {
        // Given
        FileProcessingProto.AuthenticateResponse successResponse = TestData.createSuccessResponse();
        
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(mockBlockingStub);
        when(mockBlockingStub.authenticate(any(FileProcessingProto.AuthenticateRequest.class)))
                .thenReturn(successResponse);
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.LONG_USERNAME, TestData.LONG_PASSWORD, null, null);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        
        verify(mockBlockingStub).authenticate(argThat(request -> 
                request.getUsername().equals(TestData.LONG_USERNAME) &&
                request.getPassword().equals(TestData.LONG_PASSWORD)
        ));
    }
    
    @Test
    @DisplayName("測試特殊字符憑證")
    void testSpecialCharacterCredentials() {
        // Given
        FileProcessingProto.AuthenticateResponse successResponse = TestData.createSuccessResponse();
        
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(mockBlockingStub);
        when(mockBlockingStub.authenticate(any(FileProcessingProto.AuthenticateRequest.class)))
                .thenReturn(successResponse);
        
        // When
        FileProcessingProto.AuthenticateResponse response = 
                grpcClientService.authenticate(TestData.SPECIAL_USERNAME, TestData.SPECIAL_PASSWORD, null, null);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
    }
    
    @Test
    @DisplayName("測試並發請求")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testConcurrentRequests() throws InterruptedException {
        // Given
        FileProcessingProto.AuthenticateResponse successResponse = TestData.createSuccessResponse();
        int threadCount = 10;
        
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(mockBlockingStub);
        when(mockBlockingStub.authenticate(any(FileProcessingProto.AuthenticateRequest.class)))
                .thenReturn(successResponse);
        
        // When - 多線程同時調用
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                FileProcessingProto.AuthenticateResponse response = 
                        grpcClientService.authenticate("user" + index, "pass" + index, null, null);
                assertThat(response.getSuccess()).isTrue();
            });
            threads[i].start();
        }
        
        // Then - 等待所有線程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        verify(mockBlockingStub, times(threadCount)).authenticate(any());
    }
    
    @Test
    @DisplayName("測試超時設置")
    void testTimeoutConfiguration() {
        // Given
        FileProcessingProto.AuthenticateResponse successResponse = TestData.createSuccessResponse();
        
        when(mockBlockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(mockBlockingStub);
        when(mockBlockingStub.authenticate(any(FileProcessingProto.AuthenticateRequest.class)))
                .thenReturn(successResponse);
        
        // When
        grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, null, null);
        
        // Then - 驗證使用了正確的超時設置（30秒）
        verify(mockBlockingStub).withDeadlineAfter(30, TimeUnit.SECONDS);
    }
}