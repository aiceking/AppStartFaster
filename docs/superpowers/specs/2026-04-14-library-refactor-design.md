# AppStartFaster 库深度重构设计文档

**日期**：2026-04-14  
**范围**：`appstartfasterlibrary` 模块  
**兼容性**：不要求向后兼容，可自由修改公开 API

---

## 一、背景与目标

当前库存在三类问题：职责边界模糊（副作用式输出参数）、健壮性缺陷（NPE 风险、中断标志丢失）、代码规范问题（命名违规、日志级别误用、无意义包装类）。本次重构全面解决上述问题，不新增对外 API，不改变库使用者的调用方式。

---

## 二、架构变化总览

| 组件 | 重构前 | 重构后 |
|---|---|---|
| `AppStartTaskSortUtil` | 排序 + 以副作用填充外部 HashMap | 纯函数：排序并返回 `TaskSortResult` |
| `TaskSortResult` | 不存在 | 新增：持有排好序的列表与两个 HashMap |
| `TaskSortModel` | 包裹单个 `int` 的包装类 | 删除，改用 `HashMap<Class, Integer>` |
| `AppStartTaskDispatcher` | 持有两个 HashMap 并由外部填充 | 从 `TaskSortResult` 赋值，职责自洽 |
| `AppStartTask` | `getDependsTaskList()` 调用两次；`Notify()` 命名违规 | 缓存依赖列表；方法改名 |
| `AppStartTaskLogUtil` | 全用 `Log.e()`；有无用 import | 改用 `Log.i()`；清除 import |
| `TaskExecutorManager` | 拒绝策略每次 new 新线程池 | 复用单例 fallback 线程池 |

**变更文件**：6 个现有文件 + 新增 1 个类（`TaskSortResult`）+ 删除 1 个类（`TaskSortModel`）

---

## 三、详细设计

### 3.1 新增 `TaskSortResult`

路径：`util/model/TaskSortResult.java`

```java
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

包级私有构造函数，外部不可直接实例化，只能由 `AppStartTaskSortUtil` 创建。

### 3.2 `AppStartTaskSortUtil` 重构

- 签名改为 `public static TaskSortResult getSortResult(List<AppStartTask> startTaskList)`
- 移除两个输出参数 `taskHashMap`、`taskChildHashMap`
- 内部用 `HashMap<Class<? extends AppStartTask>, Integer>` 替代 `TaskSortModel`，直接记录入度
- 遍历两次 `startTaskList` 的逻辑保持不变（规模小，无优化必要）
- 检测到重复任务或有环时继续抛出 `RuntimeException`

`AppStartTaskDispatcher.start()` 调用方式改为：
```java
TaskSortResult result = AppStartTaskSortUtil.getSortResult(mStartTaskList);
mSortTaskList     = result.sortedList;
mTaskHashMap      = result.taskMap;
mTaskChildHashMap = result.childMap;
```
Dispatcher 构造函数中移除对这两个 HashMap 的 `new` 初始化。

### 3.3 删除 `TaskSortModel`

`TaskSortModel.java` 整个文件删除。

### 3.4 `AppStartTask` 三处修复

**① 缓存依赖列表，消除重复调用**

```java
// 字段
private final List<Class<? extends AppStartTask>> mCachedDependsTaskList;
private final CountDownLatch mDepends;

// 构造函数
protected AppStartTask() {
    mCachedDependsTaskList = getDependsTaskList();
    mDepends = new CountDownLatch(
        mCachedDependsTaskList == null ? 0 : mCachedDependsTaskList.size());
}

// 新增 getter，供 SortUtil 使用
public List<Class<? extends AppStartTask>> getCachedDependsTaskList() {
    return mCachedDependsTaskList;
}
```

`SortUtil` 中所有 `task.getDependsTaskList()` 调用改为 `task.getCachedDependsTaskList()`。

**② 方法改名**

`Notify()` → `notifyDependencyFinished()`，`AppStartTaskDispatcher.setNotifyChildren()` 同步更新。

**③ `InterruptedException` 处理**

`waitToNotify()` 改为：
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    Log.w("AppStartTask", "waitToNotify interrupted: " + e.getMessage());
}
```

### 3.5 `AppStartTaskDispatcher` 两处修复

**① `setNotifyChildren()` NPE 防护**
```java
AppStartTask child = mTaskHashMap.get(aclass);
if (child != null) {
    child.notifyDependencyFinished();
}
```

**② `await()` 中断标志恢复**
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    Log.w("AppStartTask", "await interrupted: " + e.getMessage());
}
```

### 3.6 `TaskExecutorManager` 修复

`mHandler` 改为复用单例 fallback 线程池：
```java
private final ExecutorService mFallbackExecutor = Executors.newCachedThreadPool();
private final RejectedExecutionHandler mHandler =
    (r, executor) -> mFallbackExecutor.execute(r);
```

### 3.7 `AppStartTaskLogUtil` 修复

- 删除 `import com.aice.appstartfaster.dispatcher.AppStartTaskDispatcher`
- `Log.e()` 改为 `Log.i()`

---

## 四、变更文件清单

| 操作 | 文件 |
|---|---|
| 新增 | `util/model/TaskSortResult.java` |
| 删除 | `util/model/TaskSortModel.java` |
| 修改 | `util/AppStartTaskSortUtil.java` |
| 修改 | `task/AppStartTask.java` |
| 修改 | `dispatcher/AppStartTaskDispatcher.java` |
| 修改 | `executor/TaskExecutorManager.java` |
| 修改 | `util/AppStartTaskLogUtil.java` |

---

## 五、不在本次范围内

- `app` 模块（Demo 代码）
- Gradle 构建配置
- 新增功能或对外 API
