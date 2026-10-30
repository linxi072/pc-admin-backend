package com.acme.scaffold.system.notifydigest.job;

import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notification.service.NotificationService;
import com.acme.scaffold.system.notifydigest.NotificationDigestService;
import com.acme.scaffold.system.notifydigest.domain.SysNotificationDigestDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知聚合摘要兜底刷新定时任务（M17）：周期扫描 {@code last_at} 超过
 * {@code app.notifications.digest.max-wait-minutes} 的聚合行，构造摘要消息并经
 * {@link NotificationService} 走完整 dispatch 链路投递（摘要消息天然复用 M12 留痕、
 * M13 偏好透传、M14 免打扰、M16 重试/故障转移），投递尝试完成后删行。
 *
 * <p><b>为什么需要兜底</b>：阈值刷新（{@code threshold}）只覆盖「消息够密」的用户；
 * 低流量用户的聚合行可能永远差一条，若无本任务摘要将无限期囤在缓冲区——
 * 与 M14 免打扰的时间维理念一致：任何「等待」都必须有上界。</p>
 *
 * <p><b>编排归属</b>：本任务同时依赖聚合服务与编排服务，是二者唯一的组合点。
 * 刻意置于 {@code notifydigest.job} 子包而非特性包根部——包级依赖
 * {@code notification.service → notifydigest}（dispatch 挂点）与
 * {@code notifydigest → notification.service}（本任务）若同包即成环（H1 硬规则），
 * 任务层独立成包后依赖图仍为 DAG。</p>
 *
 * <p><b>一次性投递语义</b>：摘要投递无论成功/跳过/失败均删行（finally）——
 * 聚合行是待投递缓冲而非档案，反复重投只会制造 FAILED 日志轰炸，审计由 M12 承担；
 * 行级异常（含 dispatch 抛出的 FAILED）仅 warn 日志，不中断本批其余行，
 * 也不向上传播（定时任务单次执行不应因单行故障整体失败）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDigestJob {

    /** 单批刷新行数上限：与兜底扫描周期（默认 60s）配合控制单次执行时长。 */
    private static final int DUE_BATCH = 100;

    private final NotificationDigestService digestService;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString =
            "${app.notifications.digest.scan-interval-ms:60000}")
    public void flushDue() {
        if (!digestService.isEnabled()) {
            return;
        }
        List<SysNotificationDigestDO> due = digestService.findDue(DUE_BATCH);
        if (due.isEmpty()) {
            return;
        }
        int deleted = 0;
        for (SysNotificationDigestDO row : due) {
            NotificationMessage digestMessage = digestService.buildDigestMessage(row);
            if (digestMessage == null) {
                // 无处可投（用户已删 / 渠道地址缺失）：直接删行，不进投递链路
                digestService.delete(row.getId());
                deleted++;
                continue;
            }
            try {
                notificationService.dispatch(digestMessage);
            } catch (RuntimeException ex) {
                // 摘要投递失败（渠道异常等）：行仍按一次性语义删除，审计由 M12 FAILED 记录承担
                log.warn("摘要刷新投递失败 digestId={} userId={} err={}",
                        row.getId(), row.getUserId(), ex.toString());
            } finally {
                digestService.delete(row.getId());
                deleted++;
            }
        }
        log.debug("摘要兜底刷新完成 due={} deleted={}", due.size(), deleted);
    }
}
