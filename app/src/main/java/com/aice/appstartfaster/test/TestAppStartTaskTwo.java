package com.aice.appstartfaster.test;

import android.util.Log;

import com.wxy.appstartfaster.executor.TaskExceutorManager;
import com.wxy.appstartfaster.task.AppStartTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class TestAppStartTaskTwo extends AppStartTask {

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(300);
        }catch (Exception e){

        }
        Log.i("Task:","TestAppStartTaskTwo执行耗时: "+(System.currentTimeMillis()-start));
    }

    @Override
    public Executor runOnExecutor() {
        return TaskExceutorManager.getInstance().getCPUThreadPoolExecutor();
    }

    @Override
    public List<Class<? extends AppStartTask>> getDependsTaskList() {
        List<Class<? extends AppStartTask>> dependsTaskList = new ArrayList<>();
        dependsTaskList.add(TestAppStartTaskOne.class);
        return dependsTaskList;
    }

    @Override
    public boolean isRunOnMainThread() {
        return false;
    }


}
