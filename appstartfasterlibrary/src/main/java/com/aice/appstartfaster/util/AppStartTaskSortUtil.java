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
