package com.wxy.appstartfaster.util;

import android.util.Log;

import com.wxy.appstartfaster.dispatcher.AppStartTaskDispatcher;

public class AppStartTaskLogUtil {
    private static final String TAG="AppStartTask: ";
    public static void showLog(String log){
        if (AppStartTaskDispatcher.getInstance().isShowLog()){
            Log.e(TAG,log);
        }
    }
}
