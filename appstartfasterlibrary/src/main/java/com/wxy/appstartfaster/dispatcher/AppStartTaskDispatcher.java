package com.wxy.appstartfaster.dispatcher;

import android.content.Context;
import android.os.Looper;


import com.wxy.appstartfaster.runnable.AppStartTaskRunnable;
import com.wxy.appstartfaster.task.AppStartTask;
import com.wxy.appstartfaster.util.AppStartTaskLogUtils;
import com.wxy.appstartfaster.util.AppStartTaskSortUtil;
import com.wxy.appstartfaster.util.ProcessUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AppStartTaskDispatcher {
    private static volatile AppStartTaskDispatcher appStartTaskDispatcher;
    //所有任务需要等待的时间
    private  final int WAITING_TIME = 10000;
    private Context context;
    //是否在主进程
    private boolean isInMainProgress;
    //存放每个Task  （key= Class < ? extends AppStartTask>）
    private HashMap<Class<? extends AppStartTask>, AppStartTask> taskHashMap ;
    //每个Task的孩子 （key= Class < ? extends AppStartTask>）
    private HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> taskChildHashMap;
    //通过Add添加进来的所有任务
    private List<AppStartTask> startTaskList;
    //拓扑排序后的所有任务
    private List<AppStartTask> sortTaskList;

    //需要等待的任务总数，用于阻塞
    private CountDownLatch countDownLatch;
    //需要等待的任务总数，用于CountDownLatch
    private AtomicInteger needWaitCount ;
    //所有的任务总数
    private AtomicInteger allCount ;
    private long startTime,finishTime;

    public static AppStartTaskDispatcher getInstance() {
        if (appStartTaskDispatcher==null){
            synchronized (AppStartTaskDispatcher.class){
                if (appStartTaskDispatcher==null){
                    appStartTaskDispatcher=new AppStartTaskDispatcher();
                }
            }
        }
        return appStartTaskDispatcher;
    }

    private AppStartTaskDispatcher() {
        taskHashMap=new HashMap<>();
        taskChildHashMap=new HashMap<>();
        startTaskList=new ArrayList<>();
        needWaitCount=new AtomicInteger();
        allCount=new AtomicInteger();
    }
    public AppStartTaskDispatcher setContext(Context context) {
        this.context = context;
        isInMainProgress= ProcessUtils.isMainProcess(this.context);
        return this;
    }
    public AppStartTaskDispatcher addAppStartTask(AppStartTask appStartTask) {
        startTime= System.currentTimeMillis();
        if (appStartTask==null){
            throw new RuntimeException("addAppStartTask() 传入的appStartTask为null");
        }
        startTaskList.add(appStartTask);
        allCount.getAndIncrement();
        if (ifNeedWait(appStartTask)){
            needWaitCount.getAndIncrement();
        }
        return this;
    }
    public AppStartTaskDispatcher start(){
        if (context==null){
            throw new RuntimeException("context为null，调用start()方法前必须调用setContext()方法");
        }
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw new RuntimeException("start方法必须在主线程调用");
        }

        if (!isInMainProgress){
            AppStartTaskLogUtils.shwoLog("当前进程非主进程");
            return this;
        }
        //拓扑排序，拿到排好序之后的任务队列
        sortTaskList= AppStartTaskSortUtil.getSortResult(startTaskList,taskHashMap,taskChildHashMap);
        printSortTask();
        countDownLatch=new CountDownLatch(needWaitCount.get());
        senAppStartTask();
        return this;
    }
  //输出排好序的Task
    private void printSortTask() {
        StringBuffer stringBuffer=new StringBuffer();
        stringBuffer.append("当前所有任务排好的顺序为：");
        String calssName="";
        for (int i=0;i<sortTaskList.size();i++){
            calssName=sortTaskList.get(i).getClass().getName();
            calssName=calssName.replace(sortTaskList.get(i).getClass().getPackage().getName()+".","");
            if (i==0){
                stringBuffer.append(calssName);
            }else {
                stringBuffer.append("---＞"+calssName);

            }
        }
        AppStartTaskLogUtils.shwoLog(stringBuffer.toString());
    }

    //发送任务
    private void senAppStartTask() {
        //先发送非主线程的任务
        for (AppStartTask appStartTask:sortTaskList){
            if (!appStartTask.isRunOnMainThread()){
                appStartTask.runOnExecutor().execute(new AppStartTaskRunnable(appStartTask,this));
            }
        }
        //先发送主线程的任务
        for (AppStartTask appStartTask:sortTaskList){
            if (appStartTask.isRunOnMainThread()){
                new AppStartTaskRunnable(appStartTask,this).run();
            }
        }
    }
    //通知Children一个前置任务已完成
    public void satNotifyChildren(AppStartTask appStartTask) {
        List<Class<? extends AppStartTask>> arrayList = taskChildHashMap.get(appStartTask.getClass());
        if (arrayList != null && arrayList.size() > 0) {
            for (Class<? extends AppStartTask> aclass : arrayList) {
                taskHashMap.get(aclass).Notify();
            }
        }
    }
    //标记已经完成的Task
    public void markAppStartTaskFinish(AppStartTask appStartTask) {
        AppStartTaskLogUtils.shwoLog("任务完成了："+appStartTask.getClass().getName());
        allCount.getAndDecrement();
        if (ifNeedWait(appStartTask)) {
            countDownLatch.countDown();
            needWaitCount.getAndDecrement();
        }
    }
    //是否需要等待，主线程的任务本来就是阻塞的，所以不用管
    private boolean ifNeedWait(AppStartTask task) {
        return !task.isRunOnMainThread() && task.needWait();
    }
    //等待，阻塞主线程
    public void await() {
        try {
                if (countDownLatch == null) {
                    throw new RuntimeException("在调用await()之前，必须先调用start()");
                }
                countDownLatch.await(WAITING_TIME, TimeUnit.MILLISECONDS);
                finishTime= System.currentTimeMillis()-startTime;
                AppStartTaskLogUtils.shwoLog("启动耗时："+finishTime);
        } catch (InterruptedException e) {
        }
    }
}
