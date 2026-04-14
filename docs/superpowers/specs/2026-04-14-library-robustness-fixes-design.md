# AppStartFaster Library 健壮性修复设计文档

**日期：** 2026-04-14  
**范围：** `appstartfasterlibrary` 模块  
**目标：** 修复 9 个已识别问题，覆盖健壮性、算法效率、代码质量三个维度  

---

## 背景

对 `appstartfasterlibrary` 进行全面 review 后，发现 2 个 P0 高危缺陷、2 个 P1 中危缺陷和 5 个 P2 代码质量问题。`priority()` 方法已从代码库中删除，不纳入本次修复范围。

**API 兼容性：** 不作限制，以代码质量为优先。

---

## 涉及文件

| 文件 | 改动类型 |
|------|---------|
| `AppStartTaskSortUtil.java` | P0 依赖校验 |
| `AppStartTaskDispatcher.java` | P0 幂等保护 + P2 死代码清理 |
| `AppStartTaskRunnable.java` | P1 异常日志 + 字段 final |
| `AppStartTask.java` | P2 `waitToNotify()` 中断处理清理 |
| `TaskExecutorManager.java` | P1/P2 死代码清理（IO 池保持无界） |

**不在范围内：** `app` 模块、`TaskInterface`、日志工具类、`priority()` 任何相关内容。

---

## P0 修复

### P0-1：SortUtil — 未注册依赖静默 NPE

**问题：** 调用方声明了依赖但未将依赖任务加入 Dispatcher 时，`AppStartTaskSortUtil.getSortResult()` 第二轮填充 `childMap` 时调用 `childMap.get(parent).add(...)` 触发 NPE，堆栈无有效信息。

**根因：** 第二轮循环未校验 `parent` 是否已在 `taskMap`（即已通过 `addAppStartTask()` 注册）中存在。

**修复方案：** 在第二轮循环遍历每个依赖项时，先检查 `childMap.containsKey(parent)`，若不存在则抛出含任务名的 `RuntimeException`：

```
"Dependency not registered: <ParentSimpleName> (required by <ChildSimpleName>).
 Make sure to call addAppStartTask() for every dependency."
```

**测试：** 新增 `getSortResult_unregisteredDependency_throwsRuntimeException()`，构造一个任务依赖未注册任务的场景，断言抛出 `RuntimeException` 且错误信息包含缺失任务的类名。

---

### P0-2：Dispatcher — `start()` 幂等保护 + `addTask()` 后置校验

**问题一：** `start()` 被重复调用时，`mSortMainThreadTaskList` 和 `mSortThreadPoolTaskList` 会叠加追加任务，`mCountDownLatch` 被替换为新实例，导致已在 `await()` 阻塞的调用方永远无法被唤醒。

**问题二：** `addAppStartTask()` 在 `start()` 之后调用，新任务会被加入 `mStartTaskList` 但不进入已排序的 DAG，且 `mNeedWaitCount` 被错误递增。

**修复方案：** 添加 `private boolean mStarted = false` 标志位：

- `start()` 头部（主线程校验之后）：若 `mStarted == true` 抛 `"start() has already been called"`，否则置 `mStarted = true`
- `addAppStartTask()` 头部（null 校验之后）：若 `mStarted == true` 抛 `"addAppStartTask() must be called before start()"`

**测试：** 新建 `AppStartTaskDispatcherTest`，覆盖两个场景：重复调用 `start()`、`start()` 后调用 `addAppStartTask()`，均断言抛出 `RuntimeException`。

---

## P1 修复

### P1-1：AppStartTaskRunnable — 任务异常添加上下文日志

**问题：** 任务 `run()` 抛出异常时，线程池默认的 uncaught handler 只打印裸异常，无法得知是哪个任务失败。`finally` 块仍会执行，通知链不断，但问题不可追踪。

**修复方案：** 在 `try-finally` 中插入 `catch (Throwable t)`，记录含任务类名的 Error 级日志后**吞掉异常**（不 rethrow），`finally` 块结构不变：

```java
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
```

同步将两个字段声明加 `final`（只在构造器赋值，语义更明确）：

```java
private final AppStartTask mAppStartTask;
private final AppStartTaskDispatcher mAppStartTaskDispatcher;
```

**验证：** 编译通过，逻辑由现有集成行为保证。

---

### P1-2：TaskExecutorManager — 死代码清理

**设计原则：**
- CPU 密集型线程池：固定大小，控制并发，防止抢占主线程时间片
- IO 密集型线程池：无界，不控制并发，IO 任务大部分时间阻塞等待，线程不占 CPU，无界可最大化并发

**问题（死代码）：** CPU 线程池使用无界 `LinkedBlockingQueue`，队列永远不会满，`RejectedExecutionHandler mHandler` 和 `mFallbackExecutor`（一个额外的 `CachedThreadPool`）永远不会被触发，但一直持有线程资源。

**修复方案：**

- 删除实例字段 `mFallbackExecutor`、`mHandler`、`mPoolWorkQueue`（移入构造器局部变量）
- 合并 `CORE_POOL_SIZE` / `MAXIMUM_POOL_SIZE` 为单一常量 `CPU_POOL_SIZE`（两者原本相等，分开定义造成误解）
- CPU 池：构造器内局部传入 `new LinkedBlockingQueue<>()`，不设拒绝策略（队列无界，不可达）
- IO 池：保持 `Executors.newCachedThreadPool()`，不作任何限制

**验证：** 编译通过 + 全部现有测试通过。

---

## P2 代码质量

### P2-1：Dispatcher — 死代码 `mNeedWaitCount` 递减

**问题：** `markAppStartTaskFinish()` 中调用 `mNeedWaitCount.getAndDecrement()`，但 `mNeedWaitCount` 在 `start()` 执行完毕后从未被读取，递减无意义，且数值会变负让人误解状态。

**修复：** 删除 `markAppStartTaskFinish()` 中的 `mNeedWaitCount.getAndDecrement()` 一行。`mNeedWaitCount` 保留，仍用于 `addAppStartTask()` 统计 `needWait` 任务数量、初始化 `mCountDownLatch`。

---

### ~~P2-2~~（已移出范围）

`isShowLog` 是 boolean 字段，`isXxx` 命名符合规范，不需要 `m` 前缀，保持不变。

---

### P2-3：AppStartTask — `waitToNotify()` 删除无效中断标志恢复

**问题：** `waitToNotify()` 捕获 `InterruptedException` 后调用 `Thread.currentThread().interrupt()` 恢复中断标志，但框架在此之后不检查中断标志、也没有提前退出路径，任务始终继续执行。这导致两个副作用：
1. 若任务 `run()` 内部有 `Thread.sleep()` 等可中断操作，中断标志已设置会令其立即抛出 `InterruptedException`，造成任务内部莫名失败
2. 后台线程池线程执行完毕后带有中断标志，`ThreadPoolExecutor` 会检测并替换该 worker 线程，造成不必要的线程销毁重建

**修复：** 删除 `Thread.currentThread().interrupt()` 一行，保留 log 警告：

```java
public void waitToNotify() {
    try {
        mDepends.await();
    } catch (InterruptedException e) {
        Log.w("AppStartTask", "waitToNotify interrupted: " + e.getMessage());
    }
}
```

**验证：** 编译通过。

---

## 测试策略

| 改动 | 测试方式 |
|------|---------|
| P0-1 SortUtil NPE | 新增单元测试，断言异常信息含缺失类名 |
| P0-2 Dispatcher 幂等 | 新建 `AppStartTaskDispatcherTest`，覆盖两个异常场景 |
| P1-1 Runnable 日志 | 编译验证 |
| P1-2 Executor 死代码清理 | 编译验证 + 全量测试通过 |
| P2-1 Dispatcher 死代码清理 | 全量测试通过 |
| P2-3 AppStartTask 中断清理 | 编译验证 |

---

## 实现顺序

按优先级从高到低，每个 Task 独立验证后再进行下一个：

1. P0-1：SortUtil 依赖校验
2. P0-2：Dispatcher 幂等保护
3. P1-1：Runnable 异常日志
4. P1-2：Executor 死代码 + IO 池有界
5. P2-1：Dispatcher 死代码清理
6. P2-3：AppStartTask 删除无效中断标志恢复
