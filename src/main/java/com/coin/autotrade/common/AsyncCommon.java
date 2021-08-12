package com.coin.autotrade.common;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configurable
public class AsyncCommon extends AsyncConfigurerSupport {

    /**
     * Async for thread
     * @return
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(DataCommon.THREAD_MIN);
        executor.setMaxPoolSize(DataCommon.THREAD_MAX);
        executor.setQueueCapacity(DataCommon.THREAD_CAPACITY);
        executor.setThreadNamePrefix("Executor - ");
        executor.initialize();

        return executor;
    }
}
