package com.aice.appstartfaster.test;

import android.util.Log;


import com.wxy.appstartfaster.task.AppStartTask;

import java.util.List;

public class TestAppStartTaskOne extends AppStartTask {

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(300);
        }catch (Exception e){
        }
        Log.i("Task:","TestAppStartTaskOne执行耗时: "+(System.currentTimeMillis()-start));
    }

    @Override
    public List<Class<? extends AppStartTask>> getDependsTaskList() {
        return null;
    }

    @Override
    public boolean isRunOnMainThread() {
        return true;
    }


}
