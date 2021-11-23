package com.aice.appstartfaster.runnable;

import android.os.Process;

import com.aice.appstartfaster.dispatcher.AppStartTaskDispatcher;
import com.aice.appstartfaster.task.AppStartTask;


public class AppStartTaskRunnable implements Runnable {
    private AppStartTask appStartTask;
    private AppStartTaskDispatcher appStartTaskDispatcher;

    public AppStartTaskRunnable(AppStartTask appStartTask, AppStartTaskDispatcher appStartTaskDispatcher) {
        this.appStartTask = appStartTask;
        this.appStartTaskDispatcher = appStartTaskDispatcher;
    }

    @Override
    public void run() {
        Process.setThreadPriority(appStartTask.priority());
        appStartTask.waitToNotify();
        appStartTask.run();
        appStartTaskDispatcher.setNotifyChildren(appStartTask);
        appStartTaskDispatcher.markAppStartTaskFinish(appStartTask);
    }
}
