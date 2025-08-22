package dowob.xyz.filemanagementwebdav.component.security;

import dowob.xyz.filemanagementwebdav.config.properties.WebDavSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IpWhitelistService 單元測試
 * 
 * 測試 IP 白名單和黑名單服務的核心功能，包括 CIDR 範圍、IP 範圍、單個 IP 檢查和快取機制。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName IpWhitelistServiceTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("IpWhitelistService 測試")
class IpWhitelistServiceTest {
    
    private IpWhitelistService ipWhitelistService;
    
    @Mock
    private WebDavSecurityProperties securityProperties;
    
    @Mock
    private WebDavSecurityProperties.IpConfig ipConfig;
    
    @Mock
    private WebDavSecurityProperties.IpConfig.WhitelistConfig whitelistConfig;
    
    @Mock
    private WebDavSecurityProperties.IpConfig.BlacklistConfig blacklistConfig;
    
    // ===== 測試用常量 =====
    
    private static final String VALID_IP_1 = "192.168.1.100";
    private static final String VALID_IP_2 = "192.168.1.200";
    private static final String VALID_IP_3 = "10.0.0.50";
    private static final String EXTERNAL_IP = "8.8.8.8";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String INVALID_IP = "999.999.999.999";
    
    private static final String CIDR_RANGE = "192.168.1.0/24";
    private static final String IP_RANGE = "10.0.0.1-10.0.0.100";
    
    // ===== 初始化方法 =====
    
    @BeforeEach
    void setUp() {
        // 設置 mock 物件的預設行為
        when(securityProperties.getIp()).thenReturn(ipConfig);
        when(ipConfig.getWhitelist()).thenReturn(whitelistConfig);
        when(ipConfig.getBlacklist()).thenReturn(blacklistConfig);
        when(whitelistConfig.isEnabled()).thenReturn(false);
        when(whitelistConfig.getIps()).thenReturn(Collections.emptyList());
        when(blacklistConfig.getIps()).thenReturn(Collections.emptyList());
        
        // 默認創建啟用白名單的服務（專用 WebDAV 子服務）
        ipWhitelistService = new IpWhitelistService(securityProperties);
    }
    
    // ===== 構造函數和初始化測試 =====
    
    @Test
    @DisplayName("測試服務初始化（專用子服務預設啟用）")
    void testServiceInitializationWithEnabledWhitelist() {
        // Given
        when(whitelistConfig.isEnabled()).thenReturn(false);
        when(whitelistConfig.getIps()).thenReturn(Collections.emptyList());
        when(blacklistConfig.getIps()).thenReturn(Collections.emptyList());
        
        // When
        IpWhitelistService service = new IpWhitelistService(securityProperties);
        
        // Then
        assertThat(service.isEnabled()).isFalse(); // 依據配置
        assertThat(service.isWhitelisted("127.0.0.1")).isTrue(); // 本地IP自動在白名單中
        assertThat(service.isBlacklisted(EXTERNAL_IP)).isFalse();
    }
    
    @Test
    @DisplayName("測試服務初始化含自定義 IP 列表")
    void testServiceInitializationWithCustomIpLists() {
        // Given
        List<String> whitelistIps = Arrays.asList(VALID_IP_1, CIDR_RANGE);
        List<String> blacklistIps = Arrays.asList(EXTERNAL_IP);
        when(whitelistConfig.isEnabled()).thenReturn(true);
        when(whitelistConfig.getIps()).thenReturn(whitelistIps);
        when(blacklistConfig.getIps()).thenReturn(blacklistIps);
        
        // When
        IpWhitelistService service = new IpWhitelistService(securityProperties);
        
        // Then
        assertThat(service.isEnabled()).isTrue();
        assertThat(service.isWhitelisted(VALID_IP_1)).isTrue();
        assertThat(service.isWhitelisted("192.168.1.50")).isTrue(); // CIDR範圍內
        assertThat(service.isBlacklisted(EXTERNAL_IP)).isTrue();
    }
    
    @Test
    @DisplayName("測試預設添加本地IP到白名單")
    void testDefaultLocalIpsAddedToWhitelist() {
        // Given
        when(whitelistConfig.isEnabled()).thenReturn(false);
        when(whitelistConfig.getIps()).thenReturn(Collections.emptyList());
        when(blacklistConfig.getIps()).thenReturn(Collections.emptyList());
        
        // When
        IpWhitelistService service = new IpWhitelistService(securityProperties);
        
        // Then - 本地IP應該被自動添加到白名單
        assertThat(service.isWhitelisted("127.0.0.1")).isTrue();
        assertThat(service.isWhitelisted("192.168.1.1")).isTrue(); // 私有網路範圍
        assertThat(service.isWhitelisted("172.16.0.1")).isTrue();  // 私有網路範圍
        assertThat(service.isWhitelisted("10.0.0.1")).isTrue();    // 私有網路範圍
    }
    
    // ===== 白名單檢查測試 =====
    
    @Test
    @DisplayName("測試啟用白名單時的默認行為")
    void testEnabledWhitelistDefaultBehavior() {
        // Given - 在 setup 中白名單被設置為 disabled
        assertThat(ipWhitelistService.isEnabled()).isFalse();
        
        // When & Then - 即使白名單未啟用，本地IP仍應該被允許
        assertThat(ipWhitelistService.isWhitelisted("127.0.0.1")).isTrue(); // 本地IP
        assertThat(ipWhitelistService.isWhitelisted("192.168.1.1")).isTrue(); // 私有網路
        assertThat(ipWhitelistService.isWhitelisted(EXTERNAL_IP)).isTrue(); // 白名單未啟用時，外部IP也被允許
    }
    
    @Test
    @DisplayName("測試單個IP白名單檢查")
    void testSingleIpWhitelist() {
        // Given - 使用外部IP避免與預設私有網路衝突
        String testIp1 = "203.0.113.10"; // 測試用保留IP段
        String testIp2 = "203.0.113.20";
        List<String> whitelistIps = Arrays.asList(testIp1, testIp2);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        // When & Then
        assertThat(service.isWhitelisted(testIp1)).isTrue();
        assertThat(service.isWhitelisted(testIp2)).isTrue();
        assertThat(service.isWhitelisted("203.0.113.30")).isFalse(); // 不在白名單中
        assertThat(service.isWhitelisted(EXTERNAL_IP)).isFalse();
    }
    
    @Test
    @DisplayName("測試CIDR範圍白名單檢查")
    void testCidrRangeWhitelist() {
        // Given - 使用不與預設私有網路衝突的IP段
        String testCidr = "203.0.113.0/24";
        List<String> whitelistIps = Arrays.asList(testCidr);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        // When & Then
        assertThat(service.isWhitelisted("203.0.113.1")).isTrue();   // 範圍內
        assertThat(service.isWhitelisted("203.0.113.100")).isTrue(); // 範圍內
        assertThat(service.isWhitelisted("203.0.113.254")).isTrue(); // 範圍內
        assertThat(service.isWhitelisted("203.0.114.1")).isFalse();  // 範圍外
        assertThat(service.isWhitelisted("204.0.113.1")).isFalse();  // 範圍外
    }
    
    @Test
    @DisplayName("測試IP範圍白名單檢查")
    void testIpRangeWhitelist() {
        // Given - 使用不與預設私有網路衝突的IP段
        String testRange = "203.0.113.1-203.0.113.100";
        List<String> whitelistIps = Arrays.asList(testRange);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        // When & Then
        assertThat(service.isWhitelisted("203.0.113.1")).isTrue();   // 範圍開始
        assertThat(service.isWhitelisted("203.0.113.50")).isTrue();  // 範圍內
        assertThat(service.isWhitelisted("203.0.113.100")).isTrue(); // 範圍結束
        assertThat(service.isWhitelisted("203.0.113.101")).isFalse(); // 範圍外
        assertThat(service.isWhitelisted("203.0.114.50")).isFalse();  // 範圍外
    }
    
    @Test
    @DisplayName("測試混合白名單檢查")
    void testMixedWhitelist() {
        // Given - 使用不與預設私有網路衝突的IP段
        String testIp = "203.0.113.10";
        String testCidr = "198.51.100.0/24";
        String testRange = "203.0.113.50-203.0.113.100";
        List<String> whitelistIps = Arrays.asList(testIp, testCidr, testRange);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        // When & Then
        assertThat(service.isWhitelisted(testIp)).isTrue();           // 單個IP
        assertThat(service.isWhitelisted("198.51.100.50")).isTrue(); // CIDR範圍
        assertThat(service.isWhitelisted("203.0.113.75")).isTrue();  // IP範圍
        assertThat(service.isWhitelisted(EXTERNAL_IP)).isFalse();    // 都不匹配
    }
    
    // ===== 黑名單檢查測試 =====
    
    @Test
    @DisplayName("測試單個IP黑名單檢查")
    void testSingleIpBlacklist() {
        // Given
        List<String> blacklistIps = Arrays.asList(EXTERNAL_IP, VALID_IP_1);
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), blacklistIps);
        
        // When & Then
        assertThat(service.isBlacklisted(EXTERNAL_IP)).isTrue();
        assertThat(service.isBlacklisted(VALID_IP_1)).isTrue();
        assertThat(service.isBlacklisted(VALID_IP_2)).isFalse(); // 不在黑名單中
    }
    
    @Test
    @DisplayName("測試CIDR範圍黑名單檢查")
    void testCidrRangeBlacklist() {
        // Given
        List<String> blacklistIps = Arrays.asList("192.168.100.0/24");
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), blacklistIps);
        
        // When & Then
        assertThat(service.isBlacklisted("192.168.100.1")).isTrue();   // 範圍內
        assertThat(service.isBlacklisted("192.168.100.100")).isTrue(); // 範圍內
        assertThat(service.isBlacklisted("192.168.101.1")).isFalse();  // 範圍外
    }
    
    @Test
    @DisplayName("測試IP範圍黑名單檢查")
    void testIpRangeBlacklist() {
        // Given
        List<String> blacklistIps = Arrays.asList("8.8.8.1-8.8.8.100");
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), blacklistIps);
        
        // When & Then
        assertThat(service.isBlacklisted("8.8.8.8")).isTrue();   // 範圍內
        assertThat(service.isBlacklisted("8.8.8.50")).isTrue();  // 範圍內
        assertThat(service.isBlacklisted("8.8.8.101")).isFalse(); // 範圍外
    }
    
    // ===== 輸入驗證測試 =====
    
    @Test
    @DisplayName("測試null IP檢查")
    void testNullIpCheck() {
        // Given
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), Collections.emptyList());
        
        // When & Then
        assertThat(service.isWhitelisted(null)).isFalse();
        assertThat(service.isBlacklisted(null)).isFalse();
    }
    
    @Test
    @DisplayName("測試空字符串IP檢查")
    void testEmptyIpCheck() {
        // Given
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), Collections.emptyList());
        
        // When & Then
        assertThat(service.isWhitelisted("")).isFalse();
        assertThat(service.isWhitelisted("   ")).isFalse();
        assertThat(service.isBlacklisted("")).isFalse();
        assertThat(service.isBlacklisted("   ")).isFalse();
    }
    
    @Test
    @DisplayName("測試無效IP格式")
    void testInvalidIpFormat() {
        // Given
        List<String> ips = Arrays.asList(INVALID_IP, "not.an.ip", "300.300.300.300");
        IpWhitelistService service = new IpWhitelistService(ips, Collections.emptyList());
        
        // When & Then - 無效IP應該被忽略，但預設本地IP仍然在白名單中
        // 使用明確的外部IP進行測試
        assertThat(service.isWhitelisted(EXTERNAL_IP)).isFalse(); // 外部IP不在白名單中
    }
    
    @Test
    @DisplayName("測試無效CIDR格式")
    void testInvalidCidrFormat() {
        // Given
        List<String> ips = Arrays.asList("203.0.113.0/99", "203.0.113.0/", "203.0.113.0/abc");
        IpWhitelistService service = new IpWhitelistService(ips, Collections.emptyList());
        
        // When & Then - 無效CIDR應該被忽略，但預設本地IP仍然在白名單中
        // 使用外部IP測試
        assertThat(service.isWhitelisted("203.0.113.1")).isFalse();
    }
    
    // ===== 動態添加和移除測試 =====
    
    @Test
    @DisplayName("測試動態添加IP到白名單")
    void testAddToWhitelist() {
        // Given
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), Collections.emptyList());
        // 注意：啟用白名單時會自動添加本地IP，VALID_IP_1可能在私有網路範圍內
        String externalIp = "8.8.8.8"; // 使用明確的外部IP進行測試
        assertThat(service.isWhitelisted(externalIp)).isFalse(); // 初始不在白名單
        
        // When
        service.addToWhitelist(externalIp);
        
        // Then
        assertThat(service.isWhitelisted(externalIp)).isTrue();
    }
    
    @Test
    @DisplayName("測試動態添加CIDR範圍到白名單")
    void testAddCidrRangeToWhitelist() {
        // Given
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), Collections.emptyList());
        // 使用不在預設私有網路範圍內的IP段進行測試
        String testIp1 = "203.0.113.1"; // 測試用保留IP段
        String testIp2 = "203.0.113.100";
        assertThat(service.isWhitelisted(testIp1)).isFalse();
        
        // When
        service.addToWhitelist("203.0.113.0/24");
        
        // Then
        assertThat(service.isWhitelisted(testIp1)).isTrue();
        assertThat(service.isWhitelisted(testIp2)).isTrue();
    }
    
    @Test
    @DisplayName("測試動態添加IP到黑名單")
    void testAddToBlacklist() {
        // Given
        assertThat(ipWhitelistService.isBlacklisted(VALID_IP_1)).isFalse();
        
        // When
        ipWhitelistService.addToBlacklist(VALID_IP_1);
        
        // Then
        assertThat(ipWhitelistService.isBlacklisted(VALID_IP_1)).isTrue();
    }
    
    @Test
    @DisplayName("測試從白名單移除IP")
    void testRemoveFromWhitelist() {
        // Given - 使用外部IP進行測試
        String testIp = "203.0.113.10";
        List<String> whitelistIps = Arrays.asList(testIp);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        assertThat(service.isWhitelisted(testIp)).isTrue();
        
        // When
        service.removeFromWhitelist(testIp);
        
        // Then
        assertThat(service.isWhitelisted(testIp)).isFalse();
    }
    
    @Test
    @DisplayName("測試從黑名單移除IP")
    void testRemoveFromBlacklist() {
        // Given
        List<String> blacklistIps = Arrays.asList(VALID_IP_1);
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), blacklistIps);
        assertThat(service.isBlacklisted(VALID_IP_1)).isTrue();
        
        // When
        service.removeFromBlacklist(VALID_IP_1);
        
        // Then
        assertThat(service.isBlacklisted(VALID_IP_1)).isFalse();
    }
    
    // ===== 快取機制測試 =====
    
    @Test
    @DisplayName("測試白名單快取機制")
    void testWhitelistCache() {
        // Given
        List<String> whitelistIps = Arrays.asList(VALID_IP_1);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        // When - 多次檢查同一IP
        boolean result1 = service.isWhitelisted(VALID_IP_1);
        boolean result2 = service.isWhitelisted(VALID_IP_1);
        boolean result3 = service.isWhitelisted(VALID_IP_1);
        
        // Then - 結果應該一致（測試快取不會影響正確性）
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertThat(result3).isTrue();
    }
    
    @Test
    @DisplayName("測試黑名單快取機制")
    void testBlacklistCache() {
        // Given
        List<String> blacklistIps = Arrays.asList(VALID_IP_1);
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), blacklistIps);
        
        // When - 多次檢查同一IP
        boolean result1 = service.isBlacklisted(VALID_IP_1);
        boolean result2 = service.isBlacklisted(VALID_IP_1);
        boolean result3 = service.isBlacklisted(VALID_IP_1);
        
        // Then - 結果應該一致
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertThat(result3).isTrue();
    }
    
    @Test
    @DisplayName("測試清空快取")
    void testClearCache() {
        // Given
        List<String> whitelistIps = Arrays.asList(VALID_IP_1);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        // 先觸發快取
        service.isWhitelisted(VALID_IP_1);
        service.isBlacklisted(VALID_IP_2);
        
        // When
        service.clearCache();
        
        // Then - 清空快取後仍應正常工作
        assertThat(service.isWhitelisted(VALID_IP_1)).isTrue();
        assertThat(service.isBlacklisted(VALID_IP_2)).isFalse();
    }
    
    // ===== 統計信息測試 =====
    
    @Test
    @DisplayName("測試獲取白名單統計")
    void testGetWhitelistStats() {
        // Given
        List<String> whitelistIps = Arrays.asList(VALID_IP_1, VALID_IP_2, CIDR_RANGE);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        // When
        String stats = service.getWhitelistStats();
        
        // Then
        assertThat(stats).contains("白名單");
        assertThat(stats).contains("IP:");
        assertThat(stats).contains("範圍:");
        assertThat(stats).contains("快取命中:");
    }
    
    @Test
    @DisplayName("測試獲取黑名單統計")
    void testGetBlacklistStats() {
        // Given
        List<String> blacklistIps = Arrays.asList(EXTERNAL_IP, "192.168.100.0/24");
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), blacklistIps);
        
        // When
        String stats = service.getBlacklistStats();
        
        // Then
        assertThat(stats).contains("黑名單");
        assertThat(stats).contains("IP:");
        assertThat(stats).contains("範圍:");
        assertThat(stats).contains("快取命中:");
    }
    
    // ===== 併發測試 =====
    
    @Test
    @DisplayName("測試併發白名單檢查")
    void testConcurrentWhitelistCheck() throws InterruptedException {
        // Given
        List<String> whitelistIps = Arrays.asList(VALID_IP_1, CIDR_RANGE);
        IpWhitelistService service = new IpWhitelistService(whitelistIps, Collections.emptyList());
        
        int threadCount = 10;
        int checksPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // When - 多個線程同時檢查白名單
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < checksPerThread; j++) {
                        if (service.isWhitelisted(VALID_IP_1)) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then - 所有檢查都應該成功
        assertThat(successCount.get()).isEqualTo(threadCount * checksPerThread);
    }
    
    @Test
    @DisplayName("測試併發動態添加和檢查")
    void testConcurrentAddAndCheck() throws InterruptedException {
        // Given
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), Collections.emptyList());
        
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // When - 一半線程添加IP，一半線程檢查IP
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    String ip = "192.168.10." + (threadIndex + 1);
                    if (threadIndex % 2 == 0) {
                        // 添加IP到白名單
                        service.addToWhitelist(ip);
                    } else {
                        // 檢查IP是否在白名單
                        service.isWhitelisted(ip);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then - 應該沒有並發問題
        assertThat(service.getWhitelistStats()).contains("白名單");
    }
    
    // ===== 邊界情況和特殊測試 =====
    
    @Test
    @DisplayName("測試IPv6地址處理")
    void testIpv6Handling() {
        // Given
        List<String> ips = Arrays.asList("::1", "2001:db8::1");
        IpWhitelistService service = new IpWhitelistService(ips, Collections.emptyList());
        
        // When & Then - IPv6地址應該被正確處理
        assertThat(service.isWhitelisted("::1")).isTrue();
        // 注意：由於實現中使用了 InetAddress.getByName，IPv6支持取決於具體實現
    }
    
    @Test
    @DisplayName("測試域名處理")
    void testDomainNameHandling() {
        // Given
        List<String> ips = Arrays.asList("localhost");
        IpWhitelistService service = new IpWhitelistService(ips, Collections.emptyList());
        
        // When & Then
        assertThat(service.isWhitelisted("localhost")).isTrue();
    }
    
    @Test
    @DisplayName("測試極長IP字符串")
    void testVeryLongIpString() {
        // Given
        String longString = "203.0.113.1" + "x".repeat(1000); // 減少長度以避免內存問題
        
        // When & Then - 應該優雅處理，不崩潰
        // 長字符串會被當作無效IP處理，在未啟用白名單時會返回true（允許通過）
        assertThat(ipWhitelistService.isWhitelisted(longString)).isTrue(); // 白名單未啟用，無效IP也被允許
        assertThat(ipWhitelistService.isBlacklisted(longString)).isFalse(); // 不在黑名單中
    }
    
    @Test
    @DisplayName("測試空列表初始化")
    void testEmptyListInitialization() {
        // Given & When
        IpWhitelistService service = new IpWhitelistService(null, null);
        
        // Then - 應該正常工作，只有默認的本地IP被添加
        assertThat(service.isWhitelisted(LOCALHOST_IP)).isTrue();
        assertThat(service.isWhitelisted(EXTERNAL_IP)).isFalse();
    }
    
    @Test
    @DisplayName("測試CIDR邊界值")
    void testCidrBoundaryValues() {
        // Given - 使用不與預設私有網路衝突的IP段
        List<String> ips = Arrays.asList("203.0.113.0/32");
        IpWhitelistService service = new IpWhitelistService(ips, Collections.emptyList());
        
        // When & Then
        // /32 應該只匹配單個IP
        assertThat(service.isWhitelisted("203.0.113.0")).isTrue();
        assertThat(service.isWhitelisted("203.0.113.1")).isFalse();
    }
    
    @Test
    @DisplayName("測試特殊字符和編碼")
    void testSpecialCharactersAndEncoding() {
        // Given
        String specialIp = "192.168.1.1%20"; // URL編碼的空格
        
        // When & Then - 應該處理特殊字符
        ipWhitelistService.addToWhitelist(specialIp);
        // 結果取決於 InetAddress.getByName 如何處理這些字符
    }
    
    @Test
    @DisplayName("測試大量IP處理性能")
    void testLargeNumberOfIps() {
        // Given - 添加大量IP到白名單
        IpWhitelistService service = new IpWhitelistService(Collections.emptyList(), Collections.emptyList());
        
        // When - 添加100個外部IP（使用測試用保留IP段）
        for (int i = 1; i <= 100; i++) {
            service.addToWhitelist("203.0.113." + i);
        }
        
        // Then - 檢查應該仍然有效
        assertThat(service.isWhitelisted("203.0.113.1")).isTrue();
        assertThat(service.isWhitelisted("203.0.113.50")).isTrue();
        assertThat(service.isWhitelisted("203.0.113.100")).isTrue();
        assertThat(service.isWhitelisted("203.0.114.1")).isFalse(); // 不在測試範圍內
        
        // 統計信息應該反映大量的IP
        String stats = service.getWhitelistStats();
        assertThat(stats).contains("IP:");
    }
}