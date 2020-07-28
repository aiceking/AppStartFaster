package com.aice.appstartfaster.application;

import android.app.Application;
import android.content.Context;

import com.aice.appstartfaster.multidex.MultidexUtils;
import com.aice.appstartfaster.test.TestAppStartTaskFive;
import com.aice.appstartfaster.test.TestAppStartTaskFour;
import com.aice.appstartfaster.test.TestAppStartTaskOne;
import com.aice.appstartfaster.test.TestAppStartTaskThree;
import com.aice.appstartfaster.test.TestAppStartTaskTwo;
import com.wxy.appstartfaster.dispatcher.AppStartTaskDispatcher;


public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (MultidexUtils.isMainProcess(this)){
        AppStartTaskDispatcher.getInstance()
                .setContext(this)
                .setShowLog(true)
                .addAppStartTask(new TestAppStartTaskTwo())
                .addAppStartTask(new TestAppStartTaskFour())
                .addAppStartTask(new TestAppStartTaskFive())
                .addAppStartTask(new TestAppStartTaskThree())
                .addAppStartTask(new TestAppStartTaskOne())
                .start()
                .await();
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        boolean isMainProcess = MultidexUtils.isMainProcess(base);
        if (isMainProcess && !MultidexUtils.isVMMultidexCapable()){
            MultidexUtils.loadMultiDex(base);
        }else {
            MultidexUtils.preNewActivity();
        }
    }
}
