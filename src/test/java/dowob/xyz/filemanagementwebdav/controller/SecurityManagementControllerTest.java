package dowob.xyz.filemanagementwebdav.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dowob.xyz.filemanagementwebdav.component.security.CommonSecurityService;
import dowob.xyz.filemanagementwebdav.component.security.IpWhitelistService;
import dowob.xyz.filemanagementwebdav.component.security.RateLimitService;
import dowob.xyz.filemanagementwebdav.component.security.SecurityAuditService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/**
 * SecurityManagementController 單元測試
 * 
 * 測試安全管理控制器的所有端點，包括IP白名單/黑名單管理、頻率限制管理、
 * 安全統計查看和安全檢查功能。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName SecurityManagementControllerTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityManagementController 測試")
class SecurityManagementControllerTest {
    
    @Mock
    private CommonSecurityService mockSecurityService;
    
    @Mock
    private IpWhitelistService mockIpWhitelistService;
    
    @Mock
    private RateLimitService mockRateLimitService;
    
    @Mock
    private SecurityAuditService mockAuditService;
    
    @InjectMocks
    private SecurityManagementController securityController;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    // ===== 測試用常量 =====
    
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_KEY = "ip:192.168.1.100";
    private static final String BASE_URL = "/api/security";
    
    // ===== 初始化方法 =====
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(securityController).build();
        objectMapper = new ObjectMapper();
        
        // 重置所有 mocks
        reset(mockSecurityService, mockIpWhitelistService, mockRateLimitService, mockAuditService);
    }
    
    // ===== 獲取安全統計信息測試 =====
    
    @Test
    @DisplayName("測試獲取安全統計信息成功")
    void testGetSecurityStatsSuccess() throws Exception {
        // Given
        when(mockIpWhitelistService.isEnabled()).thenReturn(true);
        when(mockIpWhitelistService.getWhitelistStats()).thenReturn("Whitelist: 10 IPs");
        when(mockIpWhitelistService.getBlacklistStats()).thenReturn("Blacklist: 5 IPs");
        when(mockRateLimitService.getStats()).thenReturn("RateLimit: 100 requests");
        when(mockAuditService.getAuditStats()).thenReturn(Map.of("events", 50, "alerts", 2));
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ipWhitelist.enabled").value(true))
                .andExpect(jsonPath("$.ipWhitelist.stats").value("Whitelist: 10 IPs"))
                .andExpect(jsonPath("$.ipBlacklist.stats").value("Blacklist: 5 IPs"))
                .andExpect(jsonPath("$.rateLimit.stats").value("RateLimit: 100 requests"))
                .andExpect(jsonPath("$.audit.events").value(50))
                .andExpect(jsonPath("$.audit.alerts").value(2));
        
        // 驗證調用
        verify(mockIpWhitelistService).isEnabled();
        verify(mockIpWhitelistService).getWhitelistStats();
        verify(mockIpWhitelistService).getBlacklistStats();
        verify(mockRateLimitService).getStats();
        verify(mockAuditService).getAuditStats();
    }
    
    @Test
    @DisplayName("測試獲取安全統計信息時發生異常")
    void testGetSecurityStatsException() throws Exception {
        // Given
        when(mockIpWhitelistService.isEnabled()).thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/stats"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("獲取安全統計失敗")));
        
        verify(mockIpWhitelistService).isEnabled();
    }
    
    // ===== 白名單管理測試 =====
    
    @Test
    @DisplayName("測試添加IP到白名單成功")
    void testAddToWhitelistSuccess() throws Exception {
        // Given
        doNothing().when(mockIpWhitelistService).addToWhitelist(TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", TEST_IP))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("IP 已添加到白名單: " + TEST_IP));
        
        verify(mockIpWhitelistService).addToWhitelist(TEST_IP);
    }
    
    @Test
    @DisplayName("測試添加空IP到白名單")
    void testAddToWhitelistEmptyIp() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("IP 地址不能為空"));
        
        verify(mockIpWhitelistService, never()).addToWhitelist(anyString());
    }
    
    @Test
    @DisplayName("測試添加null IP到白名單")
    void testAddToWhitelistNullIp() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("IP 地址不能為空"));
        
        verify(mockIpWhitelistService, never()).addToWhitelist(anyString());
    }
    
    @Test
    @DisplayName("測試添加IP到白名單時發生異常")
    void testAddToWhitelistException() throws Exception {
        // Given
        doThrow(new RuntimeException("Invalid IP")).when(mockIpWhitelistService).addToWhitelist(TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", TEST_IP))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("添加 IP 到白名單失敗")));
        
        verify(mockIpWhitelistService).addToWhitelist(TEST_IP);
    }
    
    @Test
    @DisplayName("測試從白名單移除IP成功")
    void testRemoveFromWhitelistSuccess() throws Exception {
        // Given
        doNothing().when(mockIpWhitelistService).removeFromWhitelist(TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", TEST_IP))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("IP 已從白名單移除: " + TEST_IP));
        
        verify(mockIpWhitelistService).removeFromWhitelist(TEST_IP);
    }
    
    @Test
    @DisplayName("測試從白名單移除IP時包含空格")
    void testRemoveFromWhitelistWithSpaces() throws Exception {
        // Given
        String ipWithSpaces = "  " + TEST_IP + "  ";
        doNothing().when(mockIpWhitelistService).removeFromWhitelist(TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", ipWithSpaces))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("IP 已從白名單移除: " + ipWithSpaces));
        
        verify(mockIpWhitelistService).removeFromWhitelist(TEST_IP); // 應該調用trim後的結果
    }
    
    // ===== 黑名單管理測試 =====
    
    @Test
    @DisplayName("測試添加IP到黑名單成功")
    void testAddToBlacklistSuccess() throws Exception {
        // Given
        doNothing().when(mockIpWhitelistService).addToBlacklist(TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/blacklist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", TEST_IP))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("IP 已添加到黑名單: " + TEST_IP));
        
        verify(mockIpWhitelistService).addToBlacklist(TEST_IP);
    }
    
    @Test
    @DisplayName("測試添加空IP到黑名單")
    void testAddToBlacklistEmptyIp() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL + "/blacklist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("IP 地址不能為空"));
        
        verify(mockIpWhitelistService, never()).addToBlacklist(anyString());
    }
    
    @Test
    @DisplayName("測試從黑名單移除IP成功")
    void testRemoveFromBlacklistSuccess() throws Exception {
        // Given
        doNothing().when(mockIpWhitelistService).removeFromBlacklist(TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/blacklist/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", TEST_IP))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("IP 已從黑名單移除: " + TEST_IP));
        
        verify(mockIpWhitelistService).removeFromBlacklist(TEST_IP);
    }
    
    @Test
    @DisplayName("測試從黑名單移除IP時發生異常")
    void testRemoveFromBlacklistException() throws Exception {
        // Given
        doThrow(new RuntimeException("Service error")).when(mockIpWhitelistService).removeFromBlacklist(TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/blacklist/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", TEST_IP))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("從黑名單移除 IP 失敗")));
        
        verify(mockIpWhitelistService).removeFromBlacklist(TEST_IP);
    }
    
    // ===== IP狀態檢查測試 =====
    
    @Test
    @DisplayName("測試檢查IP狀態 - 在白名單中")
    void testCheckIpStatusWhitelisted() throws Exception {
        // Given
        when(mockIpWhitelistService.isWhitelisted(TEST_IP)).thenReturn(true);
        when(mockIpWhitelistService.isBlacklisted(TEST_IP)).thenReturn(false);
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/ip/check")
                .param("ip", TEST_IP))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ip").value(TEST_IP))
                .andExpect(jsonPath("$.whitelisted").value(true))
                .andExpect(jsonPath("$.blacklisted").value(false))
                .andExpect(jsonPath("$.status").value("allowed"));
        
        verify(mockIpWhitelistService).isWhitelisted(TEST_IP);
        verify(mockIpWhitelistService).isBlacklisted(TEST_IP);
    }
    
    @Test
    @DisplayName("測試檢查IP狀態 - 在黑名單中")
    void testCheckIpStatusBlacklisted() throws Exception {
        // Given
        when(mockIpWhitelistService.isWhitelisted(TEST_IP)).thenReturn(false);
        when(mockIpWhitelistService.isBlacklisted(TEST_IP)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/ip/check")
                .param("ip", TEST_IP))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ip").value(TEST_IP))
                .andExpect(jsonPath("$.whitelisted").value(false))
                .andExpect(jsonPath("$.blacklisted").value(true))
                .andExpect(jsonPath("$.status").value("blocked"));
        
        verify(mockIpWhitelistService).isWhitelisted(TEST_IP);
        verify(mockIpWhitelistService).isBlacklisted(TEST_IP);
    }
    
    @Test
    @DisplayName("測試檢查IP狀態 - 默認狀態")
    void testCheckIpStatusDefault() throws Exception {
        // Given
        when(mockIpWhitelistService.isWhitelisted(TEST_IP)).thenReturn(false);
        when(mockIpWhitelistService.isBlacklisted(TEST_IP)).thenReturn(false);
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/ip/check")
                .param("ip", TEST_IP))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ip").value(TEST_IP))
                .andExpect(jsonPath("$.whitelisted").value(false))
                .andExpect(jsonPath("$.blacklisted").value(false))
                .andExpect(jsonPath("$.status").value("default"));
        
        verify(mockIpWhitelistService).isWhitelisted(TEST_IP);
        verify(mockIpWhitelistService).isBlacklisted(TEST_IP);
    }
    
    @Test
    @DisplayName("測試檢查空IP狀態")
    void testCheckIpStatusEmptyIp() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/ip/check")
                .param("ip", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("IP 地址不能為空"));
        
        verify(mockIpWhitelistService, never()).isWhitelisted(anyString());
        verify(mockIpWhitelistService, never()).isBlacklisted(anyString());
    }
    
    @Test
    @DisplayName("測試檢查IP狀態時發生異常")
    void testCheckIpStatusException() throws Exception {
        // Given
        when(mockIpWhitelistService.isWhitelisted(TEST_IP)).thenThrow(new RuntimeException("Service error"));
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/ip/check")
                .param("ip", TEST_IP))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("檢查 IP 狀態失敗")));
        
        verify(mockIpWhitelistService).isWhitelisted(TEST_IP);
    }
    
    // ===== 頻率限制管理測試 =====
    
    @Test
    @DisplayName("測試清除頻率限制成功")
    void testClearRateLimitSuccess() throws Exception {
        // Given
        doNothing().when(mockRateLimitService).clearRateLimit(TEST_KEY);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/rate-limit/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("key", TEST_KEY))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("頻率限制已清除: " + TEST_KEY));
        
        verify(mockRateLimitService).clearRateLimit(TEST_KEY);
    }
    
    @Test
    @DisplayName("測試清除頻率限制時key為空")
    void testClearRateLimitEmptyKey() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL + "/rate-limit/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("key", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("限制鍵不能為空"));
        
        verify(mockRateLimitService, never()).clearRateLimit(anyString());
    }
    
    @Test
    @DisplayName("測試清除所有頻率限制成功")
    void testClearAllRateLimitsSuccess() throws Exception {
        // Given
        doNothing().when(mockRateLimitService).clearAllRateLimits();
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/rate-limit/clear-all"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("所有頻率限制已清除"));
        
        verify(mockRateLimitService).clearAllRateLimits();
    }
    
    @Test
    @DisplayName("測試清除所有頻率限制時發生異常")
    void testClearAllRateLimitsException() throws Exception {
        // Given
        doThrow(new RuntimeException("Service error")).when(mockRateLimitService).clearAllRateLimits();
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/rate-limit/clear-all"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("清除所有頻率限制失敗")));
        
        verify(mockRateLimitService).clearAllRateLimits();
    }
    
    @Test
    @DisplayName("測試獲取頻率限制狀態成功")
    void testGetRateLimitStatusSuccess() throws Exception {
        // Given
        when(mockRateLimitService.getRemainingRequests(TEST_KEY)).thenReturn(10);
        when(mockRateLimitService.getCurrentRequestCount(TEST_KEY)).thenReturn(5);
        when(mockRateLimitService.isAllowed(TEST_KEY)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(get(BASE_URL + "/rate-limit/status")
                .param("key", TEST_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.key").value(TEST_KEY))
                .andExpect(jsonPath("$.remainingRequests").value(10))
                .andExpect(jsonPath("$.currentRequests").value(5))
                .andExpect(jsonPath("$.isAllowed").value(true));
        
        verify(mockRateLimitService).getRemainingRequests(TEST_KEY);
        verify(mockRateLimitService).getCurrentRequestCount(TEST_KEY);
        verify(mockRateLimitService).isAllowed(TEST_KEY);
    }
    
    @Test
    @DisplayName("測試獲取頻率限制狀態時key為空")
    void testGetRateLimitStatusEmptyKey() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/rate-limit/status")
                .param("key", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("限制鍵不能為空"));
        
        verify(mockRateLimitService, never()).getRemainingRequests(anyString());
    }
    
    // ===== 快取管理測試 =====
    
    @Test
    @DisplayName("測試清空快取成功")
    void testClearCachesSuccess() throws Exception {
        // Given
        doNothing().when(mockIpWhitelistService).clearCache();
        doNothing().when(mockRateLimitService).clearAllRateLimits();
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("所有安全快取已清空"));
        
        verify(mockIpWhitelistService).clearCache();
        verify(mockRateLimitService).clearAllRateLimits();
    }
    
    @Test
    @DisplayName("測試清空快取時發生異常")
    void testClearCachesException() throws Exception {
        // Given
        doThrow(new RuntimeException("Cache error")).when(mockIpWhitelistService).clearCache();
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/cache/clear"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("清空快取失敗")));
        
        verify(mockIpWhitelistService).clearCache();
    }
    
    // ===== 安全檢查測試 =====
    
    @Test
    @DisplayName("測試執行安全檢查成功 - 允許")
    void testPerformSecurityCheckAllowed() throws Exception {
        // Given
        CommonSecurityService.SecurityCheckResult allowResult = 
            CommonSecurityService.SecurityCheckResult.allow();
        when(mockSecurityService.performSecurityCheck(any(CommonSecurityService.RequestContext.class)))
            .thenReturn(allowResult);
        
        Map<String, String> request = Map.of(
            "clientIp", TEST_IP,
            "userAgent", "TestUA",
            "username", "testuser",
            "path", "/test",
            "method", "GET"
        );
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").value("N/A"))
                .andExpect(jsonPath("$.action").value("ALLOW"));
        
        verify(mockSecurityService).performSecurityCheck(any(CommonSecurityService.RequestContext.class));
    }
    
    @Test
    @DisplayName("測試執行安全檢查成功 - 拒絕")
    void testPerformSecurityCheckDenied() throws Exception {
        // Given
        CommonSecurityService.SecurityCheckResult denyResult = 
            CommonSecurityService.SecurityCheckResult.deny("IP blocked", 
                CommonSecurityService.SecurityAction.IP_BLOCK);
        when(mockSecurityService.performSecurityCheck(any(CommonSecurityService.RequestContext.class)))
            .thenReturn(denyResult);
        
        Map<String, String> request = Map.of(
            "clientIp", TEST_IP,
            "userAgent", "TestUA"
        );
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("IP blocked"))
                .andExpect(jsonPath("$.action").value("IP_BLOCK"));
        
        verify(mockSecurityService).performSecurityCheck(any(CommonSecurityService.RequestContext.class));
    }
    
    @Test
    @DisplayName("測試執行安全檢查時clientIp為空")
    void testPerformSecurityCheckEmptyClientIp() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL + "/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userAgent", "TestUA"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("clientIp 不能為空"));
        
        verify(mockSecurityService, never()).performSecurityCheck(any());
    }
    
    @Test
    @DisplayName("測試執行安全檢查時發生異常")
    void testPerformSecurityCheckException() throws Exception {
        // Given
        when(mockSecurityService.performSecurityCheck(any(CommonSecurityService.RequestContext.class)))
            .thenThrow(new RuntimeException("Security check failed"));
        
        Map<String, String> request = Map.of("clientIp", TEST_IP);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("執行安全檢查失敗")));
        
        verify(mockSecurityService).performSecurityCheck(any(CommonSecurityService.RequestContext.class));
    }
    
    // ===== 邊界情況和錯誤處理測試 =====
    
    @Test
    @DisplayName("測試無效JSON請求")
    void testInvalidJsonRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("測試不支持的HTTP方法")
    void testUnsupportedHttpMethod() throws Exception {
        // When & Then
        mockMvc.perform(put(BASE_URL + "/stats"))
                .andExpect(status().isMethodNotAllowed());
    }
    
    @Test
    @DisplayName("測試特殊字符IP處理")
    void testSpecialCharacterIp() throws Exception {
        // Given
        String specialIp = "192.168.1.1<script>";
        doNothing().when(mockIpWhitelistService).addToWhitelist(specialIp);
        
        // When & Then
        mockMvc.perform(post(BASE_URL + "/whitelist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ip", specialIp))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("IP 已添加到白名單: " + specialIp));
        
        verify(mockIpWhitelistService).addToWhitelist(specialIp);
    }
}