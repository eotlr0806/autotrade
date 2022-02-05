package com.coin.autotrade.common;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configurable
public class AsyncCommon extends AsyncConfigurerSupport {

    /**
     * Async for thread
     */
    @Override
    public Executor getAsyncExecutor() {
        /** Thread pool size */
        int THREAD_MIN      = 10;
        int THREAD_MAX      = 100;
        int THREAD_CAPACITY = 200;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(THREAD_MIN);
        executor.setMaxPoolSize(THREAD_MAX);
        executor.setQueueCapacity(THREAD_CAPACITY);
        executor.setThreadNamePrefix("Executor - ");
        executor.initialize();

        return executor;
    }
}
