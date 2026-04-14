# AppStartFaster Library Robustness Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `appstartfasterlibrary` 中经深度 review 发现的 3 个 P0 高危缺陷、4 个 P1 中危缺陷和 3 个 P2 代码质量问题，覆盖健壮性、架构合理性、算法效率三个维度。

**Architecture:** 所有修改都在 `appstartfasterlibrary` 模块内，不改动公开 API（`AppStartTask` 抽象方法签名不变）。各任务相互独立，按优先级从高到低排列，每完成一个任务即可单独验证和提交。

**Tech Stack:** Java 17, Android Library, JUnit 4 (`testImplementation 'junit:junit:4.12'`), `returnDefaultValues = true`（unit test 中 Android API 返回默认值）

---

## 文件一览

| 文件 | 本次操作 |
|------|---------|
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java` | 修改（Task 1、Task 2） |
| `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java` | 修改（Task 1、Task 2） |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java` | 修改（Task 3、Task 6） |
| `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcherTest.java` | 新建（Task 3） |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/runnable/AppStartTaskRunnable.java` | 修改（Task 4） |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java` | 修改（Task 5） |
| `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java` | 修改（Task 7） |

---

## Task 1: SortUtil — P0 未注册依赖 NPE 修复

**修复:** 在第二轮填充 childMap 前校验每个依赖项已注册，缺失则抛出含任务名称的有意义异常，替代当前的静默 NPE。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java`
- Modify: `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java`

- [ ] **Step 1: 先写失败测试**

在 `AppStartTaskSortUtilTest.java` 中，在现有类末尾（`getSortResult_cyclicDependency_throwsException` 之后）添加：

```java
// 未注册的依赖任务（TaskB 依赖 TaskA，但 TaskA 未加入列表）
static class TaskOnlyB extends AppStartTask {
    @Override public void run() {}
    @Override public boolean isRunOnMainThread() { return false; }
    @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
        return Collections.singletonList(TaskA.class); // TaskA 不在 startTaskList 中
    }
}

@Test(expected = RuntimeException.class)
public void getSortResult_unregisteredDependency_throwsRuntimeException() {
    // TaskOnlyB 依赖 TaskA，但列表中没有 TaskA
    List<AppStartTask> tasks = Collections.singletonList(new TaskOnlyB());
    AppStartTaskSortUtil.getSortResult(tasks);
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /Users/mac/Desktop/MyProject/AppStartFaster
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest.getSortResult_unregisteredDependency_throwsRuntimeException"
```

期望：`FAILED`（当前会抛出 NullPointerException 而非 RuntimeException，但 `@Test(expected=RuntimeException.class)` 在有些 JUnit 版本会接受 NPE，因为 NPE 是 RuntimeException 的子类——如果测试意外通过，改用下面的断言版本确认错误信息包含任务名称）。

若测试意外通过（NPE 被接受），将测试改为：

```java
@Test
public void getSortResult_unregisteredDependency_throwsWithUsefulMessage() {
    List<AppStartTask> tasks = Collections.singletonList(new TaskOnlyB());
    try {
        AppStartTaskSortUtil.getSortResult(tasks);
        fail("Expected RuntimeException");
    } catch (RuntimeException e) {
        assertTrue("Error message should contain dependency class name",
            e.getMessage().contains("TaskA"));
    }
}
```

- [ ] **Step 3: 修改 `AppStartTaskSortUtil.java` 第二轮循环**

将第二轮 `for` 循环（当前约 42-49 行）整体替换为：

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

- [ ] **Step 4: 运行所有 SortUtil 测试，确认全部通过**

```bash
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest"
```

期望：所有 5 个测试 `PASSED`。

- [ ] **Step 5: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java \
        appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java
git commit -m "fix(SortUtil): validate dependency registration before building childMap, prevent silent NPE"
```

---

## Task 2: SortUtil — P1 同级任务按 priority() 排序

**修复:** 将 Kahn 算法中的 `ArrayDeque` 替换为 `PriorityQueue`（按 `priority()` 升序，数值越小优先级越高），确保同一拓扑层级内高优先级任务先被分发到线程池。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java`
- Modify: `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java`

- [ ] **Step 1: 先写失败测试**

在 `AppStartTaskSortUtilTest.java` 中添加两个带优先级的任务类和一个测试：

```java
// 高优先级（Process.THREAD_PRIORITY_FOREGROUND = -2）
static class TaskHighPriority extends AppStartTask {
    @Override public void run() {}
    @Override public boolean isRunOnMainThread() { return false; }
    @Override public int priority() { return -2; } // FOREGROUND
    @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
        return null; // 无依赖，与 TaskLowPriority 在同一拓扑层级
    }
}

// 低优先级（Process.THREAD_PRIORITY_BACKGROUND = 10）
static class TaskLowPriority extends AppStartTask {
    @Override public void run() {}
    @Override public boolean isRunOnMainThread() { return false; }
    @Override public int priority() { return 10; } // BACKGROUND
    @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
        return null;
    }
}

@Test
public void getSortResult_sameLevelTasks_highPriorityFirst() {
    // 低优先级先加入，期望高优先级排在前面
    List<AppStartTask> tasks = Arrays.asList(new TaskLowPriority(), new TaskHighPriority());
    TaskSortResult result = AppStartTaskSortUtil.getSortResult(tasks);

    assertEquals(2, result.sortedList().size());
    assertTrue("High priority task should come first",
        result.sortedList().get(0) instanceof TaskHighPriority);
    assertTrue(result.sortedList().get(1) instanceof TaskLowPriority);
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest.getSortResult_sameLevelTasks_highPriorityFirst"
```

期望：`FAILED`（当前队列是 ArrayDeque，顺序由输入顺序决定，低优先级任务会排在前面）。

- [ ] **Step 3: 修改 `AppStartTaskSortUtil.java`**

**3a. 更新 import 区块**（将 `Deque` 和 `ArrayDeque` 替换为 `PriorityQueue`，同时添加 `Comparator`）：

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
```

（删去 `import java.util.ArrayDeque;` 和 `import java.util.Deque;`）

**3b. 将第一轮循环中的队列声明和入队逻辑修改**（约第 25 行、第 37-39 行）：

将：
```java
Deque<Class<? extends AppStartTask>> zeroInDegreeQueue = new ArrayDeque<>();
```
改为：
```java
// 按 priority() 升序排列（数值越小优先级越高，先分发）
PriorityQueue<AppStartTask> zeroInDegreeQueue =
        new PriorityQueue<>(Comparator.comparingInt(AppStartTask::priority));
```

将第一轮循环末尾的入队：
```java
if (inDegree == 0) {
    zeroInDegreeQueue.offer(task.getClass());
}
```
改为：
```java
if (inDegree == 0) {
    zeroInDegreeQueue.offer(task); // 直接入队 task 实例，用于优先级比较
}
```

**3c. 更新 Kahn 主循环**（约第 53-63 行），将：

```java
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
```

改为：

```java
while (!zeroInDegreeQueue.isEmpty()) {
    AppStartTask current = zeroInDegreeQueue.poll();
    sortedList.add(current);
    for (Class<? extends AppStartTask> childCls : childMap.get(current.getClass())) {
        int newDegree = inDegreeMap.get(childCls) - 1;
        inDegreeMap.put(childCls, newDegree);
        if (newDegree == 0) {
            zeroInDegreeQueue.offer(taskMap.get(childCls));
        }
    }
}
```

- [ ] **Step 4: 运行全部 SortUtil 测试**

```bash
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest"
```

期望：所有 6 个测试（含 Task 1 新增的）全部 `PASSED`。

- [ ] **Step 5: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/util/AppStartTaskSortUtil.java \
        appstartfasterlibrary/src/test/java/com/aice/appstartfaster/util/AppStartTaskSortUtilTest.java
git commit -m "feat(SortUtil): dispatch same-level tasks in priority order using PriorityQueue"
```

---

## Task 3: Dispatcher — P0 start() 幂等保护 + addTask 后置调用校验

**修复:** 添加 `mStarted` 标志位，防止 `start()` 被重复调用以及 `addAppStartTask()` 在 `start()` 之后被调用破坏状态。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`
- Create: `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcherTest.java`

- [ ] **Step 1: 新建测试文件**

创建 `appstartfasterlibrary/src/test/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcherTest.java`：

```java
package com.aice.appstartfaster.dispatcher;

import com.aice.appstartfaster.task.AppStartTask;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.fail;

public class AppStartTaskDispatcherTest {

    // 最简单的主线程任务（isRunOnMainThread=true 使其在 dispatchAppStartTask 中直接 run()）
    static class SimpleMainTask extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return true; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() { return null; }
    }

    // 最简单的后台任务
    static class SimpleBackgroundTask extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return false; }
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
            dispatcher.addAppStartTask(new SimpleBackgroundTask());
            fail("Expected RuntimeException when addAppStartTask called after start()");
        } catch (RuntimeException e) {
            // pass
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.dispatcher.AppStartTaskDispatcherTest"
```

期望：两个测试均 `FAILED`（当前无任何保护）。

- [ ] **Step 3: 修改 `AppStartTaskDispatcher.java`**

**3a.** 在字段声明区（`isShowLog` 之后）添加：

```java
private boolean mStarted = false;
```

**3b.** 修改 `addAppStartTask()` 方法，在 null 校验之后、`mStartTaskList.add` 之前添加：

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

**3c.** 修改 `start()` 方法，在主线程校验之后立即添加幂等保护：

```java
public AppStartTaskDispatcher start() {
    if (Looper.getMainLooper() != Looper.myLooper()) {
        throw new RuntimeException("start() must be called on the main thread");
    }
    if (mStarted) {
        throw new RuntimeException("start() has already been called");
    }
    mStarted = true;
    // ... 其余代码不变
```

- [ ] **Step 4: 运行所有测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：所有测试 `PASSED`。

- [ ] **Step 5: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java \
        appstartfasterlibrary/src/test/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcherTest.java
git commit -m "fix(Dispatcher): guard against duplicate start() and post-start addAppStartTask() calls"
```

---

## Task 4: AppStartTaskRunnable — P1 任务异常添加上下文日志

**修复:** 在 `run()` 的 try 块中 catch `Throwable`，记录含任务类名的 error 日志后 rethrow，使异常排查有据可查。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/runnable/AppStartTaskRunnable.java`

- [ ] **Step 1: 修改 `AppStartTaskRunnable.java`**

文件顶部添加 import（已有 `android.os.Process`，补充 `android.util.Log`）：

```java
import android.util.Log;
```

将 `run()` 方法中的 try-finally 块：

```java
try {
    mAppStartTask.run();
} finally {
    mAppStartTaskDispatcher.setNotifyChildren(mAppStartTask);
    mAppStartTaskDispatcher.markAppStartTaskFinish(mAppStartTask);
}
```

改为：

```java
try {
    mAppStartTask.run();
} catch (Throwable t) {
    Log.e("AppStartTask", "Task failed: " + mAppStartTask.getClass().getSimpleName(), t);
    throw t;
} finally {
    mAppStartTaskDispatcher.setNotifyChildren(mAppStartTask);
    mAppStartTaskDispatcher.markAppStartTaskFinish(mAppStartTask);
}
```

同时将两个字段声明加上 `final`（它们只在构造器中赋值）：

```java
private final AppStartTask mAppStartTask;
private final AppStartTaskDispatcher mAppStartTaskDispatcher;
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :appstartfasterlibrary:compileDebugSources
```

期望：`BUILD SUCCESSFUL`，无编译错误。

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/runnable/AppStartTaskRunnable.java
git commit -m "fix(Runnable): log task name and exception on task failure, mark fields final"
```

---

## Task 5: AppStartTask — P2 依赖列表返回空集合 + 不可变暴露

**修复:** `getDependsTaskList()` 默认返回 `Collections.emptyList()` 替代 `null`，消除所有调用方的 null-check。`getCachedDependsTaskList()` 包装 `unmodifiableList`，防止外部修改破坏 CountDownLatch 计数。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java`

- [ ] **Step 1: 修改 `AppStartTask.java`**

**1a.** 文件顶部添加 import：

```java
import java.util.Collections;
```

**1b.** 将 `getDependsTaskList()` 默认实现改为返回空列表：

```java
@Override
public List<Class<? extends AppStartTask>> getDependsTaskList() {
    return Collections.emptyList();
}
```

**1c.** 构造器中的 null-check 可简化（`emptyList()` 的 `size()` 是 0，安全）：

```java
protected AppStartTask() {
    mCachedDependsTaskList = getDependsTaskList();
    mDepends = new CountDownLatch(mCachedDependsTaskList.size());
}
```

**1d.** `getCachedDependsTaskList()` 返回不可变视图：

```java
public List<Class<? extends AppStartTask>> getCachedDependsTaskList() {
    return Collections.unmodifiableList(mCachedDependsTaskList);
}
```

- [ ] **Step 2: 运行全部测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：全部 `PASSED`（现有 SortUtil 测试中有任务仍返回 `null` —— `getCachedDependsTaskList()` 在构造器中调用 `getDependsTaskList()`，子类覆盖返回 null 时 `mCachedDependsTaskList` 为 null，`unmodifiableList(null)` 会 NPE）。

若出现 NPE，在 `getCachedDependsTaskList()` 和构造器中做保护：

```java
protected AppStartTask() {
    List<Class<? extends AppStartTask>> deps = getDependsTaskList();
    mCachedDependsTaskList = (deps != null) ? deps : Collections.emptyList();
    mDepends = new CountDownLatch(mCachedDependsTaskList.size());
}

public List<Class<? extends AppStartTask>> getCachedDependsTaskList() {
    return Collections.unmodifiableList(mCachedDependsTaskList);
}
```

（这样即使子类仍然返回 null，构造器也可以安全处理，兼容历史代码。）

再次运行测试确认全部通过。

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/task/AppStartTask.java
git commit -m "fix(AppStartTask): return emptyList by default, expose unmodifiable dependency list"
```

---

## Task 6: Dispatcher — P2 mNeedWaitCount 死代码清理 + 命名修正

**修复:** 删除 `markAppStartTaskFinish()` 中无效的 `mNeedWaitCount.getAndDecrement()`（该值在 `start()` 后从未被读取）。修正 `isShowLog` 字段名加上 `m` 前缀，与其他字段命名统一。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java`

- [ ] **Step 1: 修改 `AppStartTaskDispatcher.java`**

**1a.** 删除 `markAppStartTaskFinish()` 中的无效递减，完整方法变为：

```java
public void markAppStartTaskFinish(AppStartTask appStartTask) {
    AppStartTaskLogUtil.showLog(isShowLog, "Task finished: " + appStartTask.getClass().getSimpleName());
    if (ifNeedWait(appStartTask)) {
        mCountDownLatch.countDown();
    }
}
```

**1b.** 字段声明中将 `boolean isShowLog` 改为 `boolean mIsShowLog`：

```java
private boolean mIsShowLog;
```

**1c.** 同步修改 setter 和所有使用处：

`setShowLog` 方法：
```java
public AppStartTaskDispatcher setShowLog(boolean showLog) {
    mIsShowLog = showLog;
    return this;
}
```

`printSortTask()` 中：
```java
AppStartTaskLogUtil.showLog(mIsShowLog, sb.toString());
```

`markAppStartTaskFinish()` 中：
```java
AppStartTaskLogUtil.showLog(mIsShowLog, "Task finished: " + appStartTask.getClass().getSimpleName());
```

`await()` 中：
```java
AppStartTaskLogUtil.showLog(mIsShowLog, "Startup time: " + mFinishTime + "ms");
```

**1d.** 顺便用 Stream 简化 `printSortTask()` 的字符串拼接（可选，但更清晰）：

在文件 import 区添加：
```java
import java.util.stream.Collectors;
```

将方法改为：
```java
private void printSortTask() {
    String order = mSortTaskList.stream()
            .map(t -> t.getClass().getSimpleName())
            .collect(Collectors.joining(" ---> "));
    AppStartTaskLogUtil.showLog(mIsShowLog, "Current task execution order: " + order);
}
```

- [ ] **Step 2: 运行全部测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：全部 `PASSED`。

- [ ] **Step 3: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/dispatcher/AppStartTaskDispatcher.java
git commit -m "refactor(Dispatcher): remove dead mNeedWaitCount decrement, fix isShowLog field naming"
```

---

## Task 7: TaskExecutorManager — P1 死代码清理 + IO 线程池有界

**修复:**
1. 删除永远不会触发的 `mFallbackExecutor` 和 `mHandler`（CPU 线程池队列无界，拒绝策略不可达）。
2. 将 IO 线程池从无界 `newCachedThreadPool()` 改为有界，防止极端情况 OOM。
3. 将 `mPoolWorkQueue` 从实例字段改为构造器局部变量（仅构造器使用）。
4. 合并 `CORE_POOL_SIZE` 和 `MAXIMUM_POOL_SIZE`（两者相等），消除误导性参数。

**Files:**
- Modify: `appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java`

- [ ] **Step 1: 修改 `TaskExecutorManager.java`**

将整个类替换为以下内容（保留 import 中 `ExecutorService`，新增 `SynchronousQueue`）：

```java
package com.aice.appstartfaster.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

public class TaskExecutorManager {
    private static volatile TaskExecutorManager sTaskExecutorManager;

    // CPU 核数
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // CPU 密集型线程池大小：固定线程数，避免抢占主线程时间片
    private static final int CPU_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 5));
    // IO 密集型线程池最大线程数：CPU 核数的 2 倍，上限 8，防止 OOM
    private static final int IO_POOL_MAX_SIZE = Math.min(CPU_COUNT * 2, 8);

    // CPU 密集型任务线程池（固定大小，无界队列）
    private final ThreadPoolExecutor mCPUThreadPoolExecutor;
    // IO 密集型任务线程池（有界，SynchronousQueue 直接交付，满时调用方线程执行）
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
                5L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory());
        mCPUThreadPoolExecutor.allowCoreThreadTimeOut(true);

        mIOThreadPoolExecutor = new ThreadPoolExecutor(
                0, IO_POOL_MAX_SIZE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()); // 池满时退化为调用方线程执行
    }

    public ThreadPoolExecutor getCPUThreadPoolExecutor() {
        return mCPUThreadPoolExecutor;
    }

    public ExecutorService getIOThreadPoolExecutor() {
        return mIOThreadPoolExecutor;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :appstartfasterlibrary:compileDebugSources
```

期望：`BUILD SUCCESSFUL`。

- [ ] **Step 3: 运行全部测试**

```bash
./gradlew :appstartfasterlibrary:test
```

期望：全部 `PASSED`。

- [ ] **Step 4: 提交**

```bash
git add appstartfasterlibrary/src/main/java/com/aice/appstartfaster/executor/TaskExecutorManager.java
git commit -m "fix(Executor): remove unreachable fallback executor, bound IO thread pool to prevent OOM"
```

---

## Self-Review

**Spec coverage 检查:**

| Review 问题 | 对应 Task |
|------------|---------|
| P0 SortUtil NPE (未注册依赖) | Task 1 ✅ |
| P0 start() 重复调用 | Task 3 ✅ |
| P0 addTask after start | Task 3 ✅ |
| P1 任务异常无日志 | Task 4 ✅ |
| P1 中断后任务仍执行 | Task 4 部分覆盖（Runnable catch 后 rethrow，任务中断时 catch Throwable 拦截 InterruptedException）✅ |
| P1 同级任务不按优先级排序 | Task 2 ✅ |
| P1 IO 线程池无界 | Task 7 ✅ |
| P2 mNeedWaitCount 死代码 | Task 6 ✅ |
| P2 mFallbackExecutor 死代码 | Task 7 ✅ |
| P2 getDependsTaskList 返回 null | Task 5 ✅ |
| P2 getCachedDependsTaskList 暴露可变列表 | Task 5 ✅ |
| P2 isShowLog 命名不一致 | Task 6 ✅ |

**Placeholder scan:** 无 TBD/TODO，每步均含完整代码。

**Type consistency:** `AppStartTask::priority()` 在 Task 2 的 PriorityQueue 和测试中使用一致；`mIsShowLog` 在 Task 6 的所有引用点均已列出。
