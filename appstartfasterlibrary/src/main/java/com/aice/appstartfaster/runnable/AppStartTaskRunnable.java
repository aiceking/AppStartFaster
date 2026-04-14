package com.aice.appstartfaster.runnable;

import android.util.Log;

import com.aice.appstartfaster.dispatcher.AppStartTaskDispatcher;
import com.aice.appstartfaster.task.AppStartTask;

public class AppStartTaskRunnable implements Runnable {
    private final AppStartTask mAppStartTask;
    private final AppStartTaskDispatcher mAppStartTaskDispatcher;

    public AppStartTaskRunnable(AppStartTask appStartTask, AppStartTaskDispatcher appStartTaskDispatcher) {
        this.mAppStartTask = appStartTask;
        this.mAppStartTaskDispatcher = appStartTaskDispatcher;
    }

    @Override
    public void run() {
        mAppStartTask.waitToNotify();
        try {
            mAppStartTask.run();
        } catch (Throwable t) {
            Log.e("AppStartTask", "Task failed: " + mAppStartTask.getClass().getSimpleName(), t);
            // 不 rethrow：框架设计为单个任务失败不中断整体启动流程。
            // 主线程任务若 rethrow 会直接 crash Application；
            // finally 块已保证子任务通知链和 await() latch 正常递减。
        } finally {
            mAppStartTaskDispatcher.setNotifyChildren(mAppStartTask);
            mAppStartTaskDispatcher.markAppStartTaskFinish(mAppStartTask);
        }
    }
}
