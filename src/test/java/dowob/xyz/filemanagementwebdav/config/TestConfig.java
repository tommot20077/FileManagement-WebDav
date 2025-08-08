package dowob.xyz.filemanagementwebdav.config;

import xyz.dowob.filemanagement.grpc.FileProcessingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 測試配置類
 * 
 * 提供測試環境所需的 Mock Bean 和測試配置。
 * 使用 @TestConfiguration 避免影響正式配置。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName TestConfig
 * @create 2025/8/5
 * @Version 1.0
 **/
@TestConfiguration
public class TestConfig {
    
    /**
     * 提供用於測試的 in-process gRPC Channel
     * 
     * 使用 in-process transport 避免網絡開銷，提高測試速度。
     * 
     * @return 測試用的 ManagedChannel
     */
    @Bean
    @Primary
    public ManagedChannel testChannel() {
        // 生成唯一的服務器名稱
        String serverName = InProcessServerBuilder.generateName();
        
        // 創建 in-process channel
        return InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()  // 在當前線程執行，避免線程切換
                .build();
    }
    
    /**
     * 提供 Mock 的 gRPC Blocking Stub
     * 
     * @return Mock 的 FileProcessingServiceBlockingStub
     */
    @Bean
    @Primary
    public FileProcessingServiceGrpc.FileProcessingServiceBlockingStub mockBlockingStub() {
        return Mockito.mock(FileProcessingServiceGrpc.FileProcessingServiceBlockingStub.class);
    }
    
    /**
     * 提供 Mock 的 gRPC Async Stub
     * 
     * @return Mock 的 FileProcessingServiceStub
     */
    @Bean
    @Primary
    public FileProcessingServiceGrpc.FileProcessingServiceStub mockAsyncStub() {
        return Mockito.mock(FileProcessingServiceGrpc.FileProcessingServiceStub.class);
    }
    
    /**
     * gRPC 清理規則
     * 
     * 自動清理測試中創建的 gRPC 資源，避免資源洩漏。
     * 
     * @return GrpcCleanupRule 實例
     */
    @Bean
    public GrpcCleanupRule grpcCleanup() {
        return new GrpcCleanupRule();
    }
    
    /**
     * 測試用的 gRPC 服務器構建器
     * 
     * @param serverName 服務器名稱
     * @return InProcessServerBuilder
     */
    public static InProcessServerBuilder createTestServerBuilder(String serverName) {
        return InProcessServerBuilder
                .forName(serverName)
                .directExecutor();
    }
    
    /**
     * 優雅關閉 Channel
     * 
     * @param channel 要關閉的 channel
     * @throws InterruptedException 如果關閉被中斷
     */
    public static void shutdownChannel(ManagedChannel channel) throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            channel.awaitTermination(5, TimeUnit.SECONDS);
            if (!channel.isTerminated()) {
                channel.shutdownNow();
            }
        }
    }
}