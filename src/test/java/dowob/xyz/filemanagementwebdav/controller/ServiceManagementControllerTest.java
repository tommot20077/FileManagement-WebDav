package dowob.xyz.filemanagementwebdav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dowob.xyz.filemanagementwebdav.component.service.WebDavToggleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ServiceManagementController 單元測試
 * 
 * 測試服務管理控制器的所有端點，包括服務開關控制、狀態查看和健康檢查功能。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName ServiceManagementControllerTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceManagementController 測試")
class ServiceManagementControllerTest {
    
    @Mock
    private WebDavToggleService mockToggleService;
    
    @InjectMocks
    private ServiceManagementController serviceController;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    // ===== 測試用常量 =====
    
    private static final String BASE_URL = "/api/service";
    private static final String TEST_REASON = "Unit test reason";
    private static final String SERVICE_STATUS_INFO = "WebDAV Service - Current: ENABLED, Default: ENABLED";
    
    // ===== 初始化方法 =====
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serviceController).build();
        objectMapper = new ObjectMapper();
        
        // 重置所有 mocks
        reset(mockToggleService);
    }
    
    // ===== 獲取服務狀態測試 =====
    
    @Test
    @DisplayName("測試獲取服務狀態 - 服務已啟用")
    void testGetServiceStatusEnabled() throws Exception {
        // Given
        when(mockToggleService.isServiceEnabled()).thenReturn(true);
        when(mockToggleService.isServiceAvailable()).thenReturn(true);
        when(mockToggleService.getServiceStatus()).thenReturn(SERVICE_STATUS_INFO);
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.details").value(SERVICE_STATUS_INFO));
        
        verify(mockToggleService).isServiceEnabled();
        verify(mockToggleService).isServiceAvailable();
        verify(mockToggleService).getServiceStatus();
    }
    
    @Test
    @DisplayName("測試獲取服務狀態 - 服務已禁用")
    void testGetServiceStatusDisabled() throws Exception {
        // Given
        when(mockToggleService.isServiceEnabled()).thenReturn(false);
        when(mockToggleService.isServiceAvailable()).thenReturn(false);
        when(mockToggleService.getServiceStatus()).thenReturn("WebDAV Service - Current: DISABLED, Default: ENABLED");
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.details").value("WebDAV Service - Current: DISABLED, Default: ENABLED"));
        
        verify(mockToggleService).isServiceEnabled();
        verify(mockToggleService).isServiceAvailable();
        verify(mockToggleService).getServiceStatus();
    }
    
    @Test
    @DisplayName("測試獲取服務狀態時發生異常")
    void testGetServiceStatusException() throws Exception {
        // Given
        when(mockToggleService.isServiceEnabled()).thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("獲取服務狀態失敗")));
        
        verify(mockToggleService).isServiceEnabled();
    }
    
    // ===== 啟用服務測試 =====
    
    @Test
    @DisplayName("測試啟用服務成功 - 有變化")
    void testEnableServiceSuccessWithChange() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service enabled successfully");
        when(mockToggleService.enableService(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service enabled successfully"))
                .andExpect(jsonPath("$.changed").value("true"));
        
        verify(mockToggleService).enableService(TEST_REASON);
    }
    
    @Test
    @DisplayName("測試啟用服務成功 - 無變化")
    void testEnableServiceSuccessNoChange() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.noChange("Service was already enabled");
        when(mockToggleService.enableService(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service was already enabled"))
                .andExpect(jsonPath("$.changed").value("false"));
        
        verify(mockToggleService).enableService(TEST_REASON);
    }
    
    @Test
    @DisplayName("測試啟用服務時沒有請求體")
    void testEnableServiceWithoutRequestBody() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service enabled successfully");
        when(mockToggleService.enableService(null)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service enabled successfully"))
                .andExpect(jsonPath("$.changed").value("true"));
        
        verify(mockToggleService).enableService(null);
    }
    
    @Test
    @DisplayName("測試啟用服務時沒有reason字段")
    void testEnableServiceWithoutReasonField() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service enabled successfully");
        when(mockToggleService.enableService(null)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service enabled successfully"))
                .andExpect(jsonPath("$.changed").value("true"));
        
        verify(mockToggleService).enableService(null);
    }
    
    @Test
    @DisplayName("測試啟用服務失敗")
    void testEnableServiceFailure() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.error("Cannot enable service");
        when(mockToggleService.enableService(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Cannot enable service"));
        
        verify(mockToggleService).enableService(TEST_REASON);
    }
    
    @Test
    @DisplayName("測試啟用服務時發生異常")
    void testEnableServiceException() throws Exception {
        // Given
        when(mockToggleService.enableService(TEST_REASON)).thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("啟用服務失敗")));
        
        verify(mockToggleService).enableService(TEST_REASON);
    }
    
    // ===== 禁用服務測試 =====
    
    @Test
    @DisplayName("測試禁用服務成功 - 有變化")
    void testDisableServiceSuccessWithChange() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service disabled successfully");
        when(mockToggleService.disableService(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service disabled successfully"))
                .andExpect(jsonPath("$.changed").value("true"));
        
        verify(mockToggleService).disableService(TEST_REASON);
    }
    
    @Test
    @DisplayName("測試禁用服務成功 - 無變化")
    void testDisableServiceSuccessNoChange() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.noChange("Service was already disabled");
        when(mockToggleService.disableService(null)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/disable"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service was already disabled"))
                .andExpect(jsonPath("$.changed").value("false"));
        
        verify(mockToggleService).disableService(null);
    }
    
    @Test
    @DisplayName("測試禁用服務失敗")
    void testDisableServiceFailure() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.error("Cannot disable service");
        when(mockToggleService.disableService(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Cannot disable service"));
        
        verify(mockToggleService).disableService(TEST_REASON);
    }
    
    @Test
    @DisplayName("測試禁用服務時發生異常")
    void testDisableServiceException() throws Exception {
        // Given
        when(mockToggleService.disableService(TEST_REASON)).thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("禁用服務失敗")));
        
        verify(mockToggleService).disableService(TEST_REASON);
    }
    
    // ===== 切換服務測試 =====
    
    @Test
    @DisplayName("測試切換服務成功 - 從禁用到啟用")
    void testToggleServiceFromDisabledToEnabled() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service enabled successfully");
        when(mockToggleService.toggleService(TEST_REASON)).thenReturn(result);
        when(mockToggleService.isServiceEnabled()).thenReturn(true);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/toggle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service enabled successfully"))
                .andExpect(jsonPath("$.changed").value("true"))
                .andExpect(jsonPath("$.currentState").value("ENABLED"));
        
        verify(mockToggleService).toggleService(TEST_REASON);
        verify(mockToggleService).isServiceEnabled();
    }
    
    @Test
    @DisplayName("測試切換服務成功 - 從啟用到禁用")
    void testToggleServiceFromEnabledToDisabled() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service disabled successfully");
        when(mockToggleService.toggleService(TEST_REASON)).thenReturn(result);
        when(mockToggleService.isServiceEnabled()).thenReturn(false);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/toggle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service disabled successfully"))
                .andExpect(jsonPath("$.changed").value("true"))
                .andExpect(jsonPath("$.currentState").value("DISABLED"));
        
        verify(mockToggleService).toggleService(TEST_REASON);
        verify(mockToggleService).isServiceEnabled();
    }
    
    @Test
    @DisplayName("測試切換服務時沒有請求體")
    void testToggleServiceWithoutRequestBody() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service disabled successfully");
        when(mockToggleService.toggleService(null)).thenReturn(result);
        when(mockToggleService.isServiceEnabled()).thenReturn(false);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/toggle"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service disabled successfully"))
                .andExpect(jsonPath("$.changed").value("true"))
                .andExpect(jsonPath("$.currentState").value("DISABLED"));
        
        verify(mockToggleService).toggleService(null);
        verify(mockToggleService).isServiceEnabled();
    }
    
    @Test
    @DisplayName("測試切換服務失敗")
    void testToggleServiceFailure() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.error("Cannot toggle service");
        when(mockToggleService.toggleService(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/toggle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Cannot toggle service"));
        
        verify(mockToggleService).toggleService(TEST_REASON);
        verify(mockToggleService, never()).isServiceEnabled();
    }
    
    @Test
    @DisplayName("測試切換服務時發生異常")
    void testToggleServiceException() throws Exception {
        // Given
        when(mockToggleService.toggleService(TEST_REASON)).thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/toggle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("切換服務狀態失敗")));
        
        verify(mockToggleService).toggleService(TEST_REASON);
    }
    
    // ===== 重置服務測試 =====
    
    @Test
    @DisplayName("測試重置服務成功 - 有變化")
    void testResetServiceSuccessWithChange() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service reset to default state: true");
        when(mockToggleService.resetToDefault(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service reset to default state: true"))
                .andExpect(jsonPath("$.changed").value("true"));
        
        verify(mockToggleService).resetToDefault(TEST_REASON);
    }
    
    @Test
    @DisplayName("測試重置服務成功 - 無變化")
    void testResetServiceSuccessNoChange() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.noChange("Service state already matches default");
        when(mockToggleService.resetToDefault(null)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/reset"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service state already matches default"))
                .andExpect(jsonPath("$.changed").value("false"));
        
        verify(mockToggleService).resetToDefault(null);
    }
    
    @Test
    @DisplayName("測試重置服務失敗")
    void testResetServiceFailure() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.error("Cannot reset service");
        when(mockToggleService.resetToDefault(TEST_REASON)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Cannot reset service"));
        
        verify(mockToggleService).resetToDefault(TEST_REASON);
    }
    
    @Test
    @DisplayName("測試重置服務時發生異常")
    void testResetServiceException() throws Exception {
        // Given
        when(mockToggleService.resetToDefault(TEST_REASON)).thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", TEST_REASON))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("重置服務失敗")));
        
        verify(mockToggleService).resetToDefault(TEST_REASON);
    }
    
    // ===== 健康檢查測試 =====
    
    @Test
    @DisplayName("測試健康檢查 - 服務可用")
    void testHealthCheckServiceAvailable() throws Exception {
        // Given
        when(mockToggleService.isServiceAvailable()).thenReturn(true);
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
        
        verify(mockToggleService).isServiceAvailable();
    }
    
    @Test
    @DisplayName("測試健康檢查 - 服務不可用")
    void testHealthCheckServiceUnavailable() throws Exception {
        // Given
        when(mockToggleService.isServiceAvailable()).thenReturn(false);
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.timestamp").exists());
        
        verify(mockToggleService).isServiceAvailable();
    }
    
    @Test
    @DisplayName("測試健康檢查時發生異常")
    void testHealthCheckException() throws Exception {
        // Given
        when(mockToggleService.isServiceAvailable()).thenThrow(new RuntimeException("Health check failed"));
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.error").value("Health check failed"))
                .andExpect(jsonPath("$.timestamp").exists());
        
        verify(mockToggleService).isServiceAvailable();
    }
    
    // ===== 邊界情況和錯誤處理測試 =====
    
    @Test
    @DisplayName("測試無效JSON請求")
    void testInvalidJsonRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("測試不支持的HTTP方法")
    void testUnsupportedHttpMethod() throws Exception {
        // When & Then
        mockMvc.perform(put(BASE_URL + "/status"))
                .andExpect(status().isMethodNotAllowed());
    }
    
    @Test
    @DisplayName("測試特殊字符reason處理")
    void testSpecialCharacterReason() throws Exception {
        // Given
        String specialReason = "原因包含特殊字符: 中文、emoji 🚀、引號\"和換行符\n測試";
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service enabled successfully");
        when(mockToggleService.enableService(specialReason)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", specialReason))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service enabled successfully"));
        
        verify(mockToggleService).enableService(specialReason);
    }
    
    @Test
    @DisplayName("測試極長reason字符串")
    void testVeryLongReason() throws Exception {
        // Given
        String longReason = "A".repeat(10000);
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service enabled successfully");
        when(mockToggleService.enableService(longReason)).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", longReason))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service enabled successfully"));
        
        verify(mockToggleService).enableService(longReason);
    }
    
    @Test
    @DisplayName("測試空字符串reason")
    void testEmptyStringReason() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service disabled successfully");
        when(mockToggleService.disableService("")).thenReturn(result);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", ""))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service disabled successfully"));
        
        verify(mockToggleService).disableService("");
    }
    
    @Test
    @DisplayName("測試多個字段的請求體")
    void testRequestBodyWithMultipleFields() throws Exception {
        // Given
        WebDavToggleService.ToggleResult result = WebDavToggleService.ToggleResult.success("Service toggled successfully");
        when(mockToggleService.toggleService(TEST_REASON)).thenReturn(result);
        when(mockToggleService.isServiceEnabled()).thenReturn(true);
        
        Map<String, String> requestBody = Map.of(
            "reason", TEST_REASON,
            "extraField", "extraValue",
            "anotherField", "anotherValue"
        );
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/toggle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Service toggled successfully"))
                .andExpect(jsonPath("$.currentState").value("ENABLED"));
        
        verify(mockToggleService).toggleService(TEST_REASON);
        verify(mockToggleService).isServiceEnabled();
    }
}