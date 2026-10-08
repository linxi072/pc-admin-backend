package com.acme.scaffold.job;

import com.acme.scaffold.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 幂等记录清理任务（迁移前对应 XXL-JOB 的清理 handler）。
 *
 * <p>幂等表写入频繁但生命周期短（默认 24h），不清理会持续膨胀并拖慢唯一索引查找。
 * 删除已过期记录即可：过期后即便同一 key 再次出现，也视为一次全新业务操作——
 * 这与「幂等窗口」的语义一致（超出窗口的重试本就不应被自动去重）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

    private final IdempotencyService idempotencyService;

    public void purgeExpired() {
        int removed = idempotencyService.purgeExpired();
        if (removed > 0) {
            log.info("清理过期幂等记录 {} 条", removed);
        }
    }
}
