package com.acme.scaffold.cms.article.job;

import com.acme.scaffold.cms.article.service.CmsArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时发布扫描任务（C5）：周期将到期的 SCHEDULED 文章转为 PUBLISHED。
 *
 * <p>开关 {@code cms.scheduled-publish.enabled} 默认开启（matchIfMissing=true）；
 * 设为 false 时本 Bean 不注册，定时发布降级为手动，与需求「无调度时降级手动」一致。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cms.scheduled-publish", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CmsScheduledPublishJob {

    private final CmsArticleService articleService;

    @Scheduled(fixedDelayString = "${cms.scheduled-publish.scan-interval-ms:30000}")
    public void publishDue() {
        try {
            int count = articleService.publishDue();
            if (count > 0) {
                log.info("CMS 定时发布扫描：本次发布 {} 篇", count);
            }
        } catch (RuntimeException e) {
            log.error("CMS 定时发布扫描异常", e);
        }
    }
}
