package com.aice.appstartfaster.util;

import android.util.Log;

import com.aice.appstartfaster.dispatcher.AppStartTaskDispatcher;

public class AppStartTaskLogUtil {
    private static final String TAG = "AppStartTask ";

    public static void showLog(boolean isShowLog, String log) {
        if (isShowLog) {
            Log.e(TAG, log);
        }
    }
}
