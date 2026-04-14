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
