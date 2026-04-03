package com.vaishnavaachal.url_shortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async Thread Pool Configuration
 *
 * Used for "Blind Updates" — incrementing click counts asynchronously so that
 * the redirect response is NOT blocked by a database write operation.
 *
 * Interview talking point:
 *   Without @Async, every redirect would block for a DB write → adds ~10-30ms latency.
 *   With @Async, the redirect fires immediately and the counter updates in background.
 */
@Configuration
public class AsyncConfig {

    @Value("${async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:100}")
    private int queueCapacity;

    @Bean(name = "clickCountExecutor")
    public Executor clickCountExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("click-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);  // Graceful shutdown
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
