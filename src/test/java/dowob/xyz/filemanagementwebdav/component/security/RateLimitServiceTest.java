package dowob.xyz.filemanagementwebdav.component.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimitService 單元測試
 * 
 * 測試頻率限制服務的核心功能，包括不同類型的頻率限制、快取機制和統計信息。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName RateLimitServiceTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService 測試")
class RateLimitServiceTest {
    
    private RateLimitService rateLimitService;
    
    // ===== 測試用常量 =====
    
    private static final int IP_REQUESTS_PER_MINUTE = 10;
    private static final int USER_REQUESTS_PER_MINUTE = 20;
    private static final int GLOBAL_REQUESTS_PER_SECOND = 5;
    private static final int CACHE_SIZE = 1000;
    
    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_USERNAME = "testuser";
    private static final String IP_KEY_PREFIX = "ip:";
    private static final String USER_KEY_PREFIX = "user:";
    private static final String GLOBAL_KEY = "global:requests";
    
    // ===== 初始化方法 =====
    
    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(
            IP_REQUESTS_PER_MINUTE,
            USER_REQUESTS_PER_MINUTE, 
            GLOBAL_REQUESTS_PER_SECOND,
            CACHE_SIZE
        );
    }
    
    // ===== 基本功能測試 =====
    
    @Test
    @DisplayName("測試初始狀態允許請求")
    void testInitialRequestsAllowed() {
        // When & Then
        assertThat(rateLimitService.isAllowed(IP_KEY_PREFIX + TEST_IP)).isTrue();
        assertThat(rateLimitService.isAllowed(USER_KEY_PREFIX + TEST_USERNAME)).isTrue();
        assertThat(rateLimitService.isAllowed(GLOBAL_KEY)).isTrue();
    }
    
    @Test
    @DisplayName("測試 IP 頻率限制")
    void testIpRateLimit() {
        // Given
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        
        // When - 消耗所有允許的請求
        for (int i = 0; i < IP_REQUESTS_PER_MINUTE; i++) {
            assertThat(rateLimitService.isAllowed(ipKey)).isTrue();
        }
        
        // Then - 超過限制後應該被拒絕
        assertThat(rateLimitService.isAllowed(ipKey)).isFalse();
        assertThat(rateLimitService.isAllowed(ipKey)).isFalse();
    }
    
    @Test
    @DisplayName("測試用戶頻率限制")
    void testUserRateLimit() {
        // Given
        String userKey = USER_KEY_PREFIX + TEST_USERNAME;
        
        // When - 消耗所有允許的請求
        for (int i = 0; i < USER_REQUESTS_PER_MINUTE; i++) {
            assertThat(rateLimitService.isAllowed(userKey)).isTrue();
        }
        
        // Then - 超過限制後應該被拒絕
        assertThat(rateLimitService.isAllowed(userKey)).isFalse();
        assertThat(rateLimitService.isAllowed(userKey)).isFalse();
    }
    
    @Test
    @DisplayName("測試全域頻率限制")
    void testGlobalRateLimit() {
        // When - 消耗所有允許的請求
        for (int i = 0; i < GLOBAL_REQUESTS_PER_SECOND; i++) {
            assertThat(rateLimitService.isAllowed(GLOBAL_KEY)).isTrue();
        }
        
        // Then - 超過限制後應該被拒絕
        assertThat(rateLimitService.isAllowed(GLOBAL_KEY)).isFalse();
        assertThat(rateLimitService.isAllowed(GLOBAL_KEY)).isFalse();
    }
    
    @Test
    @DisplayName("測試不同類型的鍵使用不同的限制")
    void testDifferentKeyTypesDifferentLimits() {
        // Given
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        String userKey = USER_KEY_PREFIX + TEST_USERNAME;
        
        // When - IP 消耗完所有請求
        for (int i = 0; i < IP_REQUESTS_PER_MINUTE; i++) {
            assertThat(rateLimitService.isAllowed(ipKey)).isTrue();
        }
        
        // Then - IP 被限制，但用戶請求仍然可以
        assertThat(rateLimitService.isAllowed(ipKey)).isFalse();
        assertThat(rateLimitService.isAllowed(userKey)).isTrue();
        
        // When - 用戶也消耗完所有請求
        for (int i = 1; i < USER_REQUESTS_PER_MINUTE; i++) { // 從1開始，因為已經用了一個
            assertThat(rateLimitService.isAllowed(userKey)).isTrue();
        }
        
        // Then - 用戶也被限制
        assertThat(rateLimitService.isAllowed(userKey)).isFalse();
    }
    
    // ===== 剩餘請求數測試 =====
    
    @Test
    @DisplayName("測試獲取剩餘請求數")
    void testGetRemainingRequests() {
        // Given
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        
        // When & Then - 初始狀態
        assertThat(rateLimitService.getRemainingRequests(ipKey)).isEqualTo(IP_REQUESTS_PER_MINUTE);
        
        // When - 使用一些請求
        rateLimitService.isAllowed(ipKey);
        rateLimitService.isAllowed(ipKey);
        rateLimitService.isAllowed(ipKey);
        
        // Then - 剩餘請求數應該減少
        assertThat(rateLimitService.getRemainingRequests(ipKey)).isEqualTo(IP_REQUESTS_PER_MINUTE - 3);
        
        // When - 消耗完所有請求
        for (int i = 3; i < IP_REQUESTS_PER_MINUTE; i++) {
            rateLimitService.isAllowed(ipKey);
        }
        
        // Then - 剩餘請求數應該為0
        assertThat(rateLimitService.getRemainingRequests(ipKey)).isEqualTo(0);
    }
    
    @Test
    @DisplayName("測試獲取當前請求數")
    void testGetCurrentRequestCount() {
        // Given
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        
        // When & Then - 初始狀態
        assertThat(rateLimitService.getCurrentRequestCount(ipKey)).isEqualTo(0);
        
        // When - 使用一些請求
        rateLimitService.isAllowed(ipKey);
        rateLimitService.isAllowed(ipKey);
        rateLimitService.isAllowed(ipKey);
        
        // Then - 當前請求數應該增加
        assertThat(rateLimitService.getCurrentRequestCount(ipKey)).isEqualTo(3);
    }
    
    @Test
    @DisplayName("測試未知鍵的剩餘請求數")
    void testGetRemainingRequestsForUnknownKey() {
        // Given
        String unknownKey = "unknown:key";
        
        // When & Then
        assertThat(rateLimitService.getRemainingRequests(unknownKey)).isEqualTo(IP_REQUESTS_PER_MINUTE);
    }
    
    // ===== 輸入驗證測試 =====
    
    @Test
    @DisplayName("測試 null 鍵")
    void testNullKey() {
        // When & Then
        assertThat(rateLimitService.isAllowed(null)).isTrue();
        assertThat(rateLimitService.getRemainingRequests(null)).isEqualTo(Integer.MAX_VALUE);
        assertThat(rateLimitService.getCurrentRequestCount(null)).isEqualTo(0);
    }
    
    @Test
    @DisplayName("測試空字符串鍵")
    void testEmptyKey() {
        // When & Then
        assertThat(rateLimitService.isAllowed("")).isTrue();
        assertThat(rateLimitService.isAllowed("   ")).isTrue();
        assertThat(rateLimitService.getRemainingRequests("")).isEqualTo(Integer.MAX_VALUE);
        assertThat(rateLimitService.getCurrentRequestCount("")).isEqualTo(0);
    }
    
    @Test
    @DisplayName("測試無前綴鍵使用默認IP限制")
    void testKeyWithoutPrefixUsesDefaultIpLimit() {
        // Given
        String keyWithoutPrefix = "somekey";
        
        // When - 消耗所有允許的請求（應該使用IP限制）
        for (int i = 0; i < IP_REQUESTS_PER_MINUTE; i++) {
            assertThat(rateLimitService.isAllowed(keyWithoutPrefix)).isTrue();
        }
        
        // Then - 超過限制後應該被拒絕
        assertThat(rateLimitService.isAllowed(keyWithoutPrefix)).isFalse();
    }
    
    // ===== 快取清除測試 =====
    
    @Test
    @DisplayName("測試清除特定鍵的限制")
    void testClearRateLimit() {
        // Given
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        
        // 消耗所有請求
        for (int i = 0; i < IP_REQUESTS_PER_MINUTE; i++) {
            rateLimitService.isAllowed(ipKey);
        }
        assertThat(rateLimitService.isAllowed(ipKey)).isFalse();
        
        // When - 清除限制
        rateLimitService.clearRateLimit(ipKey);
        
        // Then - 應該能夠再次請求
        assertThat(rateLimitService.isAllowed(ipKey)).isTrue();
        assertThat(rateLimitService.getRemainingRequests(ipKey)).isEqualTo(IP_REQUESTS_PER_MINUTE - 1);
    }
    
    @Test
    @DisplayName("測試清除所有限制")
    void testClearAllRateLimits() {
        // Given
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        String userKey = USER_KEY_PREFIX + TEST_USERNAME;
        
        // 消耗所有請求
        for (int i = 0; i < IP_REQUESTS_PER_MINUTE; i++) {
            rateLimitService.isAllowed(ipKey);
        }
        for (int i = 0; i < USER_REQUESTS_PER_MINUTE; i++) {
            rateLimitService.isAllowed(userKey);
        }
        assertThat(rateLimitService.isAllowed(ipKey)).isFalse();
        assertThat(rateLimitService.isAllowed(userKey)).isFalse();
        
        // When - 清除所有限制
        rateLimitService.clearAllRateLimits();
        
        // Then - 所有鍵都應該能夠再次請求
        assertThat(rateLimitService.isAllowed(ipKey)).isTrue();
        assertThat(rateLimitService.isAllowed(userKey)).isTrue();
    }
    
    // ===== 便利方法測試 =====
    
    @Test
    @DisplayName("測試檢查全域頻率限制")
    void testCheckGlobalRateLimit() {
        // When - 消耗所有允許的請求
        for (int i = 0; i < GLOBAL_REQUESTS_PER_SECOND; i++) {
            assertThat(rateLimitService.checkGlobalRateLimit()).isTrue();
        }
        
        // Then - 超過限制後應該被拒絕
        assertThat(rateLimitService.checkGlobalRateLimit()).isFalse();
    }
    
    @Test
    @DisplayName("測試檢查IP頻率限制")
    void testCheckIpRateLimit() {
        // When - 消耗所有允許的請求
        for (int i = 0; i < IP_REQUESTS_PER_MINUTE; i++) {
            assertThat(rateLimitService.checkIpRateLimit(TEST_IP)).isTrue();
        }
        
        // Then - 超過限制後應該被拒絕
        assertThat(rateLimitService.checkIpRateLimit(TEST_IP)).isFalse();
    }
    
    @Test
    @DisplayName("測試檢查用戶頻率限制")
    void testCheckUserRateLimit() {
        // When - 消耗所有允許的請求
        for (int i = 0; i < USER_REQUESTS_PER_MINUTE; i++) {
            assertThat(rateLimitService.checkUserRateLimit(TEST_USERNAME)).isTrue();
        }
        
        // Then - 超過限制後應該被拒絕
        assertThat(rateLimitService.checkUserRateLimit(TEST_USERNAME)).isFalse();
    }
    
    @Test
    @DisplayName("測試檢查空IP頻率限制")
    void testCheckIpRateLimitWithNullIp() {
        // When & Then
        assertThat(rateLimitService.checkIpRateLimit(null)).isTrue();
        assertThat(rateLimitService.checkIpRateLimit("")).isTrue();
        assertThat(rateLimitService.checkIpRateLimit("   ")).isTrue();
    }
    
    @Test
    @DisplayName("測試檢查空用戶頻率限制")
    void testCheckUserRateLimitWithNullUsername() {
        // When & Then
        assertThat(rateLimitService.checkUserRateLimit(null)).isTrue();
        assertThat(rateLimitService.checkUserRateLimit("")).isTrue();
        assertThat(rateLimitService.checkUserRateLimit("   ")).isTrue();
    }
    
    // ===== 統計信息測試 =====
    
    @Test
    @DisplayName("測試獲取統計信息")
    void testGetStats() {
        // Given - 觸發一些快取操作
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        rateLimitService.isAllowed(ipKey);
        rateLimitService.isAllowed(ipKey);
        
        // When
        String stats = rateLimitService.getStats();
        
        // Then
        assertThat(stats).contains("RateLimit Stats");
        assertThat(stats).contains("Size:");
        assertThat(stats).contains("Hits:");
        assertThat(stats).contains("Misses:");
        assertThat(stats).contains("Hit Rate:");
    }
    
    @Test
    @DisplayName("測試預熱功能")
    void testWarmUp() {
        // When & Then - 應該正常執行，不拋出異常
        rateLimitService.warmUp();
    }
    
    // ===== 併發測試 =====
    
    @Test
    @DisplayName("測試併發訪問同一鍵")
    void testConcurrentAccessSameKey() throws InterruptedException {
        // Given
        String ipKey = IP_KEY_PREFIX + TEST_IP;
        int threadCount = 20;
        int requestsPerThread = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // When - 多個線程同時訪問同一鍵
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        if (rateLimitService.isAllowed(ipKey)) {
                            successCount.incrementAndGet();
                        }
                        Thread.sleep(1); // 小延遲避免過快的請求
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then - 成功的請求數不應該超過限制
        assertThat(successCount.get()).isLessThanOrEqualTo(IP_REQUESTS_PER_MINUTE);
        assertThat(successCount.get()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("測試併發訪問不同鍵")
    void testConcurrentAccessDifferentKeys() throws InterruptedException {
        // Given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // When - 多個線程同時訪問不同鍵
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    String key = IP_KEY_PREFIX + "192.168.1." + threadIndex;
                    // 每個鍵請求5次，都應該成功（因為是不同的鍵）
                    for (int j = 0; j < 5; j++) {
                        if (rateLimitService.isAllowed(key)) {
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
        
        // Then - 所有請求都應該成功（因為是不同的鍵）
        assertThat(successCount.get()).isEqualTo(threadCount * 5);
    }
    
    // ===== 時間窗口測試 =====
    
    @Test
    @DisplayName("測試時間窗口重置")
    void testTimeWindowReset() throws InterruptedException {
        // Given - 創建一個較短限制的服務用於測試
        RateLimitService shortLimitService = new RateLimitService(2, 2, 2, 100);
        String testKey = "test:short";
        
        // When - 消耗所有請求
        assertThat(shortLimitService.isAllowed(testKey)).isTrue();
        assertThat(shortLimitService.isAllowed(testKey)).isTrue();
        assertThat(shortLimitService.isAllowed(testKey)).isFalse(); // 超過限制
        
        // 等待時間窗口重置（全域限制是每秒，所以等待1.1秒）
        Thread.sleep(1100);
        
        // Then - 應該能夠再次請求
        assertThat(shortLimitService.isAllowed("global:requests")).isTrue();
    }
    
    // ===== 邊界情況測試 =====
    
    @Test
    @DisplayName("測試極長的鍵")
    void testVeryLongKey() {
        // Given
        String longKey = "ip:" + "a".repeat(10000);
        
        // When & Then - 應該正常處理
        assertThat(rateLimitService.isAllowed(longKey)).isTrue();
        assertThat(rateLimitService.getRemainingRequests(longKey)).isEqualTo(IP_REQUESTS_PER_MINUTE - 1);
    }
    
    @Test
    @DisplayName("測試包含特殊字符的鍵")
    void testKeyWithSpecialCharacters() {
        // Given
        String specialKey = "ip:192.168.1.1:8080/path?param=value&中文=測試";
        
        // When & Then - 應該正常處理
        assertThat(rateLimitService.isAllowed(specialKey)).isTrue();
        assertThat(rateLimitService.getCurrentRequestCount(specialKey)).isEqualTo(1);
    }
    
    @Test
    @DisplayName("測試不同前綴的變化")
    void testDifferentPrefixVariations() {
        // Given
        String[] keys = {
            "ip:test",
            "IP:test",      // 大寫前綴
            "user:test",
            "USER:test",    // 大寫前綴
            "global:test",
            "GLOBAL:test",  // 大寫前綴
            "other:test"    // 未知前綴
        };
        
        // When & Then - 測試不同前綴的行為
        for (String key : keys) {
            assertThat(rateLimitService.isAllowed(key)).isTrue();
            int remaining = rateLimitService.getRemainingRequests(key);
            assertThat(remaining).isGreaterThan(0);
        }
    }
    
    @Test
    @DisplayName("測試快取大小限制")
    void testCacheSizeLimit() {
        // Given - 創建小快取大小的服務
        RateLimitService smallCacheService = new RateLimitService(10, 10, 10, 5);
        
        // When - 添加超過快取大小的條目
        for (int i = 0; i < 10; i++) {
            String key = "ip:192.168.1." + i;
            smallCacheService.isAllowed(key);
        }
        
        // Then - 服務應該仍然正常工作
        assertThat(smallCacheService.isAllowed("ip:192.168.1.100")).isTrue();
        String stats = smallCacheService.getStats();
        assertThat(stats).contains("RateLimit Stats");
    }
    
    @Test
    @DisplayName("測試零限制配置")
    void testZeroLimitConfiguration() {
        // Given - 創建零限制的服務
        RateLimitService zeroLimitService = new RateLimitService(0, 0, 0, 100);
        
        // When & Then - 所有請求都應該被拒絕
        assertThat(zeroLimitService.checkIpRateLimit(TEST_IP)).isFalse();
        assertThat(zeroLimitService.checkUserRateLimit(TEST_USERNAME)).isFalse();
        assertThat(zeroLimitService.checkGlobalRateLimit()).isFalse();
    }
}