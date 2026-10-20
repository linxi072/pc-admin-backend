package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.AlertReceiverService;
import com.acme.scaffold.monitor.alert.rules.AlertInboxRules;
import com.acme.scaffold.monitor.alert.rules.AlertInboxRules.Summary;
import com.acme.scaffold.monitor.dto.AlertInboxItemView;
import com.acme.scaffold.monitor.dto.AlertInboxStatsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警收件箱运维查询服务（告警接收模块的管理侧面）。
 *
 * <p><b>定位</b>：告警由 {@link AlertReceiverService#receive} 在 Alertmanager
 * webhook 链路上写入内存收件箱，本服务只提供<b>筛选读取 / 汇总 / 清空</b>，
 * 不参与接收链路，也不提供单条删除——收件箱是「最近 N 条」的有界窗口，
 * 单条删除会制造「跳过某条」的错觉，而清空（整体丢弃窗口）语义清晰无歧义。</p>
 *
 * <p><b>数据口径</b>：读取的是 {@link AlertReceiverService#inbox()} 返回的快照副本
 * （最新在前），筛选与截断在副本上完成，不触碰接收线程的在途状态；
 * 汇总的 severity 分布包含 {@code unknown} 桶，保证「总数 = 各桶之和」。
 * 多实例部署下收件箱为进程本地视图，跨实例汇总需走指标/日志侧（见模块手册）。</p>
 */
@Service
@RequiredArgsConstructor
public class AlertInboxService {

    private final AlertReceiverService alertReceiverService;

    /**
     * 按条件读取收件箱（最新在前）。
     *
     * @param severity 严重级别筛选（大小写不敏感，归一后空值忽略；自由 label 不做白名单）
     * @param status   状态筛选：firing / resolved，白名单外忽略（容错 posture）
     * @param limit    读取条数（{@code null} 取默认 100；按收件箱实际容量自适应钳制）
     * @return 视图列表（不过滤时返回空列表而非 null）
     */
    public List<AlertInboxItemView> list(String severity, String status, Integer limit) {
        int capacity = alertReceiverService.capacity();
        int take = AlertInboxRules.normalizeLimit(limit, capacity);
        List<AlertInboxEntry> filtered = AlertInboxRules.filter(
                alertReceiverService.inbox(), status, severity);
        List<AlertInboxItemView> views = new ArrayList<>(Math.min(filtered.size(), take));
        for (int i = 0; i < filtered.size() && i < take; i++) {
            views.add(AlertInboxItemView.of(filtered.get(i)));
        }
        return views;
    }

    /**
     * 收件箱汇总：容量 / 总数 / 状态分布 / severity 分布 / 最新与最旧留存时间。
     */
    public AlertInboxStatsView stats() {
        Summary summary = AlertInboxRules.summarize(alertReceiverService.inbox(),
                alertReceiverService.capacity());
        return new AlertInboxStatsView(
                summary.capacity(),
                summary.total(),
                summary.firing(),
                summary.resolved(),
                summary.severityCounts(),
                summary.mostRecentEpochMillis(),
                summary.oldestRetainedEpochMillis());
    }

    /**
     * 清空收件箱（整体丢弃当前窗口，返回清理条数）。
     *
     * <p>与「单条删除」的取舍见类注释：窗口型存储只提供整体清空。
     * 已接收的 {@code AlertReceivedEvent} 不受影响（事件在入箱时已发布）。</p>
     */
    public int clear() {
        return alertReceiverService.clearInbox();
    }
}
