package com.acme.scaffold.job.management;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务处理器注册中心：收集容器中所有 {@link JobHandler} 实现，按 {@link JobHandler#key()} 建索引。
 * <p>
 * Spring 构造器注入 {@code List<JobHandler>} 由容器按实现类顺序装配，新增处理器只需实现接口并标注
 * {@code @Component}，无需修改本类（开闭原则）。
 */
@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlerList) {
        Map<String, JobHandler> map = new LinkedHashMap<>();
        for (JobHandler h : handlerList) {
            JobHandler previous = map.put(h.key(), h);
            if (previous != null) {
                throw new IllegalStateException("任务处理器 key 重复: " + h.key()
                        + "（" + previous.getClass().getName() + " 与 " + h.getClass().getName() + "）");
            }
        }
        this.handlers = map;
    }

    /** 按 taskKey 查找处理器；不存在返回空。 */
    public Optional<JobHandler> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(key));
    }

    /** 全部已注册处理器（不可变视图），用于前端下拉选项。 */
    public Map<String, JobHandler> all() {
        return Map.copyOf(handlers);
    }

    /** 是否已注册指定 processor。 */
    public boolean contains(String key) {
        return key != null && handlers.containsKey(key);
    }
}
