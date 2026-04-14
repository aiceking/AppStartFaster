package com.aice.appstartfaster.task;

import android.util.Log;

import com.aice.appstartfaster.base.TaskInterface;
import com.aice.appstartfaster.executor.TaskExecutorManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public abstract class AppStartTask implements TaskInterface {

    // 当前Task依赖的Task数量（等父亲们执行完了，孩子才能执行），默认没有依赖
    private final List<Class<? extends AppStartTask>> mCachedDependsTaskList;
    private final CountDownLatch mDepends;

    protected AppStartTask() {
        mCachedDependsTaskList = getDependsTaskList();
        mDepends = new CountDownLatch(mCachedDependsTaskList == null ? 0 : mCachedDependsTaskList.size());
    }

    /**
     * 返回缓存的依赖列表，避免重复调用 getDependsTaskList()。
     * SortUtil 应使用此方法而非直接调用 getDependsTaskList()。
     */
    public List<Class<? extends AppStartTask>> getCachedDependsTaskList() {
        return mCachedDependsTaskList;
    }

    //当前Task等待，让父亲Task先执行
    public void waitToNotify() {
        try {
            mDepends.await();
        } catch (InterruptedException e) {
            Log.w("AppStartTask", "waitToNotify interrupted: " + e.getMessage());
        }
    }

    //执行任务代码
    public abstract void run();

    // 父任务执行完成时由 Dispatcher 调用，解除当前任务的等待
    public void notifyDependencyFinished() {
        mDepends.countDown();
    }

    @Override
    public Executor runOnExecutor() {
        return TaskExecutorManager.getInstance().getIOThreadPoolExecutor();
    }

    @Override
    public List<Class<? extends AppStartTask>> getDependsTaskList() {
        return null;
    }

    @Override
    public boolean needWait() {
        return false;
    }

    //是否在主线程执行
    public abstract boolean isRunOnMainThread();
}
