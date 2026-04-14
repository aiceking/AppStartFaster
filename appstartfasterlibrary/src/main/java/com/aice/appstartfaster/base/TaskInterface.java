package com.aice.appstartfaster.base;

import com.aice.appstartfaster.task.AppStartTask;
import java.util.List;
import java.util.concurrent.Executor;

public interface TaskInterface {
    //执行任务所在的线程池
    Executor runOnExecutor();

    //所依赖的父亲们,父亲们执行完了，孩子才能执行
    List<Class<? extends AppStartTask>> getDependsTaskList();

    //在非主线程执行的Task是否需要在被调用await的时候等待，默认不需要，返回true即在Application的onCreate中阻塞，直到该任务执行完
    boolean needWait();


}
