package com.aice.appstartfaster.dispatcher;

import android.os.Looper;
import android.util.Log;

import com.aice.appstartfaster.runnable.AppStartTaskRunnable;
import com.aice.appstartfaster.util.AppStartTaskLogUtil;
import com.aice.appstartfaster.util.AppStartTaskSortUtil;
import com.aice.appstartfaster.util.model.TaskSortResult;
import com.aice.appstartfaster.task.AppStartTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AppStartTaskDispatcher {
    // Maximum wait time for all tasks (ms)
    private static final int WAITING_TIME = 10000;
    // Map to store each Task (key = Class<? extends AppStartTask>)
    private HashMap<Class<? extends AppStartTask>, AppStartTask> mTaskHashMap;
    // Map to store child tasks for each Task (key = Class<? extends AppStartTask>)
    private HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> mTaskChildHashMap;
    // All tasks added via addAppStartTask()
    private List<AppStartTask> mStartTaskList;
    // All tasks after topological sort
    private List<AppStartTask> mSortTaskList;
    // Main thread tasks after topological sort
    private List<AppStartTask> mSortMainThreadTaskList;
    // Thread pool tasks after topological sort
    private List<AppStartTask> mSortThreadPoolTaskList;
    // Total number of tasks to wait for, used for blocking
    private CountDownLatch mCountDownLatch;
    // Total number of tasks to wait for, used for CountDownLatch
    private AtomicInteger mNeedWaitCount;
    // Start time and finish time for all tasks
    private long mStartTime, mFinishTime;
    // Total timeout for all blocking tasks
    private long mAllTaskWaitTimeOut;
    private boolean isShowLog;

    public static AppStartTaskDispatcher create() {
        return new AppStartTaskDispatcher();
    }

    private AppStartTaskDispatcher() {
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
            throw new RuntimeException("addAppStartTask(): appStartTask must not be null");
        }
        mStartTaskList.add(appStartTask);
        if (ifNeedWait(appStartTask)) {
            mNeedWaitCount.getAndIncrement();
        }
        return this;
    }

    public AppStartTaskDispatcher start() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw new RuntimeException("start() must be called on the main thread");
        }
        mStartTime = System.currentTimeMillis();
        // Topological sort to get the ordered task queue
        TaskSortResult result = AppStartTaskSortUtil.getSortResult(mStartTaskList);
        mSortTaskList     = result.sortedList;
        mTaskHashMap      = result.taskMap;
        mTaskChildHashMap = result.childMap;
        initRealSortTask();
        printSortTask();
        mCountDownLatch = new CountDownLatch(mNeedWaitCount.get());
        dispatchAppStartTask();
        return this;
    }

    // Separate tasks into main thread and thread pool lists
    private void initRealSortTask() {
        for (AppStartTask appStartTask : mSortTaskList) {
            if (appStartTask.isRunOnMainThread()) {
                mSortMainThreadTaskList.add(appStartTask);
            } else {
                mSortThreadPoolTaskList.add(appStartTask);
            }
        }
    }

    // Print the sorted task order
    private void printSortTask() {
        StringBuilder sb = new StringBuilder();
        sb.append("Current task execution order: ");
        for (int i = 0; i < mSortTaskList.size(); i++) {
            String taskName = mSortTaskList.get(i).getClass().getSimpleName();
            if (i != 0) {
                sb.append("---＞");
            }
            sb.append(taskName);
        }
        AppStartTaskLogUtil.showLog(isShowLog, sb.toString());
    }

    // Dispatch tasks
    private void dispatchAppStartTask() {
        // Dispatch thread pool tasks first
        for (AppStartTask appStartTask : mSortThreadPoolTaskList) {
            appStartTask.runOnExecutor().execute(new AppStartTaskRunnable(appStartTask, this));
        }
        // Dispatch main thread tasks after, to prevent them from blocking thread pool task execution
        for (AppStartTask appStartTask : mSortMainThreadTaskList) {
            new AppStartTaskRunnable(appStartTask, this).run();
        }
    }

    // Notify child tasks that a prerequisite task has completed
    public void setNotifyChildren(AppStartTask appStartTask) {
        List<Class<? extends AppStartTask>> arrayList = mTaskChildHashMap.get(appStartTask.getClass());
        if (arrayList != null && arrayList.size() > 0) {
            for (Class<? extends AppStartTask> aclass : arrayList) {
                AppStartTask child = mTaskHashMap.get(aclass);
                if (child != null) {
                    child.notifyDependencyFinished();
                }
            }
        }
    }

    // Mark a task as finished
    public void markAppStartTaskFinish(AppStartTask appStartTask) {
        AppStartTaskLogUtil.showLog(isShowLog, "Task finished: " + appStartTask.getClass().getSimpleName());
        if (ifNeedWait(appStartTask)) {
            mCountDownLatch.countDown();
            mNeedWaitCount.getAndDecrement();
        }
    }

    // Whether the task needs to be waited on; main thread tasks are inherently blocking, so they are excluded
    private boolean ifNeedWait(AppStartTask task) {
        return !task.isRunOnMainThread() && task.needWait();
    }

    // Wait and block the main thread
    public void await() {
        try {
            if (mCountDownLatch == null) {
                throw new RuntimeException("start() must be called before await()");
            }
            if (mAllTaskWaitTimeOut == 0) {
                mAllTaskWaitTimeOut = WAITING_TIME;
            }
            mCountDownLatch.await(mAllTaskWaitTimeOut, TimeUnit.MILLISECONDS);
            mFinishTime = System.currentTimeMillis() - mStartTime;
            AppStartTaskLogUtil.showLog(isShowLog, "Startup time: " + mFinishTime + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w("AppStartTask", "await interrupted: " + e.getMessage());
        }
    }
}
