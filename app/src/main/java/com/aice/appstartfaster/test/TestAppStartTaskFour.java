package com.aice.appstartfaster.test;

import android.util.Log;


import com.wxy.appstartfaster.task.AppStartTask;

import java.util.ArrayList;
import java.util.List;

public class TestAppStartTaskFour extends AppStartTask {

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(300);
        }catch (Exception e){

        }
        Log.i("Task:","TestAppStartTaskFour执行耗时: "+(System.currentTimeMillis()-start));
    }

    @Override
    public List<Class<? extends AppStartTask>> getDependsTaskList() {
        List<Class<? extends AppStartTask>> dependsTaskList = new ArrayList<>();
        dependsTaskList.add(TestAppStartTaskTwo.class);
        dependsTaskList.add(TestAppStartTaskThree.class);
        return dependsTaskList;
    }

    @Override
    public boolean isRunOnMainThread() {
        return false;
    }

}
