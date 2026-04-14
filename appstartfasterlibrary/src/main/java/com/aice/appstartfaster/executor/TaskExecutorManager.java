package com.aice.appstartfaster.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TaskExecutorManager {
    private static volatile TaskExecutorManager sTaskExecutorManager;
    //CPU 密集型任务的线程池
    private ThreadPoolExecutor mCPUThreadPoolExecutor;
    // IO 密集型任务的线程池
    private ExecutorService mIOThreadPoolExecutor;
    //CPU 核数
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //线程池线程数
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 5));
    //线程池线程数的最大值
    private static final int MAXIMUM_POOL_SIZE = CORE_POOL_SIZE;
    //线程空置回收时间
    private static final int KEEP_ALIVE_SECONDS = 5;
    //线程池队列
    private final BlockingQueue<Runnable> mPoolWorkQueue = new LinkedBlockingQueue<>();
    // 当 CPU 线程池拒绝任务时的备用线程池（复用单例，避免每次 new）
    private final ExecutorService mFallbackExecutor = Executors.newCachedThreadPool();
    private final RejectedExecutionHandler mHandler =
            (r, executor) -> mFallbackExecutor.execute(r);

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

    //初始化线程池
    private TaskExecutorManager() {
        mCPUThreadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                mPoolWorkQueue, Executors.defaultThreadFactory(), mHandler);
        mCPUThreadPoolExecutor.allowCoreThreadTimeOut(true);
        mIOThreadPoolExecutor = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
    }

    //获得cpu密集型线程池
    public ThreadPoolExecutor getCPUThreadPoolExecutor() {
        return mCPUThreadPoolExecutor;
    }

    //获得io密集型线程池
    public ExecutorService getIOThreadPoolExecutor() {
        return mIOThreadPoolExecutor;
    }
}
