# AppStartFaster 库深度重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 `appstartfasterlibrary` 中的职责边界模糊、健壮性缺陷和代码规范问题，共 8 个优化点。

**Architecture:** `AppStartTaskSortUtil` 由副作用式填充外部 Map 改为返回 `TaskSortResult`；`AppStartTask` 缓存依赖列表并修正方法命名；`AppStartTaskDispatcher` 从 `TaskSortResult` 取数据，修复 NPE 和中断处理；`TaskExecutorManager` 改为复用单例 fallback 线程池。

**Tech Stack:** Java 17, Android Library (AAR), JUnit 4, Gradle 8.13

---

## 文件变更清单

| 操作 | 路径 |
|---|---|
| 新增 | `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/model/TaskSortResult.java` |
| 新增 | `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java` |
| 修改 | `appstartfasterlibrary/build.gradle` |
| 修改 | `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java` |
| 修改 | `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java` |
| 修改 | `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java` |
| 修改 | `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java` |
| 修改 | `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskLogUtil.java` |
| 删除 | `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/model/TaskSortModel.java` |

---

## Task 1: 新增 TaskSortResult + 开启单元测试支持

**Files:**
- Create: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/model/TaskSortResult.java`
- Modify: `appstartfasterlibrary/build.gradle`

- [ ] **Step 1: 创建 TaskSortResult.java**

```java
package com.aice.appstartfaster.util.model;

import com.aice.appstartfaster.task.AppStartTask;

import java.util.HashMap;
import java.util.List;

public class TaskSortResult {
    public final List<AppStartTask> sortedList;
    public final HashMap<Class<? extends AppStartTask>, AppStartTask> taskMap;
    public final HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> childMap;

    TaskSortResult(List<AppStartTask> sortedList,
                   HashMap<Class<? extends AppStartTask>, AppStartTask> taskMap,
                   HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> childMap) {
        this.sortedList = sortedList;
        this.taskMap    = taskMap;
        this.childMap   = childMap;
    }
}
```

- [ ] **Step 2: 在 appstartfasterlibrary/build.gradle 的 android{} 块末尾添加 testOptions**

在 `compileOptions { ... }` 块之后添加：

```groovy
    testOptions {
        unitTests {
            returnDefaultValues = true
        }
    }
```

- [ ] **Step 3: 验证编译通过**

```bash
./gradlew :appstartfasterlibrary:assembleRelease
```

期望输出：`BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/model/TaskSortResult.java
git add appstartfasterlibrary/build.gradle
git commit -m "feat: 新增 TaskSortResult，开启单元测试 returnDefaultValues"
```

---

## Task 2: 重构 AppStartTask（缓存依赖列表 + getCachedDependsTaskList）

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java`

本步骤只添加构造函数和 `getCachedDependsTaskList()`，保留 `Notify()` 原名（Task 5 再改名），确保现有代码仍可编译。

- [ ] **Step 1: 用以下内容完整替换 AppStartTask.java**

```java
package com.aice.appstartfaster.task;

import android.os.Process;
import android.util.Log;

import com.aice.appstartfaster.base.TaskInterface;
import com.aice.appstartfaster.executor.TaskExecutorManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public abstract class AppStartTask implements TaskInterface {

    // 当前Task依赖的Task数量（等父亲们执行完了，孩子才能执行），默认没有依赖
    private final List<Class<? extends AppStartTask>> mCachedDependsTaskList;
    private final CountDownLatch mDepends;

    protected AppStartTask() {
        mCachedDependsTaskList = getDependsTaskList();
        mDepends = new CountDownLatch(mCachedDependsTaskList == null ? 0 : mCachedDependsTaskList.size());
    }

    /**
     * 返回缓存的依赖列表，避免重复调用 getDependsTaskList()。
     * SortUtil 应使用此方法而非直接调用 getDependsTaskList()。
     */
    public List<Class<? extends AppStartTask>> getCachedDependsTaskList() {
        return mCachedDependsTaskList;
    }

    //当前Task等待，让父亲Task先执行
    public void waitToNotify() {
        try {
            mDepends.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w("AppStartTask", "waitToNotify interrupted: " + e.getMessage());
        }
    }

    @Override
    public int priority() {
        return Process.THREAD_PRIORITY_BACKGROUND;
    }

    //执行任务代码
    public abstract void run();

    //他的父亲们执行完了一个（Task 5 将改名为 notifyDependencyFinished）
    public void Notify() {
        mDepends.countDown();
    }

    @Override
    public Executor runOnExecutor() {
        return TaskExecutorManager.getInstance().getIOThreadPoolExecutor();
    }

    @Override
    public List<Class<? extends AppStartTask>> getDependsTaskList() {
        return null;
    }

    @Override
    public boolean needWait() {
        return false;
    }

    //是否在主线程执行
    public abstract boolean isRunOnMainThread();
}
```

- [ ] **Step 2: 验证编译通过**

```bash
./gradlew :appstartfasterlibrary:assembleRelease
```

期望输出：`BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java
git commit -m "refactor: AppStartTask 缓存依赖列表，修复 InterruptedException 处理"
```

---

## Task 3: 编写 AppStartTaskSortUtil 单元测试（先写失败测试）

**Files:**
- Create: `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java`

- [ ] **Step 1: 创建测试文件**

```java
package com.aice.appstartfaster.util;

import com.aice.appstartfaster.task.AppStartTask;
import com.aice.appstartfaster.util.model.TaskSortResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppStartTaskSortUtilTest {

    // ---- 测试用任务类 ----

    static class TaskA extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return false; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() { return null; }
    }

    static class TaskB extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return false; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
            return Collections.singletonList(TaskA.class);
        }
    }

    static class TaskC extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return false; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
            return Collections.singletonList(TaskB.class);
        }
    }

    // 循环依赖：X → Y → X
    static class TaskX extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return false; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
            return Collections.singletonList(TaskY.class);
        }
    }

    static class TaskY extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return false; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
            return Collections.singletonList(TaskX.class);
        }
    }

    // ---- 测试用例 ----

    @Test
    public void getSortResult_linearDependency_correctOrder() {
        // 乱序输入：C → B → A，期望输出：A → B → C
        List<AppStartTask> tasks = Arrays.asList(new TaskC(), new TaskA(), new TaskB());
        TaskSortResult result = AppStartTaskSortUtil.getSortResult(tasks);

        assertEquals(3, result.sortedList.size());
        assertTrue(result.sortedList.get(0) instanceof TaskA);
        assertTrue(result.sortedList.get(1) instanceof TaskB);
        assertTrue(result.sortedList.get(2) instanceof TaskC);
    }

    @Test
    public void getSortResult_singleTask_returnsIt() {
        List<AppStartTask> tasks = Collections.singletonList(new TaskA());
        TaskSortResult result = AppStartTaskSortUtil.getSortResult(tasks);

        assertEquals(1, result.sortedList.size());
        assertNotNull(result.taskMap);
        assertNotNull(result.childMap);
        assertTrue(result.taskMap.containsKey(TaskA.class));
    }

    @Test(expected = RuntimeException.class)
    public void getSortResult_duplicateTask_throwsException() {
        List<AppStartTask> tasks = Arrays.asList(new TaskA(), new TaskA());
        AppStartTaskSortUtil.getSortResult(tasks);
    }

    @Test(expected = RuntimeException.class)
    public void getSortResult_cyclicDependency_throwsException() {
        List<AppStartTask> tasks = Arrays.asList(new TaskX(), new TaskY());
        AppStartTaskSortUtil.getSortResult(tasks);
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败（签名不匹配）**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：`BUILD FAILED` 或编译错误（`getSortResult` 签名不匹配），属于正常现象。

- [ ] **Step 3: 提交失败测试**

```bash
git add appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java
git commit -m "test: 添加 AppStartTaskSortUtil 单元测试（当前失败）"
```

---

## Task 4: 重构 AppStartTaskSortUtil（让测试通过）

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java`

- [ ] **Step 1: 用以下内容完整替换 AppStartTaskSortUtil.java**

```java
package com.aice.appstartfaster.util;

import com.aice.appstartfaster.task.AppStartTask;
import com.aice.appstartfaster.util.model.TaskSortResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

public class AppStartTaskSortUtil {

    /**
     * 拓扑排序（Kahn 算法）。
     * 纯函数：不修改任何外部状态，结果封装在 TaskSortResult 中返回。
     *
     * @throws RuntimeException 任务重复或存在循环依赖时抛出
     */
    public static TaskSortResult getSortResult(List<AppStartTask> startTaskList) {
        List<AppStartTask> sortedList = new ArrayList<>();
        HashMap<Class<? extends AppStartTask>, Integer> inDegreeMap = new HashMap<>();
        HashMap<Class<? extends AppStartTask>, AppStartTask> taskMap = new HashMap<>();
        HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> childMap = new HashMap<>();
        Deque<Class<? extends AppStartTask>> zeroInDegreeQueue = new ArrayDeque<>();

        // 第一轮：建立入度表和 taskMap，初始化 childMap
        for (AppStartTask task : startTaskList) {
            if (inDegreeMap.containsKey(task.getClass())) {
                throw new RuntimeException("Duplicate task: " + task.getClass());
            }
            taskMap.put(task.getClass(), task);
            List<Class<? extends AppStartTask>> depends = task.getCachedDependsTaskList();
            int inDegree = depends == null ? 0 : depends.size();
            inDegreeMap.put(task.getClass(), inDegree);
            childMap.put(task.getClass(), new ArrayList<>());
            if (inDegree == 0) {
                zeroInDegreeQueue.offer(task.getClass());
            }
        }

        // 第二轮：填充 childMap（每个父节点记录其子节点）
        for (AppStartTask task : startTaskList) {
            List<Class<? extends AppStartTask>> depends = task.getCachedDependsTaskList();
            if (depends != null) {
                for (Class<? extends AppStartTask> parent : depends) {
                    childMap.get(parent).add(task.getClass());
                }
            }
        }

        // Kahn 算法主循环
        while (!zeroInDegreeQueue.isEmpty()) {
            Class<? extends AppStartTask> cls = zeroInDegreeQueue.poll();
            sortedList.add(taskMap.get(cls));
            for (Class<? extends AppStartTask> child : childMap.get(cls)) {
                int newDegree = inDegreeMap.get(child) - 1;
                inDegreeMap.put(child, newDegree);
                if (newDegree == 0) {
                    zeroInDegreeQueue.offer(child);
                }
            }
        }

        if (sortedList.size() != startTaskList.size()) {
            throw new RuntimeException("Cycle detected in task dependency graph");
        }

        return new TaskSortResult(sortedList, taskMap, childMap);
    }
}
```

- [ ] **Step 2: 运行测试，确认全部通过**

```bash
./gradlew :appstartfasterlibrary:test
```

期望输出：
```
AppStartTaskSortUtilTest > getSortResult_linearDependency_correctOrder PASSED
AppStartTaskSortUtilTest > getSortResult_singleTask_returnsIt PASSED
AppStartTaskSortUtilTest > getSortResult_duplicateTask_throwsException PASSED
AppStartTaskSortUtilTest > getSortResult_cyclicDependency_throwsException PASSED

BUILD SUCCESSFUL
```

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java
git commit -m "refactor: AppStartTaskSortUtil 改为返回 TaskSortResult，消除副作用"
```

---

## Task 5: 重命名 Notify() + 更新 AppStartTaskDispatcher.setNotifyChildren()

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java`
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`

两个文件必须在同一步骤修改，否则编译中断。

- [ ] **Step 1: 在 AppStartTask.java 中将 Notify() 改名为 notifyDependencyFinished()**

将：
```java
    //他的父亲们执行完了一个（Task 5 将改名为 notifyDependencyFinished）
    public void Notify() {
        mDepends.countDown();
    }
```

替换为：
```java
    // 父任务执行完成时由 Dispatcher 调用，解除当前任务的等待
    public void notifyDependencyFinished() {
        mDepends.countDown();
    }
```

- [ ] **Step 2: 在 AppStartTaskDispatcher.java 中更新 setNotifyChildren()，同时添加 NPE 防护**

将：
```java
    // Notify child tasks that a prerequisite task has completed
    public void setNotifyChildren(AppStartTask appStartTask) {
        List<Class<? extends AppStartTask>> arrayList = mTaskChildHashMap.get(appStartTask.getClass());
        if (arrayList != null && arrayList.size() > 0) {
            for (Class<? extends AppStartTask> aclass : arrayList) {
                mTaskHashMap.get(aclass).Notify();
            }
        }
    }
```

替换为：
```java
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
```

- [ ] **Step 3: 运行测试，确认仍然通过**

```bash
./gradlew :appstartfasterlibrary:test
```

期望输出：`BUILD SUCCESSFUL`（4 个测试全部 PASSED）

- [ ] **Step 4: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java
git commit -m "refactor: Notify() 改名为 notifyDependencyFinished()，setNotifyChildren() 添加 NPE 防护"
```

---

## Task 6: 重构 AppStartTaskDispatcher（使用 TaskSortResult + 修复 await）

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`

- [ ] **Step 1: 用以下内容完整替换 AppStartTaskDispatcher.java**

```java
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
```

- [ ] **Step 2: 运行测试，确认全部通过**

```bash
./gradlew :appstartfasterlibrary:test
```

期望输出：`BUILD SUCCESSFUL`（4 个测试全部 PASSED）

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java
git commit -m "refactor: Dispatcher 从 TaskSortResult 取数据，修复 await() 中断处理，移除 HashMap 冗余初始化"
```

---

## Task 7: 删除 TaskSortModel

**Files:**
- Delete: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/model/TaskSortModel.java`

- [ ] **Step 1: 删除文件**

```bash
rm appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/model/TaskSortModel.java
```

- [ ] **Step 2: 验证编译和测试仍然通过**

```bash
./gradlew :appstartfasterlibrary:test
```

期望输出：`BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add -u appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/model/TaskSortModel.java
git commit -m "refactor: 删除无意义包装类 TaskSortModel"
```

---

## Task 8: 修复 TaskExecutorManager（复用 fallback 线程池）

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java`

- [ ] **Step 1: 用以下内容完整替换 TaskExecutorManager.java**

```java
package com.aice.appstartfaster.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TaskExecutorManager {
    private static volatile TaskExecutorManager sTaskExecutorManager;
    //CPU 密集型任务的线程池
    private ThreadPoolExecutor mCPUThreadPoolExecutor;
    // IO 密集型任务的线程池
    private ExecutorService mIOThreadPoolExecutor;
    //CPU 核数
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //线程池线程数
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 5));
    //线程池线程数的最大值
    private static final int MAXIMUM_POOL_SIZE = CORE_POOL_SIZE;
    //线程空置回收时间
    private static final int KEEP_ALIVE_SECONDS = 5;
    //线程池队列
    private final BlockingQueue<Runnable> mPoolWorkQueue = new LinkedBlockingQueue<>();
    // 当 CPU 线程池拒绝任务时的备用线程池（复用单例，避免每次 new）
    private final ExecutorService mFallbackExecutor = Executors.newCachedThreadPool();
    private final RejectedExecutionHandler mHandler =
            (r, executor) -> mFallbackExecutor.execute(r);

    public static TaskExecutorManager getInstance() {
        if (sTaskExecutorManager == null) {
            synchronized (TaskExecutorManager.class) {
                if (sTaskExecutorManager == null) {
                    sTaskExecutorManager = new TaskExecutorManager();
                }
            }
        }
        return sTaskExecutorManager;
    }

    //初始化线程池
    private TaskExecutorManager() {
        mCPUThreadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                mPoolWorkQueue, Executors.defaultThreadFactory(), mHandler);
        mCPUThreadPoolExecutor.allowCoreThreadTimeOut(true);
        mIOThreadPoolExecutor = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
    }

    //获得cpu密集型线程池
    public ThreadPoolExecutor getCPUThreadPoolExecutor() {
        return mCPUThreadPoolExecutor;
    }

    //获得io密集型线程池
    public ExecutorService getIOThreadPoolExecutor() {
        return mIOThreadPoolExecutor;
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望输出：`BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java
git commit -m "fix: TaskExecutorManager 拒绝策略改为复用单例 fallback 线程池"
```

---

## Task 9: 修复 AppStartTaskLogUtil（日志级别 + 清除无用 import）

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskLogUtil.java`

- [ ] **Step 1: 用以下内容完整替换 AppStartTaskLogUtil.java**

```java
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
```

- [ ] **Step 2: 运行测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望输出：`BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskLogUtil.java
git commit -m "fix: AppStartTaskLogUtil 日志改用 Log.i()，删除无用 import"
```

---

## Task 10: 最终集成验证

- [ ] **Step 1: 构建 Release AAR**

```bash
./gradlew :appstartfasterlibrary:assembleRelease
```

期望输出：`BUILD SUCCESSFUL`

- [ ] **Step 2: 构建 Demo App（集成验证）**

```bash
./gradlew :app:assembleDebug
```

期望输出：`BUILD SUCCESSFUL`

- [ ] **Step 3: 运行全部测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望输出：4 个测试全部 `PASSED`，`BUILD SUCCESSFUL`

- [ ] **Step 4: 提交最终验证记录**

```bash
git commit --allow-empty -m "chore: 深度重构全部完成，8 项优化点已落地"
```
