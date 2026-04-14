# AppStartFaster Library 健壮性修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `appstartfasterlibrary` 中 2 个 P0 崩溃缺陷、2 个 P1 中危缺陷和 2 个 P2 代码质量问题，覆盖健壮性、架构合理性三个维度。

**Architecture:** 所有修改均在 `appstartfasterlibrary` 模块内，各 Task 相互独立，每完成一个 Task 即可单独验证。不改动 `app` 模块、`TaskInterface` 接口、日志工具类。不执行任何 git 命令，仅在本地修改文件。

**Tech Stack:** Java 17, Android Library, JUnit 4 (`testImplementation 'junit:junit:4.12'`), `returnDefaultValues = true`（单元测试中 Android API 返回默认值，`Looper.getMainLooper()` 和 `Looper.myLooper()` 均返回 null，使 `start()` 的主线程校验通过）

---

## 文件一览

| 文件 | 操作 | Task |
|------|------|------|
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java` | 修改 | Task 1 |
| `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java` | 修改 | Task 1 |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java` | 修改 | Task 2、Task 5 |
| `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcherTest.java` | 新建 | Task 2 |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/runnable/AppStartTaskRunnable.java` | 修改 | Task 3 |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java` | 修改 | Task 4 |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java` | 修改 | Task 6 |

---

## Task 1：SortUtil — P0 未注册依赖静默 NPE 修复

**问题：** 若调用方在 `getDependsTaskList()` 中声明了某个依赖任务，但忘记将该依赖任务通过 `addAppStartTask()` 加入 Dispatcher，`getSortResult()` 第二轮填充 `childMap` 时执行 `childMap.get(parent).add(...)` 会触发 NPE，且堆栈无任何有效信息。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java`
- Modify: `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java`

- [ ] **Step 1: 在 AppStartTaskSortUtilTest.java 中添加测试任务类和失败测试**

打开 `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java`。

在文件顶部 import 区添加（若尚未存在）：
```java
import static org.junit.Assert.fail;
```

在类内最后一个测试方法之后、类的结束括号 `}` 之前添加：

```java
// 依赖未注册任务的测试任务（依赖 TaskA，但 TaskA 不会被加入 startTaskList）
static class TaskOnlyB extends AppStartTask {
    @Override public void run() {}
    @Override public boolean isRunOnMainThread() { return false; }
    @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
        return Collections.singletonList(TaskA.class);
    }
}

@Test
public void getSortResult_unregisteredDependency_throwsWithUsefulMessage() {
    // TaskOnlyB 依赖 TaskA，但列表中只有 TaskOnlyB，TaskA 未注册
    List<AppStartTask> tasks = Collections.singletonList(new TaskOnlyB());
    try {
        AppStartTaskSortUtil.getSortResult(tasks);
        fail("Expected RuntimeException");
    } catch (RuntimeException e) {
        assertTrue("Error message should contain missing dependency class name",
                e.getMessage().contains("TaskA"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

```bash
cd /Users/mac/Desktop/MyProject/AppStartFaster
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest.getSortResult_unregisteredDependency_throwsWithUsefulMessage"
```

期望：`FAILED`（当前抛出 NullPointerException，getMessage() 为 null，`assertTrue` 失败）

- [ ] **Step 3: 修改 AppStartTaskSortUtil.java 第二轮循环，添加依赖注册校验**

打开 `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java`。

将第二轮 `for` 循环（`// 第二轮：填充 childMap` 注释开始的部分）整体替换为：

```java
// 第二轮：填充 childMap（每个父节点记录其子节点），同时校验依赖项已注册
for (AppStartTask task : startTaskList) {
    List<Class<? extends AppStartTask>> depends = task.getCachedDependsTaskList();
    if (depends != null) {
        for (Class<? extends AppStartTask> parent : depends) {
            if (!childMap.containsKey(parent)) {
                throw new RuntimeException(
                        "Dependency not registered: " + parent.getSimpleName()
                        + " (required by " + task.getClass().getSimpleName()
                        + "). Make sure to call addAppStartTask() for every dependency.");
            }
            childMap.get(parent).add(task.getClass());
        }
    }
}
```

- [ ] **Step 4: 运行全部 SortUtil 测试，确认全部通过**

```bash
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest"
```

期望：所有测试（含新增的）`PASSED`，BUILD SUCCESSFUL

---

## Task 2：Dispatcher — P0 `start()` 幂等保护 + `addTask()` 后置校验

**问题一：** `start()` 被重复调用时，`mSortMainThreadTaskList` / `mSortThreadPoolTaskList` 叠加追加，`mCountDownLatch` 被替换，已在 `await()` 阻塞的线程永远无法唤醒。

**问题二：** `addAppStartTask()` 在 `start()` 之后调用，新任务不进入已排序 DAG，但 `mNeedWaitCount` 被错误递增，导致 `await()` 永远阻塞。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`
- Create: `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcherTest.java`

- [ ] **Step 1: 新建 AppStartTaskDispatcherTest.java**

创建文件 `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcherTest.java`，内容如下：

```java
package com.aice.appstartfaster.dispatcher;

import com.aice.appstartfaster.task.AppStartTask;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.fail;

public class AppStartTaskDispatcherTest {

    // 无依赖的主线程任务：isRunOnMainThread()=true 使 dispatchAppStartTask() 在调用方线程同步执行
    // waitToNotify() 在 CountDownLatch(0) 时立即返回，不会阻塞
    static class SimpleMainTask extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return true; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() { return null; }
    }

    static class AnotherTask extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return true; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() { return null; }
    }

    @Test
    public void start_calledTwice_throwsRuntimeException() {
        AppStartTaskDispatcher dispatcher = AppStartTaskDispatcher.create()
                .addAppStartTask(new SimpleMainTask());
        dispatcher.start(); // 第一次：成功
        try {
            dispatcher.start(); // 第二次：应抛出异常
            fail("Expected RuntimeException on second start() call");
        } catch (RuntimeException e) {
            // pass
        }
    }

    @Test
    public void addAppStartTask_afterStart_throwsRuntimeException() {
        AppStartTaskDispatcher dispatcher = AppStartTaskDispatcher.create()
                .addAppStartTask(new SimpleMainTask());
        dispatcher.start();
        try {
            dispatcher.addAppStartTask(new AnotherTask()); // start() 之后添加：应抛出异常
            fail("Expected RuntimeException when addAppStartTask called after start()");
        } catch (RuntimeException e) {
            // pass
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

```bash
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.dispatcher.AppStartTaskDispatcherTest"
```

期望：两个测试均 `FAILED`（当前无任何保护，`fail()` 被执行）

- [ ] **Step 3: 修改 AppStartTaskDispatcher.java，添加 mStarted 标志位**

打开 `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`。

**3a.** 在字段声明区 `isShowLog` 字段之后添加一行：

```java
private boolean mStarted = false;
```

**3b.** 修改 `addAppStartTask()` 方法，在 null 校验之后、`mStartTaskList.add` 之前插入：

```java
if (mStarted) {
    throw new RuntimeException("addAppStartTask() must be called before start()");
}
```

完整方法变为：
```java
public AppStartTaskDispatcher addAppStartTask(AppStartTask appStartTask) {
    if (appStartTask == null) {
        throw new RuntimeException("addAppStartTask(): appStartTask must not be null");
    }
    if (mStarted) {
        throw new RuntimeException("addAppStartTask() must be called before start()");
    }
    mStartTaskList.add(appStartTask);
    if (ifNeedWait(appStartTask)) {
        mNeedWaitCount.getAndIncrement();
    }
    return this;
}
```

**3c.** 修改 `start()` 方法，在主线程校验（`Looper` 检查）之后立即添加：

```java
if (mStarted) {
    throw new RuntimeException("start() has already been called");
}
mStarted = true;
```

完整 `start()` 方法变为：
```java
public AppStartTaskDispatcher start() {
    if (Looper.getMainLooper() != Looper.myLooper()) {
        throw new RuntimeException("start() must be called on the main thread");
    }
    if (mStarted) {
        throw new RuntimeException("start() has already been called");
    }
    mStarted = true;
    mStartTime = System.currentTimeMillis();
    TaskSortResult result = AppStartTaskSortUtil.getSortResult(mStartTaskList);
    mSortTaskList = result.sortedList();
    mTaskHashMap = result.taskMap();
    mTaskChildHashMap = result.childMap();
    initRealSortTask();
    printSortTask();
    mCountDownLatch = new CountDownLatch(mNeedWaitCount.get());
    dispatchAppStartTask();
    return this;
}
```

- [ ] **Step 4: 运行全部测试，确认通过**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：所有测试 `PASSED`，BUILD SUCCESSFUL

---

## Task 3：AppStartTaskRunnable — P1 任务异常添加上下文日志

**问题：** 任务 `run()` 抛出异常时，线程池默认 uncaught handler 只打印裸异常，无法得知是哪个任务失败。主线程任务若 rethrow 会直接 crash Application，因此捕获后只记录日志，不重新抛出。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/runnable/AppStartTaskRunnable.java`

- [ ] **Step 1: 修改 AppStartTaskRunnable.java**

打开 `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/runnable/AppStartTaskRunnable.java`。

**1a.** 在文件顶部 import 区添加：
```java
import android.util.Log;
```

**1b.** 将两个字段声明加上 `final`：
```java
private final AppStartTask mAppStartTask;
private final AppStartTaskDispatcher mAppStartTaskDispatcher;
```

**1c.** 将 `run()` 方法中的 try-finally 块替换为 try-catch-finally：
```java
@Override
public void run() {
    mAppStartTask.waitToNotify();
    try {
        mAppStartTask.run();
    } catch (Throwable t) {
        Log.e("AppStartTask", "Task failed: " + mAppStartTask.getClass().getSimpleName(), t);
        // 不 rethrow：框架设计为单个任务失败不中断整体启动流程。
        // 主线程任务若 rethrow 会直接 crash Application；
        // finally 块已保证子任务通知链和 await() latch 正常递减。
    } finally {
        mAppStartTaskDispatcher.setNotifyChildren(mAppStartTask);
        mAppStartTaskDispatcher.markAppStartTaskFinish(mAppStartTask);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :appstartfasterlibrary:compileDebugSources
```

期望：`BUILD SUCCESSFUL`，无编译错误

---

## Task 4：TaskExecutorManager — P1 死代码清理

**问题：** CPU 线程池使用无界 `LinkedBlockingQueue`，队列永远不会满，`mHandler`（拒绝策略）和 `mFallbackExecutor`（额外 CachedThreadPool）永远不会被触发，是死代码但一直持有线程资源。IO 线程池保持无界不变（IO 任务大部分时间阻塞等待，线程不占 CPU，无界最大化并发）。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java`

- [ ] **Step 1: 将 TaskExecutorManager.java 替换为以下内容**

```java
package com.aice.appstartfaster.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TaskExecutorManager {
    private static volatile TaskExecutorManager sTaskExecutorManager;

    // CPU 核数
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // CPU 密集型线程池大小：固定线程数，避免抢占主线程时间片
    private static final int CPU_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 5));
    // 线程空闲回收时间
    private static final int KEEP_ALIVE_SECONDS = 5;

    // CPU 密集型任务线程池（固定大小，无界队列，无拒绝策略）
    private final ThreadPoolExecutor mCPUThreadPoolExecutor;
    // IO 密集型任务线程池（无界，最大化 IO 并发）
    private final ExecutorService mIOThreadPoolExecutor;

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

    private TaskExecutorManager() {
        mCPUThreadPoolExecutor = new ThreadPoolExecutor(
                CPU_POOL_SIZE, CPU_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory());
        mCPUThreadPoolExecutor.allowCoreThreadTimeOut(true);
        mIOThreadPoolExecutor = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
    }

    public ThreadPoolExecutor getCPUThreadPoolExecutor() {
        return mCPUThreadPoolExecutor;
    }

    public ExecutorService getIOThreadPoolExecutor() {
        return mIOThreadPoolExecutor;
    }
}
```

- [ ] **Step 2: 编译并运行全部测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：`BUILD SUCCESSFUL`，所有测试 `PASSED`

---

## Task 5：Dispatcher — P2 `mNeedWaitCount` 死代码删除

**问题：** `markAppStartTaskFinish()` 中调用 `mNeedWaitCount.getAndDecrement()`，但 `mNeedWaitCount` 在 `start()` 执行完毕后从未被读取，该递减无任何意义，且数值会变负造成误解。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`

- [ ] **Step 1: 修改 markAppStartTaskFinish() 方法，删除无效递减**

打开 `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`。

将 `markAppStartTaskFinish()` 方法替换为：

```java
public void markAppStartTaskFinish(AppStartTask appStartTask) {
    AppStartTaskLogUtil.showLog(isShowLog, "Task finished: " + appStartTask.getClass().getSimpleName());
    if (ifNeedWait(appStartTask)) {
        mCountDownLatch.countDown();
    }
}
```

（删除了原来的 `mNeedWaitCount.getAndDecrement();` 一行）

- [ ] **Step 2: 运行全部测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：所有测试 `PASSED`，BUILD SUCCESSFUL

---

## Task 6：AppStartTask — P2 `waitToNotify()` 删除无效中断标志恢复

**问题：** `waitToNotify()` 捕获 `InterruptedException` 后调用 `Thread.currentThread().interrupt()` 恢复中断标志，但框架在此之后不检查中断标志、没有提前退出路径，任务始终继续执行。副作用：① 任务 `run()` 内若有 `Thread.sleep()` 等可中断操作，会立即抛出 `InterruptedException`；② 后台线程执行完毕后带中断标志，线程池会替换 worker 线程，造成不必要的线程销毁重建。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java`

- [ ] **Step 1: 修改 waitToNotify() 方法，删除 Thread.currentThread().interrupt()**

打开 `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java`。

将 `waitToNotify()` 方法替换为：

```java
public void waitToNotify() {
    try {
        mDepends.await();
    } catch (InterruptedException e) {
        Log.w("AppStartTask", "waitToNotify interrupted: " + e.getMessage());
    }
}
```

（删除了 `Thread.currentThread().interrupt();` 一行）

- [ ] **Step 2: 编译验证**

```bash
./gradlew :appstartfasterlibrary:compileDebugSources
```

期望：`BUILD SUCCESSFUL`，无编译错误

- [ ] **Step 3: 运行全部测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：所有测试 `PASSED`，BUILD SUCCESSFUL

---

## Self-Review

**Spec coverage 检查：**

| Spec 要求 | 对应 Task |
|----------|---------|
| P0 SortUtil 未注册依赖 NPE | Task 1 ✅ |
| P0 start() 重复调用保护 | Task 2 ✅ |
| P0 addTask() after start() 保护 | Task 2 ✅ |
| P1 任务异常有日志、不 crash | Task 3 ✅ |
| P1 TaskExecutorManager 死代码清理 | Task 4 ✅ |
| P2 mNeedWaitCount 无效递减 | Task 5 ✅ |
| P2 waitToNotify() 无效中断恢复 | Task 6 ✅ |

**Placeholder scan:** 无 TBD/TODO，每步均含完整代码或精确命令。

**Type consistency:** `AppStartTaskDispatcher.markAppStartTaskFinish()` 在 Task 5 中修改，签名不变，Task 2/3 对其调用不受影响。`mStarted` 字段在 Task 2 引入并在同一 Task 的两处使用，一致。
