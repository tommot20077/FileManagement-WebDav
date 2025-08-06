package dowob.xyz.filemanagementwebdav.component.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebDavToggleService 單元測試
 * 
 * 測試 WebDAV 服務開關管理的核心功能，包括服務狀態切換、重置和狀態查詢。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavToggleServiceTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("WebDavToggleService 測試")
class WebDavToggleServiceTest {
    
    private WebDavToggleService toggleService;
    
    // ===== 測試用常量 =====
    
    private static final String TEST_REASON = "Unit test reason";
    private static final String ENABLE_REASON = "Enable for testing";
    private static final String DISABLE_REASON = "Disable for maintenance";
    private static final String TOGGLE_REASON = "Toggle state for testing";
    private static final String RESET_REASON = "Reset to default state";
    
    // ===== 初始化方法 =====
    
    @BeforeEach
    void setUp() {
        // WebDAV 專用子服務中服務總是啟用
        toggleService = new WebDavToggleService();
    }
    
    // ===== 構造函數和初始化測試 =====
    
    @Test
    @DisplayName("測試 WebDAV 專用子服務初始化")
    void testServiceInitializationAlwaysEnabled() {
        // Given & When
        WebDavToggleService service = new WebDavToggleService();
        
        // Then - WebDAV 專用子服務總是啟用
        assertThat(service.isServiceEnabled()).isTrue();
        assertThat(service.isServiceAvailable()).isTrue();
        assertThat(service.getServiceStatus()).contains("Current: ENABLED", "Default: ENABLED");
    }
    
    // ===== 服務狀態檢查測試 =====
    
    @Test
    @DisplayName("測試檢查服務是否啟用")
    void testIsServiceEnabled() {
        // Then - 初始狀態應該為啟用
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When - 禁用服務
        toggleService.disableService(DISABLE_REASON);
        
        // Then - 狀態應該為禁用
        assertThat(toggleService.isServiceEnabled()).isFalse();
    }
    
    @Test
    @DisplayName("測試檢查服務是否可用")
    void testIsServiceAvailable() {
        // Then - 初始狀態應該為可用
        assertThat(toggleService.isServiceAvailable()).isTrue();
        
        // When - 禁用服務
        toggleService.disableService(DISABLE_REASON);
        
        // Then - 狀態應該為不可用
        assertThat(toggleService.isServiceAvailable()).isFalse();
    }
    
    // ===== 啟用服務測試 =====
    
    @Test
    @DisplayName("測試啟用已禁用的服務")
    void testEnableDisabledService() {
        // Given - 先禁用服務
        toggleService.disableService(DISABLE_REASON);
        assertThat(toggleService.isServiceEnabled()).isFalse();
        
        // When - 啟用服務
        WebDavToggleService.ToggleResult result = toggleService.enableService(ENABLE_REASON);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Service enabled successfully");
        assertThat(toggleService.isServiceEnabled()).isTrue();
        assertThat(toggleService.isServiceAvailable()).isTrue();
    }
    
    @Test
    @DisplayName("測試啟用已啟用的服務")
    void testEnableAlreadyEnabledService() {
        // Given - 服務已經啟用
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When - 再次啟用服務
        WebDavToggleService.ToggleResult result = toggleService.enableService(ENABLE_REASON);
        
        // Then - 應該返回無變化結果
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Service was already enabled");
        assertThat(toggleService.isServiceEnabled()).isTrue();
    }
    
    @Test
    @DisplayName("測試啟用服務時傳入 null 原因")
    void testEnableServiceWithNullReason() {
        // Given - 先禁用服務
        toggleService.disableService(DISABLE_REASON);
        
        // When - 使用 null 原因啟用服務
        WebDavToggleService.ToggleResult result = toggleService.enableService(null);
        
        // Then - 應該正常工作
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isTrue();
    }
    
    @Test
    @DisplayName("測試啟用服務時傳入空字符串原因")
    void testEnableServiceWithEmptyReason() {
        // Given - 先禁用服務
        toggleService.disableService(DISABLE_REASON);
        
        // When - 使用空字符串原因啟用服務
        WebDavToggleService.ToggleResult result = toggleService.enableService("");
        
        // Then - 應該正常工作
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isTrue();
    }
    
    // ===== 禁用服務測試 =====
    
    @Test
    @DisplayName("測試禁用已啟用的服務")
    void testDisableEnabledService() {
        // Given - 服務已啟用
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When - 禁用服務
        WebDavToggleService.ToggleResult result = toggleService.disableService(DISABLE_REASON);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Service disabled successfully");
        assertThat(toggleService.isServiceEnabled()).isFalse();
        assertThat(toggleService.isServiceAvailable()).isFalse();
    }
    
    @Test
    @DisplayName("測試禁用已禁用的服務")
    void testDisableAlreadyDisabledService() {
        // Given - 先禁用服務
        toggleService.disableService(DISABLE_REASON);
        assertThat(toggleService.isServiceEnabled()).isFalse();
        
        // When - 再次禁用服務
        WebDavToggleService.ToggleResult result = toggleService.disableService(DISABLE_REASON);
        
        // Then - 應該返回無變化結果
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Service was already disabled");
        assertThat(toggleService.isServiceEnabled()).isFalse();
    }
    
    @Test
    @DisplayName("測試禁用服務時傳入 null 原因")
    void testDisableServiceWithNullReason() {
        // When - 使用 null 原因禁用服務
        WebDavToggleService.ToggleResult result = toggleService.disableService(null);
        
        // Then - 應該正常工作
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isFalse();
    }
    
    // ===== 切換服務測試 =====
    
    @Test
    @DisplayName("測試從啟用狀態切換到禁用")
    void testToggleFromEnabledToDisabled() {
        // Given - 服務已啟用
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When - 切換服務狀態
        WebDavToggleService.ToggleResult result = toggleService.toggleService(TOGGLE_REASON);
        
        // Then - 應該禁用服務
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Service disabled successfully");
        assertThat(toggleService.isServiceEnabled()).isFalse();
    }
    
    @Test
    @DisplayName("測試從禁用狀態切換到啟用")
    void testToggleFromDisabledToEnabled() {
        // Given - 先禁用服務
        toggleService.disableService(DISABLE_REASON);
        assertThat(toggleService.isServiceEnabled()).isFalse();
        
        // When - 切換服務狀態
        WebDavToggleService.ToggleResult result = toggleService.toggleService(TOGGLE_REASON);
        
        // Then - 應該啟用服務
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Service enabled successfully");
        assertThat(toggleService.isServiceEnabled()).isTrue();
    }
    
    @Test
    @DisplayName("測試多次切換服務狀態")
    void testMultipleToggle() {
        // Given - 初始狀態為啟用
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When & Then - 第一次切換（禁用）
        WebDavToggleService.ToggleResult result1 = toggleService.toggleService(TOGGLE_REASON);
        assertThat(result1.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isFalse();
        
        // When & Then - 第二次切換（啟用）
        WebDavToggleService.ToggleResult result2 = toggleService.toggleService(TOGGLE_REASON);
        assertThat(result2.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When & Then - 第三次切換（禁用）
        WebDavToggleService.ToggleResult result3 = toggleService.toggleService(TOGGLE_REASON);
        assertThat(result3.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isFalse();
    }
    
    // ===== 重置到默認狀態測試 =====
    
    @Test
    @DisplayName("測試重置到默認狀態 - 當前狀態與默認狀態不同")
    void testResetToDefaultWhenDifferent() {
        // Given - 默認啟用，但當前禁用
        toggleService.disableService(DISABLE_REASON);
        assertThat(toggleService.isServiceEnabled()).isFalse();
        
        // When - 重置到默認狀態
        WebDavToggleService.ToggleResult result = toggleService.resetToDefault(RESET_REASON);
        
        // Then - 應該恢復到啟用狀態
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Service reset to default state: true");
        assertThat(toggleService.isServiceEnabled()).isTrue();
    }
    
    @Test
    @DisplayName("測試重置到默認狀態 - 當前狀態與默認狀態相同")
    void testResetToDefaultWhenSame() {
        // Given - 默認啟用，當前也啟用
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When - 重置到默認狀態
        WebDavToggleService.ToggleResult result = toggleService.resetToDefault(RESET_REASON);
        
        // Then - 應該無變化
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Service state already matches default");
        assertThat(toggleService.isServiceEnabled()).isTrue();
    }
    
    @Test
    @DisplayName("測試 WebDAV 專用子服務的重置功能")
    void testResetToDefaultInDedicatedService() {
        // Given - WebDAV 專用子服務
        WebDavToggleService service = new WebDavToggleService();
        
        // 先禁用服務（運行時操作）
        service.disableService(DISABLE_REASON);
        assertThat(service.isServiceEnabled()).isFalse();
        
        // When - 重置到默認狀態
        WebDavToggleService.ToggleResult result = service.resetToDefault(RESET_REASON);
        
        // Then - 專用子服務默認總是啟用
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Service reset to default state: true");
        assertThat(service.isServiceEnabled()).isTrue();
    }
    
    // ===== 狀態查詢測試 =====
    
    @Test
    @DisplayName("測試獲取服務狀態信息 - 服務啟用")
    void testGetServiceStatusWhenEnabled() {
        // Given - 服務已啟用
        assertThat(toggleService.isServiceEnabled()).isTrue();
        
        // When
        String status = toggleService.getServiceStatus();
        
        // Then
        assertThat(status).isEqualTo("WebDAV Service - Current: ENABLED, Default: ENABLED");
    }
    
    @Test
    @DisplayName("測試獲取服務狀態信息 - 服務禁用")
    void testGetServiceStatusWhenDisabled() {
        // Given - 禁用服務
        toggleService.disableService(DISABLE_REASON);
        
        // When
        String status = toggleService.getServiceStatus();
        
        // Then
        assertThat(status).isEqualTo("WebDAV Service - Current: DISABLED, Default: ENABLED");
    }
    
    @Test
    @DisplayName("測試獲取 WebDAV 專用子服務狀態信息")
    void testGetServiceStatusInDedicatedService() {
        // Given - WebDAV 專用子服務
        WebDavToggleService service = new WebDavToggleService();
        
        // When
        String status = service.getServiceStatus();
        
        // Then - 專用子服務默認總是啟用
        assertThat(status).isEqualTo("WebDAV Service - Current: ENABLED, Default: ENABLED");
    }
    
    @Test
    @DisplayName("測試獲取服務不可用訊息")
    void testGetServiceUnavailableMessage() {
        // When
        String message = toggleService.getServiceUnavailableMessage();
        
        // Then
        assertThat(message).isEqualTo("WebDAV service is currently disabled. Please contact the administrator.");
    }
    
    // ===== ToggleResult 內部類測試 =====
    
    @Test
    @DisplayName("測試 ToggleResult.success() 創建")
    void testToggleResultSuccess() {
        // Given
        String message = "Operation successful";
        
        // When
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success(message);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(result.getMessage()).isEqualTo(message);
    }
    
    @Test
    @DisplayName("測試 ToggleResult.noChange() 創建")
    void testToggleResultNoChange() {
        // Given
        String message = "No change required";
        
        // When
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.noChange(message);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isFalse();
        assertThat(result.getMessage()).isEqualTo(message);
    }
    
    @Test
    @DisplayName("測試 ToggleResult.error() 創建")
    void testToggleResultError() {
        // Given
        String message = "Operation failed";
        
        // When
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.error(message);
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isChanged()).isFalse();
        assertThat(result.getMessage()).isEqualTo(message);
    }
    
    // ===== 併發測試 =====
    
    @Test
    @DisplayName("測試併發切換操作")
    void testConcurrentToggleOperations() throws InterruptedException {
        // Given
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        // When - 創建多個線程同時切換服務狀態
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    toggleService.toggleService("Concurrent test");
                }
            });
            threads[i].start();
        }
        
        // 等待所有線程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then - 服務狀態應該仍然有效（啟用或禁用）
        boolean finalState = toggleService.isServiceEnabled();
        assertThat(finalState).isIn(true, false); // 狀態應該是明確的 true 或 false
        
        // 服務可用性應該與狀態一致
        assertThat(toggleService.isServiceAvailable()).isEqualTo(finalState);
    }
    
    @Test
    @DisplayName("測試併發啟用和禁用操作")
    void testConcurrentEnableDisableOperations() throws InterruptedException {
        // Given
        int threadCount = 20;
        Thread[] threads = new Thread[threadCount];
        
        // When - 一半線程啟用，一半線程禁用
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    if (threadIndex % 2 == 0) {
                        toggleService.enableService("Concurrent enable test");
                    } else {
                        toggleService.disableService("Concurrent disable test");
                    }
                }
            });
            threads[i].start();
        }
        
        // 等待所有線程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then - 服務狀態應該仍然有效且一致
        boolean finalState = toggleService.isServiceEnabled();
        assertThat(finalState).isIn(true, false);
        assertThat(toggleService.isServiceAvailable()).isEqualTo(finalState);
        
        // 狀態信息應該包含最終狀態
        String status = toggleService.getServiceStatus();
        String expectedCurrentState = finalState ? "ENABLED" : "DISABLED";
        assertThat(status).contains("Current: " + expectedCurrentState);
    }
    
    // ===== 邊界情況測試 =====
    
    @Test
    @DisplayName("測試極長的原因字符串")
    void testVeryLongReason() {
        // Given
        String longReason = "A".repeat(10000); // 10,000 字符的長字符串
        
        // When & Then - 應該正常處理，不拋出異常
        WebDavToggleService.ToggleResult result = toggleService.disableService(longReason);
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isFalse();
    }
    
    @Test
    @DisplayName("測試包含特殊字符的原因")
    void testSpecialCharactersInReason() {
        // Given
        String specialReason = "原因包含特殊字符: 中文、emoji 🚀、引號\"和換行符\n測試";
        
        // When & Then - 應該正常處理
        WebDavToggleService.ToggleResult result = toggleService.disableService(specialReason);
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isChanged()).isTrue();
        assertThat(toggleService.isServiceEnabled()).isFalse();
    }
}