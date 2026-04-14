package com.aice.appstartfaster.util;

import android.util.Log;

public class AppStartTaskLogUtil {
    private static final String TAG = "AppStartTask ";

    public static void showLog(boolean isShowLog, String log) {
        if (isShowLog) {
            Log.i(TAG, log);
        }
    }
}
