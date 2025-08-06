package dowob.xyz.filemanagementwebdav.component.cache;

import dowob.xyz.filemanagementwebdav.testdata.TestData;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * AuthenticationCache 單元測試
 * 
 * 測試快取的功能性、並發安全性和邊界情況。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName AuthenticationCacheTest
 * @create 2025/8/5
 * @Version 1.0
 **/
@DisplayName("AuthenticationCache 測試")
class AuthenticationCacheTest {
    
    private AuthenticationCache cache;
    private static final int CACHE_SIZE = 100;
    private static final int EXPIRE_MINUTES = 1;
    
    @BeforeEach
    void setUp() {
        // WebDAV子服務中快取永遠啟用，設置較小的過期時間以便測試
        cache = new AuthenticationCache(CACHE_SIZE, EXPIRE_MINUTES);
    }
    
    // ===== 正常情況測試 =====
    
    @Test
    @DisplayName("測試快取命中")
    void testCacheHit() {
        // Given
        String username = TestData.VALID_USERNAME;
        String password = TestData.VALID_PASSWORD;
        String userId = TestData.VALID_USER_ID;
        
        // When - 存入快取
        cache.put(username, password, userId, true);
        
        // Then - 應該能夠取回
        AuthenticationCache.AuthCacheEntry entry = cache.get(username, password);
        assertThat(entry).isNotNull();
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getUsername()).isEqualTo(username);
        assertThat(entry.isAuthenticated()).isTrue();
    }
    
    @Test
    @DisplayName("測試快取未命中")
    void testCacheMiss() {
        // Given
        String username = TestData.VALID_USERNAME;
        String password = TestData.VALID_PASSWORD;
        
        // When - 嘗試獲取不存在的項目
        AuthenticationCache.AuthCacheEntry entry = cache.get(username, password);
        
        // Then
        assertThat(entry).isNull();
    }
    
    @Test
    @DisplayName("測試快取過期")
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void testCacheExpiration() {
        // Given - 創建一個過期時間很短的快取
        AuthenticationCache shortCache = new AuthenticationCache(10, 0); // 0 分鐘過期
        String username = TestData.VALID_USERNAME;
        String password = TestData.VALID_PASSWORD;
        
        // When
        shortCache.put(username, password, TestData.VALID_USER_ID, true);
        
        // 等待一小段時間讓快取過期
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> shortCache.get(username, password) == null);
        
        // Then
        assertThat(shortCache.get(username, password)).isNull();
    }
    
    @Test
    @DisplayName("測試認證失敗的快取")
    void testCacheAuthenticationFailure() {
        // Given
        String username = TestData.VALID_USERNAME;
        String password = "wrongpassword";
        
        // When - 快取失敗的認證結果
        cache.put(username, password, null, false);
        
        // Then
        AuthenticationCache.AuthCacheEntry entry = cache.get(username, password);
        assertThat(entry).isNotNull();
        assertThat(entry.isAuthenticated()).isFalse();
        assertThat(entry.getUserId()).isNull();
    }
    
    // ===== 異常情況測試 =====
    
    @Test
    @DisplayName("測試WebDAV子服務中快取永遠啟用的行為")
    void testCacheAlwaysEnabled() {
        // Given - 在WebDAV子服務中快取永遠啟用
        AuthenticationCache alwaysEnabledCache = new AuthenticationCache(100, 5);
        
        // When
        alwaysEnabledCache.put(TestData.VALID_USERNAME, TestData.VALID_PASSWORD, TestData.VALID_USER_ID, true);
        AuthenticationCache.AuthCacheEntry entry = alwaysEnabledCache.get(TestData.VALID_USERNAME, TestData.VALID_PASSWORD);
        
        // Then - 快取應該正常工作
        assertThat(entry).isNotNull();
        assertThat(entry.isAuthenticated()).isTrue();
        assertThat(entry.getUserId()).isEqualTo(TestData.VALID_USER_ID);
        assertThat(alwaysEnabledCache.getSize()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("測試使特定用戶快取失效")
    void testInvalidateUser() {
        // Given - 添加多個用戶的快取
        String user1 = "user1";
        String user2 = "user2";
        cache.put(user1, "pass1", "id1", true);
        cache.put(user1, "pass2", "id1", true);  // 同一用戶，不同密碼
        cache.put(user2, "pass3", "id2", true);
        
        // When - 使 user1 的快取失效
        cache.invalidateUser(user1);
        
        // Then
        assertThat(cache.get(user1, "pass1")).isNull();
        assertThat(cache.get(user1, "pass2")).isNull();
        assertThat(cache.get(user2, "pass3")).isNotNull();  // user2 不受影響
    }
    
    @Test
    @DisplayName("測試清空所有快取")
    void testInvalidateAll() {
        // Given
        cache.put("user1", "pass1", "id1", true);
        cache.put("user2", "pass2", "id2", true);
        cache.put("user3", "pass3", "id3", true);
        
        // When
        cache.invalidateAll();
        
        // Then
        assertThat(cache.get("user1", "pass1")).isNull();
        assertThat(cache.get("user2", "pass2")).isNull();
        assertThat(cache.get("user3", "pass3")).isNull();
    }
    
    @Test
    @DisplayName("測試並發訪問")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentAccess() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // When - 多線程同時讀寫
        IntStream.range(0, threadCount).forEach(i -> {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String username = "user" + i + "_" + j;
                        String password = "pass" + i + "_" + j;
                        
                        // 寫入
                        cache.put(username, password, "id" + i, true);
                        
                        // 讀取
                        AuthenticationCache.AuthCacheEntry entry = cache.get(username, password);
                        assertThat(entry).isNotNull();
                        assertThat(entry.isAuthenticated()).isTrue();
                    }
                } finally {
                    latch.countDown();
                }
            });
        });
        
        // Then
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }
    
    // ===== 邊界情況測試 =====
    
    @Test
    @DisplayName("測試快取大小限制")
    void testMaxSizeLimit() {
        // Given - 創建小容量快取
        AuthenticationCache smallCache = new AuthenticationCache(3, 60);
        
        // When - 添加超過容量的項目
        smallCache.put("user1", "pass1", "id1", true);
        smallCache.put("user2", "pass2", "id2", true);
        smallCache.put("user3", "pass3", "id3", true);
        smallCache.put("user4", "pass4", "id4", true);  // 應該觸發淘汰
        
        // Then - 最早的項目應該被淘汰（但 Caffeine 使用的是近似 LRU）
        // 由於 Caffeine 的淘汰策略，我們只檢查總數不超過最大值
        int actualSize = 0;
        for (int i = 1; i <= 4; i++) {
            if (smallCache.get("user" + i, "pass" + i) != null) {
                actualSize++;
            }
        }
        assertThat(actualSize).isLessThanOrEqualTo(3);
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("測試空值和空字符串輸入")
    void testNullAndEmptyInputs(String input) {
        // 測試 null 和空字符串
        assertThatCode(() -> {
            cache.put(input, TestData.VALID_PASSWORD, TestData.VALID_USER_ID, true);
            cache.get(input, TestData.VALID_PASSWORD);
        }).doesNotThrowAnyException();
        
        assertThatCode(() -> {
            cache.put(TestData.VALID_USERNAME, input, TestData.VALID_USER_ID, true);
            cache.get(TestData.VALID_USERNAME, input);
        }).doesNotThrowAnyException();
    }
    
    @ParameterizedTest
    @MethodSource("provideSpecialCharacterData")
    @DisplayName("測試特殊字符處理")
    void testSpecialCharacters(String username, String password) {
        // Given
        String userId = TestData.VALID_USER_ID;
        
        // When
        cache.put(username, password, userId, true);
        AuthenticationCache.AuthCacheEntry entry = cache.get(username, password);
        
        // Then
        assertThat(entry).isNotNull();
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.isAuthenticated()).isTrue();
    }
    
    private static Stream<Arguments> provideSpecialCharacterData() {
        return Stream.of(
            Arguments.of(TestData.SPECIAL_USERNAME, TestData.VALID_PASSWORD),
            Arguments.of(TestData.VALID_USERNAME, TestData.SPECIAL_PASSWORD),
            Arguments.of(TestData.USERNAME_WITH_SPACES, TestData.VALID_PASSWORD),
            Arguments.of(TestData.USERNAME_WITH_NEWLINE, TestData.VALID_PASSWORD),
            Arguments.of(TestData.SQL_INJECTION_USERNAME, TestData.VALID_PASSWORD),
            Arguments.of(TestData.XSS_USERNAME, TestData.VALID_PASSWORD),
            Arguments.of(TestData.CONTROL_CHAR_USERNAME, TestData.VALID_PASSWORD)
        );
    }
    
    @ParameterizedTest
    @ValueSource(ints = {100, 1000, 10000})
    @DisplayName("測試超長憑證")
    void testVeryLongCredentials(int length) {
        // Given
        String longUsername = "u".repeat(length);
        String longPassword = "p".repeat(length);
        
        // When & Then - 應該能夠正常處理
        assertThatCode(() -> {
            cache.put(longUsername, longPassword, TestData.VALID_USER_ID, true);
            AuthenticationCache.AuthCacheEntry entry = cache.get(longUsername, longPassword);
            assertThat(entry).isNotNull();
        }).doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("測試快取統計信息")
    void testCacheStats() {
        // Given
        cache.put("user1", "pass1", "id1", true);
        cache.get("user1", "pass1");  // 命中
        cache.get("user2", "pass2");  // 未命中
        
        // When
        String stats = cache.getStats();
        
        // Then
        assertThat(stats).isNotNull();
        assertThat(stats).contains("hitCount");
        assertThat(stats).contains("missCount");
    }
    
    @Test
    @DisplayName("測試相同用戶不同密碼的快取")
    void testSameUserDifferentPasswords() {
        // Given
        String username = TestData.VALID_USERNAME;
        String password1 = "password1";
        String password2 = "password2";
        
        // When
        cache.put(username, password1, TestData.VALID_USER_ID, true);
        cache.put(username, password2, TestData.VALID_USER_ID, false);
        
        // Then - 應該分別快取
        AuthenticationCache.AuthCacheEntry entry1 = cache.get(username, password1);
        AuthenticationCache.AuthCacheEntry entry2 = cache.get(username, password2);
        
        assertThat(entry1).isNotNull();
        assertThat(entry1.isAuthenticated()).isTrue();
        
        assertThat(entry2).isNotNull();
        assertThat(entry2.isAuthenticated()).isFalse();
    }
}