# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建命令

```bash
# 构建整个项目
./gradlew build

# 构建并将库 AAR 发布到本地 /repos 目录
./gradlew :appstartfasterlibrary:publishMavenPublicationToMavenRepository

# 构建 Demo App 的 Debug APK
./gradlew :app:assembleDebug

# 运行所有单元测试
./gradlew test

# 运行单个测试类
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest"

# 运行单个测试方法
./gradlew :appstartfasterlibrary:test --tests "com.aice.appstartfaster.util.AppStartTaskSortUtilTest.getSortResult_linearDependency_correctOrder"

# 运行 Instrumented 测试（需要连接设备或启动模拟器）
./gradlew connectedAndroidTest
```

## 项目结构

两个模块：

- **`appstartfasterlibrary`** — 对外发布的 Android 库（AAR），通过 JitPack（`com.github.aiceking:AppStartFaster`）分发，同时也会发布到项目根目录的本地 Maven 仓库 `repos/`。当前版本由 `appstartfasterlibrary/build.gradle` 中的 `project.version` 控制（当前为 `2.5.0`）。
- **`app`** — 演示应用，使用 5 个测试任务验证库的功能。

`app/build.gradle` 中注释掉的 `implementation 'com.ice.cloud:appstartfaster:...'` 用于切换到本地 `repos/` 发布版，当前使用的是 `project(':appstartfasterlibrary')` 直接依赖。

SDK 要求：minSdk=24，targetSdk=36，compileSdk=36，Java 17。

## 架构

库实现了一个**基于 DAG（有向无环图）的启动任务分发器**。所有任务构成一张有向无环图，通过拓扑排序确定执行顺序，任务在依赖关系约束下并行分发到线程池执行。

### `appstartfasterlibrary` 核心类

| 类 | 职责 |
|---|---|
| `TaskInterface` | 接口，定义三个可重写方法：`runOnExecutor()`、`getDependsTaskList()`、`needWait()`。 |
| `AppStartTask` | 所有启动任务的抽象基类，实现 `TaskInterface`。子类**必须**实现 `run()`、`isRunOnMainThread()`，可选重写 `getDependsTaskList()`、`needWait()`、`runOnExecutor()`。构造函数中调用 `getDependsTaskList()` 并缓存结果（`getCachedDependsTaskList()`），同时初始化与依赖数量对应的 `CountDownLatch`。 |
| `AppStartTaskDispatcher` | 入口类。调用 `create()` 链式添加 `addAppStartTask()`，再调用 `start()`（**必须在主线程调用**）；如需等待后台任务完成再继续，调用 `await()` 阻塞主线程。`setShowLog(true)` 输出执行顺序和耗时日志；`setAllTaskWaitTimeOut(ms)` 设置 `await()` 最长阻塞时间（默认 10000ms）。 |
| `AppStartTaskSortUtil` | 使用 Kahn 算法进行拓扑排序。纯函数，结果封装为 `TaskSortResult`（Java 17 record）返回。遇到重复任务或环时抛出 `RuntimeException`。 |
| `TaskExecutorManager` | 单例。提供两个线程池：固定大小的 CPU 线程池（`max(2, min(CPU核数-1, 5))`）用于 CPU 密集型任务；无界缓存线程池用于 IO 密集型任务（默认）。CPU 线程池拒绝任务时回落至单例 `mFallbackExecutor`（`CachedThreadPool`）。 |
| `AppStartTaskRunnable` | 任务执行包装器：调用 `waitToNotify()` 等待父任务完成，执行任务，在 `finally` 块中通知子任务和分发器（保证任务异常时也能通知）。 |

### 执行流程

1. `AppStartTaskDispatcher.start()` 对所有任务进行拓扑排序，将结果分为主线程任务列表和子线程任务列表。
2. 子线程任务先分发（提交到各自的线程池），主线程任务后执行——避免主线程任务阻塞子线程任务的启动。
3. 每个 `AppStartTaskRunnable` 调用 `waitToNotify()`，在其 `CountDownLatch` 归零（即所有父任务调用 `notifyDependencyFinished()`）之前保持阻塞。
4. 任务完成后，`setNotifyChildren()` 将所有子任务的 latch 减一；若该任务 `needWait=true`，`markAppStartTaskFinish()` 同时将 `await()` 的 latch 减一。

### 线程池选择

通过 Systrace 确认任务的 cpuTime 占比：cpuTime 高的任务使用 `TaskExecutorManager.getInstance().getCPUThreadPoolExecutor()`（固定大小，防止抢占主线程时间片）；IO 等待为主的任务使用 `getIOThreadPoolExecutor()`（默认）。

### Demo 任务依赖图

```
One (主线程) ──→ Two ──→ Three ──→ Four
                  └──────────→ Four
          └────────────→ Three
                              └──→ Five
                  └────────────→ Five
```

即：Two 依赖 One；Three 依赖 One、Two；Four 依赖 Two、Three；Five 依赖 Two、Three。

### 添加新任务

在调用方代码中继承 `AppStartTask`。传入 `AppStartTaskDispatcher` 的 `addAppStartTask()` 顺序不影响正确性，执行顺序由 `getDependsTaskList()` 声明的图关系决定。

### 编码规范
Claude Code 修改任何代码都不要执行git命令，仅在本地做修改即可