package com.aice.appstartfaster.util;


import com.aice.appstartfaster.task.AppStartTask;
import com.aice.appstartfaster.util.model.TaskSortModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;

public class AppStartTaskSortUtil {
    /**
     * 拓扑排序
     * taskIntegerHashMap每个Task的入度（key= Class < ? extends AppStartTask>）
     * taskHashMap每个Task            （key= Class < ? extends AppStartTask>）
     * taskChildHashMap每个Task的孩子  （key= Class < ? extends AppStartTask>）
     * deque 入度为0的Task
     * */
    public static List<AppStartTask> getSortResult(List<AppStartTask> startTaskList, HashMap<Class<? extends AppStartTask>, AppStartTask> taskHashMap, HashMap<Class<? extends AppStartTask>, List<Class<? extends AppStartTask>>> taskChildHashMap){
        List<AppStartTask> sortTaskList = new ArrayList<>();
        HashMap<Class<? extends AppStartTask>, TaskSortModel> taskIntegerHashMap=new HashMap<>();
        Deque<Class<? extends AppStartTask>> deque = new ArrayDeque<>();
        for (AppStartTask task:startTaskList){
            if (!taskIntegerHashMap.containsKey(task.getClass())){
                taskHashMap.put(task.getClass(),task);
                taskIntegerHashMap.put(task.getClass(),new TaskSortModel(task.getDependsTaskList()==null?0:task.getDependsTaskList().size()));
                taskChildHashMap.put(task.getClass(),new ArrayList<Class<? extends AppStartTask>>());
                //入度为0的队列
                if (taskIntegerHashMap.get(task.getClass()).getIn()==0){
                    deque.offer(task.getClass());
                }
            }else{
                throw new RuntimeException("任务重复了: "+task.getClass());
            }
        }
        //把孩子都加进去
        for (AppStartTask task:startTaskList){
             if (task.getDependsTaskList()!=null){
                 for (Class<? extends AppStartTask> aclass:task.getDependsTaskList()){
                     taskChildHashMap.get(aclass).add(task.getClass());
                 }
             }
        }
        //循环去除入度0的，再把孩子入度变成0的加进去
        while (!deque.isEmpty()){
            Class<? extends AppStartTask> aclass=deque.poll();
            sortTaskList.add(taskHashMap.get(aclass));
            for (Class<? extends AppStartTask> classChild:taskChildHashMap.get(aclass)){
                        taskIntegerHashMap.get(classChild).setIn(taskIntegerHashMap.get(classChild).getIn()-1);
                        if (taskIntegerHashMap.get(classChild).getIn()==0){
                            deque.offer(classChild);
                        }
                }
        }
        if (sortTaskList.size()!=startTaskList.size()){
            throw new RuntimeException("出现环了");
        }
        return sortTaskList;
    }
}
