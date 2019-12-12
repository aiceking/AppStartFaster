package com.wxy.appstartfaster.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;

import java.util.List;

public class ProcessUtils {
    //是否在主进程
    public static boolean isMainProcess(Context context){
        return context.getPackageName().equals(getProcessName(context));
    }
    public static String getProcessName(Context context){
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager
                .getRunningAppProcesses();

        int myPid = Process.myPid();

        if(appProcesses == null || appProcesses.size() == 0){
            return null;
        }

        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.processName.equals(context.getPackageName())) {
                if (appProcess.pid == myPid){
                    return appProcess.processName;
                }
            }
        }
        return null;
    }
}
