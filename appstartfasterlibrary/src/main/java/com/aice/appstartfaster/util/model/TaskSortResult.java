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
