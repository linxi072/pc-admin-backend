package com.acme.scaffold.job.handler;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置周期任务的注册表（任务 key → 可执行的 Spring Bean 方法）。
 *
 * <p>Quartz 的 {@code JobDetail} 只保存可序列化元数据，不直接持有业务 Runnable；
 * 内置任务改由 {@link #register(String, Runnable)} 在应用启动时登记，
 * {@code BuiltinQuartzJob} 触发时按 JobDataMap 中的 taskKey 查表执行。
 * 使用 RAMJobStore（内存存储），无需持久化任务实例。
 */
@Component
public class BuiltinJobTasks {

    private final Map<String, Runnable> tasks = new ConcurrentHashMap<>();

    public void register(String taskKey, Runnable task) {
        tasks.put(taskKey, task);
    }

    public Runnable get(String taskKey) {
        return taskKey == null ? null : tasks.get(taskKey);
    }

    public Map<String, Runnable> snapshot() {
        return Collections.unmodifiableMap(tasks);
    }
}
