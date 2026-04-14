package com.aice.appstartfaster.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TaskExecutorManager {
    private static volatile TaskExecutorManager sTaskExecutorManager;

    // CPU 核数
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // CPU 密集型线程池大小：固定线程数，避免抢占主线程时间片
    private static final int CPU_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 5));
    // 线程空闲回收时间
    private static final int KEEP_ALIVE_SECONDS = 5;

    // CPU 密集型任务线程池（固定大小，无界队列，无拒绝策略）
    private final ThreadPoolExecutor mCPUThreadPoolExecutor;
    // IO 密集型任务线程池（无界，最大化 IO 并发）
    private final ExecutorService mIOThreadPoolExecutor;

    public static TaskExecutorManager getInstance() {
        if (sTaskExecutorManager == null) {
            synchronized (TaskExecutorManager.class) {
                if (sTaskExecutorManager == null) {
                    sTaskExecutorManager = new TaskExecutorManager();
                }
            }
        }
        return sTaskExecutorManager;
    }

    private TaskExecutorManager() {
        mCPUThreadPoolExecutor = new ThreadPoolExecutor(
                CPU_POOL_SIZE, CPU_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory());
        mCPUThreadPoolExecutor.allowCoreThreadTimeOut(true);
        mIOThreadPoolExecutor = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
    }

    public ThreadPoolExecutor getCPUThreadPoolExecutor() {
        return mCPUThreadPoolExecutor;
    }

    public ExecutorService getIOThreadPoolExecutor() {
        return mIOThreadPoolExecutor;
    }
}
