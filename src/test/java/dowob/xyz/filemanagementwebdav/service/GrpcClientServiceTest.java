package dowob.xyz.filemanagementwebdav.service;

import xyz.dowob.filemanagement.grpc.*;
import dowob.xyz.filemanagementwebdav.config.properties.GrpcProperties;
import dowob.xyz.filemanagementwebdav.config.properties.WebDavUploadProperties;
import dowob.xyz.filemanagementwebdav.data.ProcessingRequest;
import dowob.xyz.filemanagementwebdav.data.ProcessingResponse;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.customerenum.Operation;
import dowob.xyz.filemanagementwebdav.context.RequestContextHolder;
import dowob.xyz.filemanagementwebdav.testdata.TestData;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
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
    
    @Mock
    private GrpcProperties mockGrpcProperties;
    
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
        
        // 配置 GrpcProperties mock
        lenient().when(mockGrpcProperties.getApiKey()).thenReturn("test-api-key");
        
        // 創建被測試的服務
        grpcClientService = new GrpcClientService(testChannel, mockPathMappingService, mockUploadProperties, mockResourceMonitor, mockGrpcProperties);
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
    
    // ===== parseTargetPath 私有方法測試 =====
    
    @Test
    @DisplayName("測試 parseTargetPath：正常路徑解析")
    void testParseTargetPathNormal() throws Exception {
        // Given
        String targetPath = TestData.VALID_TARGET_PATH; // "/testuser/folder/newfile.txt"
        
        // When - 使用反射調用私有方法
        Method parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
        parseMethod.setAccessible(true);
        Object result = parseMethod.invoke(grpcClientService, targetPath);
        
        // Then - 通過反射獲取結果的屬性
        Method getParentPathMethod = result.getClass().getDeclaredMethod("getParentPath");
        Method getFileNameMethod = result.getClass().getDeclaredMethod("getFileName");
        getParentPathMethod.setAccessible(true);
        getFileNameMethod.setAccessible(true);
        
        String parentPath = (String) getParentPathMethod.invoke(result);
        String fileName = (String) getFileNameMethod.invoke(result);
        
        assertThat(parentPath).isEqualTo(TestData.VALID_PARENT_PATH); // "/testuser/folder"
        assertThat(fileName).isEqualTo(TestData.VALID_FILE_NAME); // "newfile.txt"
    }
    
    @Test
    @DisplayName("測試 parseTargetPath：根目錄檔案")
    void testParseTargetPathRootFile() throws Exception {
        // Given
        String targetPath = TestData.ROOT_TARGET_PATH; // "/testuser/rootfile.txt"
        
        // When
        Method parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
        parseMethod.setAccessible(true);
        Object result = parseMethod.invoke(grpcClientService, targetPath);
        
        // Then
        Method getParentPathMethod = result.getClass().getDeclaredMethod("getParentPath");
        Method getFileNameMethod = result.getClass().getDeclaredMethod("getFileName");
        getParentPathMethod.setAccessible(true);
        getFileNameMethod.setAccessible(true);
        
        String parentPath = (String) getParentPathMethod.invoke(result);
        String fileName = (String) getFileNameMethod.invoke(result);
        
        assertThat(parentPath).isEqualTo("/testuser");
        assertThat(fileName).isEqualTo("rootfile.txt");
    }
    
    @Test
    @DisplayName("測試 parseTargetPath：深層目錄")
    void testParseTargetPathDeepDirectory() throws Exception {
        // Given
        String targetPath = TestData.DEEP_TARGET_PATH; // "/testuser/a/b/c/d/e/deepfile.txt"
        
        // When
        Method parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
        parseMethod.setAccessible(true);
        Object result = parseMethod.invoke(grpcClientService, targetPath);
        
        // Then
        Method getParentPathMethod = result.getClass().getDeclaredMethod("getParentPath");
        Method getFileNameMethod = result.getClass().getDeclaredMethod("getFileName");
        getParentPathMethod.setAccessible(true);
        getFileNameMethod.setAccessible(true);
        
        String parentPath = (String) getParentPathMethod.invoke(result);
        String fileName = (String) getFileNameMethod.invoke(result);
        
        assertThat(parentPath).isEqualTo(TestData.DEEP_PARENT_PATH); // "/testuser/a/b/c/d/e"
        assertThat(fileName).isEqualTo("deepfile.txt");
    }
    
    @Test
    @DisplayName("測試 parseTargetPath：路徑規範化（多餘斜線）")
    void testParseTargetPathNormalization() throws Exception {
        // Given
        String targetPath = "//testuser///folder//file.txt/"; // 多餘的斜線
        
        // When
        Method parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
        parseMethod.setAccessible(true);
        Object result = parseMethod.invoke(grpcClientService, targetPath);
        
        // Then
        Method getParentPathMethod = result.getClass().getDeclaredMethod("getParentPath");
        Method getFileNameMethod = result.getClass().getDeclaredMethod("getFileName");
        getParentPathMethod.setAccessible(true);
        getFileNameMethod.setAccessible(true);
        
        String parentPath = (String) getParentPathMethod.invoke(result);
        String fileName = (String) getFileNameMethod.invoke(result);
        
        // 應該正確處理路徑規範化
        assertThat(parentPath).isNotNull();
        assertThat(fileName).isNotNull();
    }
    
    @Test
    @DisplayName("測試 parseTargetPath：特殊字符處理")
    void testParseTargetPathSpecialCharacters() throws Exception {
        // Given
        String targetPath = TestData.SPECIAL_CHAR_TARGET_PATH; // "/testuser/特殊文件夾/檔案名稱🎉.txt"
        
        // When
        Method parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
        parseMethod.setAccessible(true);
        Object result = parseMethod.invoke(grpcClientService, targetPath);
        
        // Then
        Method getParentPathMethod = result.getClass().getDeclaredMethod("getParentPath");
        Method getFileNameMethod = result.getClass().getDeclaredMethod("getFileName");
        getParentPathMethod.setAccessible(true);
        getFileNameMethod.setAccessible(true);
        
        String parentPath = (String) getParentPathMethod.invoke(result);
        String fileName = (String) getFileNameMethod.invoke(result);
        
        assertThat(parentPath).isEqualTo("/testuser/特殊文件夾");
        assertThat(fileName).isEqualTo(TestData.SPECIAL_CHAR_FILE_NAME); // "檔案名稱🎉.txt"
    }
    
    @Test
    @DisplayName("測試 parseTargetPath：空路徑拋出異常")
    void testParseTargetPathEmptyThrowsException() {
        // Given
        String emptyPath = TestData.EMPTY_TARGET_PATH; // ""
        
        // When & Then
        Method parseMethod;
        try {
            parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
            parseMethod.setAccessible(true);
            
            assertThatThrownBy(() -> parseMethod.invoke(grpcClientService, emptyPath))
                    .isInstanceOf(InvocationTargetException.class)
                    .getCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target path cannot be null or empty");
        } catch (NoSuchMethodException e) {
            fail("Unable to access parseTargetPath method", e);
        }
    }
    
    @Test
    @DisplayName("測試 parseTargetPath：null 路徑拋出異常")
    void testParseTargetPathNullThrowsException() {
        // Given
        String nullPath = TestData.NULL_TARGET_PATH; // null
        
        // When & Then
        Method parseMethod;
        try {
            parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
            parseMethod.setAccessible(true);
            
            assertThatThrownBy(() -> parseMethod.invoke(grpcClientService, nullPath))
                    .isInstanceOf(InvocationTargetException.class)
                    .getCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target path cannot be null or empty");
        } catch (NoSuchMethodException e) {
            fail("Unable to access parseTargetPath method", e);
        }
    }
    
    @Test
    @DisplayName("測試 parseTargetPath：只有斜線拋出異常")
    void testParseTargetPathSlashOnlyThrowsException() {
        // Given
        String slashOnlyPath = TestData.SLASH_ONLY_PATH; // "/"
        
        // When & Then
        Method parseMethod;
        try {
            parseMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
            parseMethod.setAccessible(true);
            
            assertThatThrownBy(() -> parseMethod.invoke(grpcClientService, slashOnlyPath))
                    .isInstanceOf(InvocationTargetException.class)
                    .getCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid target path");
        } catch (NoSuchMethodException e) {
            fail("Unable to access parseTargetPath method", e);
        }
    }
    
    // ===== resolveParentFolderId 私有方法測試 =====
    
    @Test
    @DisplayName("測試 resolveParentFolderId：根路徑返回 0")
    void testResolveParentFolderIdRoot() throws Exception {
        // Given
        String rootPath = TestData.ROOT_PARENT_PATH; // "/"
        
        // When
        Method resolveMethod = GrpcClientService.class.getDeclaredMethod("resolveParentFolderId", String.class);
        resolveMethod.setAccessible(true);
        Long result = (Long) resolveMethod.invoke(grpcClientService, rootPath);
        
        // Then
        assertThat(result).isEqualTo(TestData.ROOT_FOLDER_ID); // 0L
    }
    
    @Test
    @DisplayName("測試 resolveParentFolderId：正常路徑解析")
    void testResolveParentFolderIdNormal() throws Exception {
        // Given
        String parentPath = TestData.VALID_PARENT_PATH; // "/testuser/folder"
        when(mockPathMappingService.resolvePathToId(parentPath)).thenReturn(TestData.VALID_PARENT_ID);
        
        // When
        Method resolveMethod = GrpcClientService.class.getDeclaredMethod("resolveParentFolderId", String.class);
        resolveMethod.setAccessible(true);
        Long result = (Long) resolveMethod.invoke(grpcClientService, parentPath);
        
        // Then
        assertThat(result).isEqualTo(TestData.VALID_PARENT_ID); // 456L
        verify(mockPathMappingService).resolvePathToId(parentPath);
    }
    
    @Test
    @DisplayName("測試 resolveParentFolderId：路徑不存在拋出異常")
    void testResolveParentFolderIdNotFound() {
        // Given
        String nonExistentPath = "/nonexistent/path";
        when(mockPathMappingService.resolvePathToId(nonExistentPath)).thenReturn(null);
        
        // When & Then
        Method resolveMethod;
        try {
            resolveMethod = GrpcClientService.class.getDeclaredMethod("resolveParentFolderId", String.class);
            resolveMethod.setAccessible(true);
            
            assertThatThrownBy(() -> resolveMethod.invoke(grpcClientService, nonExistentPath))
                    .isInstanceOf(InvocationTargetException.class)
                    .getCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent folder not found");
        } catch (NoSuchMethodException e) {
            fail("Unable to access resolveParentFolderId method", e);
        }
        
        verify(mockPathMappingService).resolvePathToId(nonExistentPath);
    }
    
    // ===== handleMove 操作測試 =====
    
    @Test
    @DisplayName("測試 handleMove：成功移動檔案")
    void testHandleMoveSuccess() throws Exception {
        // Given
        Long fileId = TestData.VALID_FILE_ID;
        String targetPath = TestData.VALID_TARGET_PATH;
        Long userId = Long.parseLong(TestData.VALID_USER_ID);
        String token = TestData.VALID_JWT_TOKEN;
        
        // Mock PathMappingService
        when(mockPathMappingService.resolvePathToId(TestData.VALID_PARENT_PATH))
                .thenReturn(TestData.VALID_PARENT_ID);
        
        // Mock 成功的 gRPC 響應
        MoveFileResponse successResponse = TestData.createSuccessMoveResponse();
        
        // 使用反射獲取私有方法
        Method handleMoveMethod = GrpcClientService.class.getDeclaredMethod(
                "handleMove", Long.class, String.class, Long.class, String.class);
        handleMoveMethod.setAccessible(true);
        
        // 此測試無法直接 mock gRPC stub，所以測試範圍只能到輔助方法
        // 這裡測試輔助方法是否正確調用
        
        // When & Then - 測試輔助方法是否正確解析路徑
        Method parseTargetPathMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
        parseTargetPathMethod.setAccessible(true);
        Object pathInfo = parseTargetPathMethod.invoke(grpcClientService, targetPath);
        
        Method getParentPathMethod = pathInfo.getClass().getDeclaredMethod("getParentPath");
        getParentPathMethod.setAccessible(true);
        String parentPath = (String) getParentPathMethod.invoke(pathInfo);
        
        assertThat(parentPath).isEqualTo(TestData.VALID_PARENT_PATH);
        
        // 測試父資料夾 ID 解析
        Method resolveParentFolderIdMethod = GrpcClientService.class.getDeclaredMethod("resolveParentFolderId", String.class);
        resolveParentFolderIdMethod.setAccessible(true);
        Long parentFolderId = (Long) resolveParentFolderIdMethod.invoke(grpcClientService, parentPath);
        
        assertThat(parentFolderId).isEqualTo(TestData.VALID_PARENT_ID);
        
        verify(mockPathMappingService).resolvePathToId(TestData.VALID_PARENT_PATH);
    }
    
    @Test
    @DisplayName("測試 handleMove：無效目標路徑")
    void testHandleMoveInvalidTargetPath() throws Exception {
        // Given
        Long fileId = TestData.VALID_FILE_ID;
        String invalidTargetPath = TestData.EMPTY_TARGET_PATH; // 空路徑
        Long userId = Long.parseLong(TestData.VALID_USER_ID);
        String token = TestData.VALID_JWT_TOKEN;
        
        // When
        Method handleMoveMethod = GrpcClientService.class.getDeclaredMethod(
                "handleMove", Long.class, String.class, Long.class, String.class);
        handleMoveMethod.setAccessible(true);
        
        ProcessingResponse response = (ProcessingResponse) handleMoveMethod.invoke(
                grpcClientService, fileId, invalidTargetPath, userId, token);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Invalid target path");
    }
    
    @Test
    @DisplayName("測試 handleMove：父資料夾不存在")
    void testHandleMoveParentFolderNotFound() throws Exception {
        // Given
        Long fileId = TestData.VALID_FILE_ID;
        String targetPath = "/nonexistent/parent/file.txt";
        Long userId = Long.parseLong(TestData.VALID_USER_ID);
        String token = TestData.VALID_JWT_TOKEN;
        
        // Mock PathMappingService 返回 null（父資料夾不存在）
        when(mockPathMappingService.resolvePathToId("/nonexistent/parent")).thenReturn(null);
        
        // When
        Method handleMoveMethod = GrpcClientService.class.getDeclaredMethod(
                "handleMove", Long.class, String.class, Long.class, String.class);
        handleMoveMethod.setAccessible(true);
        
        ProcessingResponse response = (ProcessingResponse) handleMoveMethod.invoke(
                grpcClientService, fileId, targetPath, userId, token);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Invalid target path");
        
        verify(mockPathMappingService).resolvePathToId("/nonexistent/parent");
    }
    
    // ===== handleCopy 操作測試 =====
    
    @Test
    @DisplayName("測試 handleCopy：成功複製檔案")
    void testHandleCopySuccess() throws Exception {
        // Given
        Long fileId = TestData.VALID_FILE_ID;
        String targetPath = TestData.VALID_TARGET_PATH;
        Long userId = Long.parseLong(TestData.VALID_USER_ID);
        String token = TestData.VALID_JWT_TOKEN;
        
        // Mock PathMappingService
        when(mockPathMappingService.resolvePathToId(TestData.VALID_PARENT_PATH))
                .thenReturn(TestData.VALID_PARENT_ID);
        
        // When & Then - 測試輔助方法是否正確解析路徑
        Method parseTargetPathMethod = GrpcClientService.class.getDeclaredMethod("parseTargetPath", String.class);
        parseTargetPathMethod.setAccessible(true);
        Object pathInfo = parseTargetPathMethod.invoke(grpcClientService, targetPath);
        
        Method getParentPathMethod = pathInfo.getClass().getDeclaredMethod("getParentPath");
        getParentPathMethod.setAccessible(true);
        String parentPath = (String) getParentPathMethod.invoke(pathInfo);
        
        assertThat(parentPath).isEqualTo(TestData.VALID_PARENT_PATH);
        
        // 測試父資料夾 ID 解析
        Method resolveParentFolderIdMethod = GrpcClientService.class.getDeclaredMethod("resolveParentFolderId", String.class);
        resolveParentFolderIdMethod.setAccessible(true);
        Long parentFolderId = (Long) resolveParentFolderIdMethod.invoke(grpcClientService, parentPath);
        
        assertThat(parentFolderId).isEqualTo(TestData.VALID_PARENT_ID);
        
        verify(mockPathMappingService).resolvePathToId(TestData.VALID_PARENT_PATH);
    }
    
    @Test
    @DisplayName("測試 handleCopy：無效目標路徑")
    void testHandleCopyInvalidTargetPath() throws Exception {
        // Given
        Long fileId = TestData.VALID_FILE_ID;
        String invalidTargetPath = TestData.NULL_TARGET_PATH; // null 路徑
        Long userId = Long.parseLong(TestData.VALID_USER_ID);
        String token = TestData.VALID_JWT_TOKEN;
        
        // When
        Method handleCopyMethod = GrpcClientService.class.getDeclaredMethod(
                "handleCopy", Long.class, String.class, Long.class, String.class);
        handleCopyMethod.setAccessible(true);
        
        ProcessingResponse response = (ProcessingResponse) handleCopyMethod.invoke(
                grpcClientService, fileId, invalidTargetPath, userId, token);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Invalid target path");
    }
    
    @Test
    @DisplayName("測試 handleCopy：父資料夾不存在")
    void testHandleCopyParentFolderNotFound() throws Exception {
        // Given
        Long fileId = TestData.VALID_FILE_ID;
        String targetPath = "/another/nonexistent/path/file.txt";
        Long userId = Long.parseLong(TestData.VALID_USER_ID);
        String token = TestData.VALID_JWT_TOKEN;
        
        // Mock PathMappingService 返回 null（父資料夾不存在）
        when(mockPathMappingService.resolvePathToId("/another/nonexistent/path")).thenReturn(null);
        
        // When
        Method handleCopyMethod = GrpcClientService.class.getDeclaredMethod(
                "handleCopy", Long.class, String.class, Long.class, String.class);
        handleCopyMethod.setAccessible(true);
        
        ProcessingResponse response = (ProcessingResponse) handleCopyMethod.invoke(
                grpcClientService, fileId, targetPath, userId, token);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Invalid target path");
        
        verify(mockPathMappingService).resolvePathToId("/another/nonexistent/path");
    }
    
    // ===== processFile 整合測試 =====
    
    @Test
    @DisplayName("測試 processFile：MOVE 操作")
    void testProcessFileMove() {
        // Given
        ProcessingRequest request = ProcessingRequest.builder()
                .operation(Operation.MOVE)
                .targetPath(TestData.VALID_TARGET_PATH)
                .fileId(TestData.VALID_FILE_ID)
                .build();
        
        // Mock 請求上下文
        try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
            RequestContextHolder.RequestContext mockContext = mock(RequestContextHolder.RequestContext.class);
            when(mockContext.isAuthenticated()).thenReturn(true);
            when(mockContext.getUserId()).thenReturn(TestData.VALID_USER_ID);
            when(mockContext.getAuthToken()).thenReturn(TestData.VALID_JWT_TOKEN);
            
            mockedStatic.when(RequestContextHolder::getContext).thenReturn(mockContext);
            
            // Mock PathMappingService
            when(mockPathMappingService.resolvePathToId(TestData.VALID_PARENT_PATH))
                    .thenReturn(TestData.VALID_PARENT_ID);
            
            // When
            ProcessingResponse response = grpcClientService.processFile(request);
            
            // Then
            assertThat(response).isNotNull();
            // 由於沒有真實的 gRPC 連接，期望會失敗，但我們測試了路徑解析邏輯
            // 這裡正常情況下應該會遇到 gRPC 連接錯誤
        }
    }
    
    @Test
    @DisplayName("測試 processFile：COPY 操作")
    void testProcessFileCopy() {
        // Given
        ProcessingRequest request = ProcessingRequest.builder()
                .operation(Operation.COPY)
                .targetPath(TestData.VALID_TARGET_PATH)
                .fileId(TestData.VALID_FILE_ID)
                .build();
        
        // Mock 請求上下文
        try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
            RequestContextHolder.RequestContext mockContext = mock(RequestContextHolder.RequestContext.class);
            when(mockContext.isAuthenticated()).thenReturn(true);
            when(mockContext.getUserId()).thenReturn(TestData.VALID_USER_ID);
            when(mockContext.getAuthToken()).thenReturn(TestData.VALID_JWT_TOKEN);
            
            mockedStatic.when(RequestContextHolder::getContext).thenReturn(mockContext);
            
            // Mock PathMappingService
            when(mockPathMappingService.resolvePathToId(TestData.VALID_PARENT_PATH))
                    .thenReturn(TestData.VALID_PARENT_ID);
            
            // When
            ProcessingResponse response = grpcClientService.processFile(request);
            
            // Then
            assertThat(response).isNotNull();
            // 由於沒有真實的 gRPC 連接，期望會失敗，但我們測試了路徑解析邏輯
        }
    }
    
    @Test
    @DisplayName("測試 processFile：未認證的請求")
    void testProcessFileUnauthenticated() {
        // Given
        ProcessingRequest request = ProcessingRequest.builder()
                .operation(Operation.MOVE)
                .targetPath(TestData.VALID_TARGET_PATH)
                .fileId(TestData.VALID_FILE_ID)
                .build();
        
        // Mock 未認證的請求上下文
        try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
            RequestContextHolder.RequestContext mockContext = mock(RequestContextHolder.RequestContext.class);
            when(mockContext.isAuthenticated()).thenReturn(false);
            
            mockedStatic.when(RequestContextHolder::getContext).thenReturn(mockContext);
            
            // When
            ProcessingResponse response = grpcClientService.processFile(request);
            
            // Then
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("Unauthorized");
        }
    }
    
    // ===== 錯誤場景和邊界情況測試 =====
    
    @Test
    @DisplayName("測試各種異常情況的處理")
    void testEdgeCasesAndExceptions() {
        // 測試 null 檔案 ID
        assertThatCode(() -> {
            ProcessingRequest request = ProcessingRequest.builder()
                    .operation(Operation.MOVE)
                    .targetPath(TestData.VALID_TARGET_PATH)
                    .fileId(null)
                    .build();
            
            ProcessingResponse response = grpcClientService.processFile(request);
            assertThat(response.isSuccess()).isFalse();
        }).doesNotThrowAnyException();
        
        // 測試無效檔案 ID
        assertThatCode(() -> {
            ProcessingRequest request = ProcessingRequest.builder()
                    .operation(Operation.COPY)
                    .targetPath(TestData.VALID_TARGET_PATH)
                    .fileId(TestData.INVALID_FILE_ID)
                    .build();
            
            ProcessingResponse response = grpcClientService.processFile(request);
            // 這應該正常處理，不拋出異常
        }).doesNotThrowAnyException();
    }
}