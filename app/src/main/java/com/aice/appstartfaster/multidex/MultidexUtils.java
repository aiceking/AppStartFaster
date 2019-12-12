package com.aice.appstartfaster.multidex;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;


import com.aice.appstartfaster.ui.MainActivity;
import com.aice.appstartfaster.ui.loadmultidex.LoadMultiDexActivity;
import com.aice.appstartfaster.ui.splash.SplashActivity;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.multidex.MultiDex;

public class MultidexUtils {
    private static final String TAG="MultidexUtils";
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
    public static boolean isVMMultidexCapable(){
        return isVMMultidexCapable(System.getProperty("java.vm.version"));
    }
    //MultiDex 拷出来的的方法，判断VM是否支持多dex
    public static boolean isVMMultidexCapable(String versionString) {
        boolean isMultidexCapable = false;
        if (versionString != null) {
            Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?").matcher(versionString);
            if (matcher.matches()) {
                try {
                    int major = Integer.parseInt(matcher.group(1));
                    int minor = Integer.parseInt(matcher.group(2));
                    isMultidexCapable = major > 2 || major == 2 && minor >= 1;
                } catch (NumberFormatException var5) {
                }
            }
        }
        Log.i("MultiDex", "VM with version " + versionString + (isMultidexCapable ? " has multidex support" : " does not have multidex support"));
        return isMultidexCapable;
    }

    public static void loadMultiDex(Context context) {
        newTempFile(context); //创建临时文件
        //启动另一个进程去加载MultiDex
        Intent intent = new Intent(context, LoadMultiDexActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        //检查MultiDex是否安装完（安装完会删除临时文件）
        checkUntilLoadDexSuccess(context);
        //另一个进程以及加载 MultiDex，有缓存了，所以主进程再加载就很快了。
        //为什么主进程要再加载，因为每个进程都有一个ClassLoader
        long startTime = System.currentTimeMillis();
        MultiDex.install(context);
        Log.d(TAG, "第二次 MultiDex.install 结束，耗时: " + (System.currentTimeMillis() - startTime));
        preNewActivity();
    }
    //创建一个临时文件，MultiDex install 成功后删除
    public static void newTempFile(Context context) {
        try {
            File file = new File(context.getCacheDir().getAbsolutePath(), "load_dex.tmp");
            if (!file.exists()) {
                Log.d(TAG, "newTempFile: ");
                file.createNewFile();
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
    /**
     * 检查MultiDex是否安装完,通过判断临时文件是否被删除
     * @param context
     * @return
     */
    public static void checkUntilLoadDexSuccess(Context context) {
        File file = new File(context.getCacheDir().getAbsolutePath(), "load_dex.tmp");
        int i = 0;
        int waitTime = 100; //睡眠时间
        try {
            Log.d(TAG, "checkUntilLoadDexSuccess: >>> ");
            while (file.exists()) {
                Thread.sleep(waitTime);
                i++;
                Log.d(TAG, "checkUntilLoadDexSuccess: sleep count = " + i);
                if (i > 400) {
                    Log.d(TAG, "checkUntilLoadDexSuccess: 超时，等待时间： " + (waitTime * i));
                    break;
                }
            }
            Log.d(TAG, "checkUntilLoadDexSuccess: 轮循结束，等待时间 " +(waitTime * i));

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public static void preNewActivity() {
        long startTime = System.currentTimeMillis();
        SplashActivity splashActivity = new SplashActivity();
        MainActivity mainActivity = new MainActivity();
        Log.d(TAG, "preNewActivity 耗时: " + (System.currentTimeMillis() - startTime));
    }
}

