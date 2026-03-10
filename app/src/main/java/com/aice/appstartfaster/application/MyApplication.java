package com.aice.appstartfaster.application;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Process;

import com.aice.appstartfaster.dispatcher.AppStartTaskDispatcher;
import com.aice.appstartfaster.test.TestAppStartTaskFive;
import com.aice.appstartfaster.test.TestAppStartTaskFour;
import com.aice.appstartfaster.test.TestAppStartTaskOne;
import com.aice.appstartfaster.test.TestAppStartTaskThree;
import com.aice.appstartfaster.test.TestAppStartTaskTwo;

import java.util.List;


public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (isMainProcess(this)) {
            AppStartTaskDispatcher.create()
                    .setShowLog(true)
                    .setAllTaskWaitTimeOut(1000)
                    .addAppStartTask(new TestAppStartTaskTwo())
                    .addAppStartTask(new TestAppStartTaskFour())
                    .addAppStartTask(new TestAppStartTaskFive())
                    .addAppStartTask(new TestAppStartTaskThree())
                    .addAppStartTask(new TestAppStartTaskOne())
                    .start()
                    .await();
        }
    }

    public static boolean isMainProcess(Context context) {
        return context.getPackageName().equals(getProcessName(context));
    }

    public static String getProcessName(Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager
                .getRunningAppProcesses();

        int myPid = Process.myPid();

        if (appProcesses == null || appProcesses.size() == 0) {
            return null;
        }

        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.processName.equals(context.getPackageName())) {
                if (appProcess.pid == myPid) {
                    return appProcess.processName;
                }
            }
        }
        return null;
    }
}
