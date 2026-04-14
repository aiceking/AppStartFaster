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
import static org.junit.Assert.fail;

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

    // 依赖未注册任务的测试任务（依赖 TaskA，但 TaskA 不会被加入 startTaskList）
    static class TaskOnlyB extends AppStartTask {
        @Override public void run() {}
        @Override public boolean isRunOnMainThread() { return false; }
        @Override public List<Class<? extends AppStartTask>> getDependsTaskList() {
            return Collections.singletonList(TaskA.class);
        }
    }

    // ---- 测试用例 ----

    @Test
    public void getSortResult_linearDependency_correctOrder() {
        // 乱序输入：C、A、B，期望输出：A → B → C
        List<AppStartTask> tasks = Arrays.asList(new TaskC(), new TaskA(), new TaskB());
        TaskSortResult result = AppStartTaskSortUtil.getSortResult(tasks);

        assertEquals(3, result.sortedList().size());
        assertTrue(result.sortedList().get(0) instanceof TaskA);
        assertTrue(result.sortedList().get(1) instanceof TaskB);
        assertTrue(result.sortedList().get(2) instanceof TaskC);
    }

    @Test
    public void getSortResult_singleTask_returnsIt() {
        List<AppStartTask> tasks = Collections.singletonList(new TaskA());
        TaskSortResult result = AppStartTaskSortUtil.getSortResult(tasks);

        assertEquals(1, result.sortedList().size());
        assertNotNull(result.taskMap());
        assertNotNull(result.childMap());
        assertTrue(result.taskMap().containsKey(TaskA.class));
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
}
