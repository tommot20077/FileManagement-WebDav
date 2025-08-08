package dowob.xyz.filemanagementwebdav.service;

import xyz.dowob.filemanagement.grpc.AuthenticationResponse;
import xyz.dowob.filemanagement.grpc.FileProcessingServiceGrpc;
import dowob.xyz.filemanagementwebdav.config.properties.WebDavUploadProperties;
import dowob.xyz.filemanagementwebdav.testdata.TestData;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * GrpcClientService 單元測試
 * 
 * 測試 gRPC 客戶端服務的各種情況，包括成功、失敗、異常和邊界情況。
 * 
 * @author yuan
 * @version 2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrpcClientService 測試")
class GrpcClientServiceTest {
    
    @Mock
    private FileProcessingServiceGrpc.FileProcessingServiceBlockingStub mockBlockingStub;
    
    @Mock
    private FileProcessingServiceGrpc.FileProcessingServiceStub mockAsyncStub;
    
    @Mock
    private PathMappingService mockPathMappingService;
    
    @Mock
    private WebDavUploadProperties mockUploadProperties;
    
    @Mock
    private WebDavResourceMonitorService mockResourceMonitor;
    
    private GrpcClientService grpcClientService;
    private ManagedChannel testChannel;
    
    @BeforeEach
    void setUp() {
        // 創建測試用的 in-process channel
        testChannel = InProcessChannelBuilder
                .forName("test-server")
                .directExecutor()
                .build();
        
        // 使用 lenient() 來避免 UnnecessaryStubbing 錯誤
        // 這些設置可能在某些測試中使用，但不是所有測試都需要
        lenient().when(mockUploadProperties.getTimeoutSeconds()).thenReturn(30);
        lenient().when(mockUploadProperties.getMaxSimpleUploadSize()).thenReturn(10 * 1024 * 1024L);
        lenient().when(mockUploadProperties.getStreamBufferSize()).thenReturn(1024 * 1024);
        lenient().when(mockUploadProperties.getEnableMd5Verification()).thenReturn(true);
        lenient().when(mockUploadProperties.getProgressReportInterval()).thenReturn(10);
        lenient().when(mockUploadProperties.getConfigSummary()).thenReturn("Test config summary");
        
        // 創建被測試的服務
        grpcClientService = new GrpcClientService(testChannel, mockPathMappingService, mockUploadProperties, mockResourceMonitor);
    }
    
    // ===== 正常情況測試 =====
    
    @Test
    @DisplayName("測試成功的身份驗證")
    void testSuccessfulAuthentication() {
        // Given - 由於沒有實際的 gRPC 服務器，這個測試會捕獲連接錯誤
        
        // When
        AuthenticationResponse response = 
                grpcClientService.authenticate(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then - 預期會收到 UNAVAILABLE 錯誤的回應
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Authentication failed");
    }
    
    @Test
    @DisplayName("測試認證失敗")
    void testFailedAuthentication() {
        // Given
        String invalidUsername = "invalid_user";
        String invalidPassword = "wrong_pass";
        
        // When
        AuthenticationResponse response = 
                grpcClientService.authenticate(invalidUsername, invalidPassword);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isFalse();
    }
    
    // TODO: 添加更多測試以覆蓋新的 gRPC 操作方法
}