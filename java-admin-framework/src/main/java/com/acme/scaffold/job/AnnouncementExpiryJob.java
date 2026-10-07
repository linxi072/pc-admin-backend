package com.acme.scaffold.job;

import com.acme.scaffold.system.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 公告周期任务。
 * <p>由 {@link com.acme.scaffold.config.JobRunrConfig} 注册为 JobRunr 周期任务
 * {@code announcement-expiry-job}：每 5 分钟扫描过期公告并自动下线。
 * <p>公告「定时生效」<b>不依赖</b>此任务：到达 publishAt 后由查询侧判定可见
 * （见 {@code AnnouncementView#isEffective}），因此即使任务延迟也不会让公告
 * 「该生效却没生效」。本任务只负责已发布公告到期后的自动下线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementExpiryJob {

    private final AnnouncementService announcementService;

    /** 扫描并下线已过有效期的公告。 */
    public void offlineExpired() {
        try {
            int count = announcementService.offlineExpired();
            if (count > 0) {
                log.info("已自动下线过期公告 {} 条", count);
            }
        } catch (Exception e) {
            log.error("自动下线过期公告失败", e);
        }
    }
}