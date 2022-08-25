package com.aice.appstartfaster.dispatcher;

import android.os.Looper;


import com.aice.appstartfaster.runnable.AppStartTaskRunnable;
import com.aice.appstartfaster.util.AppStartTaskLogUtil;
import com.aice.appstartfaster.util.AppStartTaskSortUtil;
import com.aice.appstartfaster.task.AppStartTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AppStartTaskDispatcher {
    //所有任务需要等待的时间
    private static final int WAITING_TIME = 10000;
    //存放每个Task  （key= Class < ? extends AppStartTask>）
    private HashMap<Class<? extends AppStartTask>, AppStartTask> mTaskHashMap;
    //每个Task的孩子 （key= Class < ? extends AppStartTask>）
    private HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> mTaskChildHashMap;
    //通过Add添加进来的所有任务
    private List<AppStartTask> mStartTaskList;
    //拓扑排序后的所有任务
    private List<AppStartTask> mSortTaskList;
    //拓扑排序后的主线程的任务
    private List<AppStartTask> mSortMainThreadTaskList;
    //拓扑排序后的子线程的任务
    private List<AppStartTask> mSortThreadPoolTaskList;
    //需要等待的任务总数，用于阻塞
    private CountDownLatch mCountDownLatch;
    //需要等待的任务总数，用于CountDownLatch
    private AtomicInteger mNeedWaitCount;
    //所有的任务开始时间，结束时间
    private long mStartTime, mFinishTime;
    //所有阻塞任务的总超时时间
    private long mAllTaskWaitTimeOut;
    private boolean isShowLog;

    public static AppStartTaskDispatcher create() {
        return new AppStartTaskDispatcher();
    }

    private AppStartTaskDispatcher() {
        mTaskHashMap = new HashMap<>();
        mTaskChildHashMap = new HashMap<>();
        mStartTaskList = new ArrayList<>();
        mNeedWaitCount = new AtomicInteger();
        mSortMainThreadTaskList = new ArrayList<>();
        mSortThreadPoolTaskList = new ArrayList<>();
    }

    public AppStartTaskDispatcher setAllTaskWaitTimeOut(long allTaskWaitTimeOut) {
        mAllTaskWaitTimeOut = allTaskWaitTimeOut;
        return this;
    }

    public AppStartTaskDispatcher setShowLog(boolean showLog) {
        isShowLog = showLog;
        return this;
    }

    public AppStartTaskDispatcher addAppStartTask(AppStartTask appStartTask) {
        if (appStartTask == null) {
            throw new RuntimeException("addAppStartTask() 传入的appStartTask为null");
        }
        mStartTaskList.add(appStartTask);
        if (ifNeedWait(appStartTask)) {
            mNeedWaitCount.getAndIncrement();
        }
        return this;
    }

    public AppStartTaskDispatcher start() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw new RuntimeException("start方法必须在主线程调用");
        }
        mStartTime = System.currentTimeMillis();
        //拓扑排序，拿到排好序之后的任务队列
        mSortTaskList = AppStartTaskSortUtil.getSortResult(mStartTaskList, mTaskHashMap, mTaskChildHashMap);
        initRealSortTask();
        printSortTask();
        mCountDownLatch = new CountDownLatch(mNeedWaitCount.get());
        dispatchAppStartTask();
        return this;
    }

    //分别处理主线程和子线程的任务
    private void initRealSortTask() {
        for (AppStartTask appStartTask : mSortTaskList) {
            if (appStartTask.isRunOnMainThread()) {
                mSortMainThreadTaskList.add(appStartTask);
            } else {
                mSortThreadPoolTaskList.add(appStartTask);
            }
        }
    }

    //输出排好序的Task
    private void printSortTask() {
        StringBuilder sb = new StringBuilder();
        sb.append("当前所有任务排好的顺序为：");
        for (int i = 0; i < mSortTaskList.size(); i++) {
            String taskName = mSortTaskList.get(i).getClass().getSimpleName();
            if (i != 0) {
                sb.append("---＞");
            }
            sb.append(taskName);
        }
        AppStartTaskLogUtil.showLog(isShowLog, sb.toString());
    }

    //发送任务
    private void dispatchAppStartTask() {
        //再发送非主线程的任务
        for (AppStartTask appStartTask : mSortThreadPoolTaskList) {
            appStartTask.runOnExecutor().execute(new AppStartTaskRunnable(appStartTask, this));
        }
        //再发送主线程的任务，防止主线程任务阻塞，导致子线程任务不能立刻执行
        for (AppStartTask appStartTask : mSortMainThreadTaskList) {
            new AppStartTaskRunnable(appStartTask, this).run();
        }
    }

    //通知Children一个前置任务已完成
    public void setNotifyChildren(AppStartTask appStartTask) {
        List<Class<? extends AppStartTask>> arrayList = mTaskChildHashMap.get(appStartTask.getClass());
        if (arrayList != null && arrayList.size() > 0) {
            for (Class<? extends AppStartTask> aclass : arrayList) {
                mTaskHashMap.get(aclass).Notify();
            }
        }
    }

    //标记已经完成的Task
    public void markAppStartTaskFinish(AppStartTask appStartTask) {
        AppStartTaskLogUtil.showLog(isShowLog, "任务完成了：" + appStartTask.getClass().getSimpleName());
        if (ifNeedWait(appStartTask)) {
            mCountDownLatch.countDown();
            mNeedWaitCount.getAndDecrement();
        }
    }

    //是否需要等待，主线程的任务本来就是阻塞的，所以不用管
    private boolean ifNeedWait(AppStartTask task) {
        return !task.isRunOnMainThread() && task.needWait();
    }

    //等待，阻塞主线程
    public void await() {
        try {
            if (mCountDownLatch == null) {
                throw new RuntimeException("在调用await()之前，必须先调用start()");
            }
            if (mAllTaskWaitTimeOut == 0) {
                mAllTaskWaitTimeOut = WAITING_TIME;
            }
            mCountDownLatch.await(mAllTaskWaitTimeOut, TimeUnit.MILLISECONDS);
            mFinishTime = System.currentTimeMillis() - mStartTime;
            AppStartTaskLogUtil.showLog(isShowLog, "启动耗时：" + mFinishTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
