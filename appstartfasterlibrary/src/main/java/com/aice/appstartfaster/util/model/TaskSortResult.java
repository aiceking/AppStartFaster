package com.aice.appstartfaster.util.model;

import com.aice.appstartfaster.task.AppStartTask;

import java.util.HashMap;
import java.util.List;

public record TaskSortResult(List<AppStartTask> sortedList,
                             HashMap<Class<? extends AppStartTask>, AppStartTask> taskMap,
                             HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> childMap) {
}
