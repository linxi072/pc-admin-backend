package com.acme.scaffold.common.id;

import com.acme.scaffold.common.IdGenerator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花 ID {@code workerId} 初始化器。
 *
 * <p>{@link IdGenerator} 默认 {@code workerId=1}：多实例部署时所有实例相同，
 * 同毫秒同序号即生成重复 ID（跨实例主键冲突 / 插入 500）。本组件把
 * {@code app.id.worker-id}（0~1023）配置接入启动流程；未配置时维持默认值并输出
 * 醒目警告，提醒多实例部署务必为每个实例分配不同值。</p>
 */
@Component
public class IdGeneratorInitializer {

    private static final Logger log = LoggerFactory.getLogger(IdGeneratorInitializer.class);

    private final Long workerId;

    public IdGeneratorInitializer(@Value("${app.id.worker-id:}") String workerId) {
        if (workerId == null || workerId.isBlank()) {
            this.workerId = null;
            return;
        }
        try {
            this.workerId = Long.valueOf(workerId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("app.id.worker-id 必须为整数: " + workerId, e);
        }
    }

    @PostConstruct
    public void init() {
        if (workerId == null) {
            log.warn("未配置 app.id.worker-id，雪花 ID 使用默认 workerId=1；"
                    + "多实例部署必须为每个实例配置不同的 workerId（0~1023），否则可能产生重复 ID");
            return;
        }
        try {
            IdGenerator.setWorkerId(workerId);
            log.info("雪花 ID workerId 已配置: {}", workerId);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.id.worker-id 非法: " + e.getMessage(), e);
        }
    }
}
