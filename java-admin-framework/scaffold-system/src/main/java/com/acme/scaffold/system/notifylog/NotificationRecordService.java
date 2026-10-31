package com.acme.scaffold.system.notifylog;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.notifylog.domain.SysNotificationRecordDO;
import com.acme.scaffold.system.notifylog.rules.NotificationRecordRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知发送记录查询服务（V42 通知记录页）。
 *
 * <p>只读服务：记录由 {@link NotificationRecordRecorder} 在每次 {@code NotificationService.dispatch}
 * 尝试时写入，此处仅提供分页查询与统计摘要，不做任何写操作。</p>
 *
 * <p>筛选值一律经 {@link NotificationRecordRules} 归一（渠道/状态白名单），
 * 非法值忽略而非报错——与短信记录页「容错查询」体验一致。</p>
 *
 * <p><b>口径提示</b>：本表覆盖全部五渠道的每次 dispatch 尝试（含被拒与失败）；
 * 短信网关对账请看 {@code sys_sms_record}（仅校验后实际尝试的投递），一次短信发送双写两表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRecordService {

    /** 通知记录可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> RECORD_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "channel", "channel",
            "templateCode", "template_code",
            "receiverCount", "receiver_count",
            "status", "status",
            "operatorName", "operator_name",
            "sentAt", "sent_at");

    /** 桥接自代码生成层 {@code Tables.SYS_NOTIFICATION_RECORD} 的类型安全表引用（B24）。 */
    private static final JooqTables.TableRef RECORD =
            JooqTables.SYS_NOTIFICATION_RECORD;

    private final DSLContext dsl;

    /**
     * 分页查询通知发送记录。
     *
     * @param page          页码（从 1 开始）
     * @param size          每页条数
     * @param channel       渠道精确筛选（IN_APP / WEBHOOK / WEBSOCKET / EMAIL / SMS），归一后非法值忽略
     * @param status        结果精确筛选（SUCCESS / FAILED / SKIPPED / DIGEST_PENDING），归一后非法值忽略
     * @param keyword       渠道/模板/标题/正文摘要/接收方/失败原因/traceId/操作人联合模糊筛选，为空不过滤
     * @param startTime     起始时间（含），为空默认 7 天前
     * @param endTime       结束时间（含），为空默认当前
     * @param sortField     排序字段（服务端白名单映射，白名单外忽略）
     * @param sortDirection 排序方向：ASC / DESC，为空按 sent_at DESC
     */
    public PageResult<NotificationRecordView> page(
            int page, int size, String channel, String status, String keyword,
            LocalDateTime startTime, LocalDateTime endTime, String sortField, String sortDirection) {
        Condition cond = buildCondition(channel, status, keyword, startTime, endTime);
        var pageQuery = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<SysNotificationRecordDO> raw = JooqWriters.page(dsl, RECORD,
                SysNotificationRecordDO.class, cond, pageQuery,
                JooqSorts.resolve(RECORD, pageQuery, RECORD_SORT_FIELDS,
                        JooqTables.SYS_NOTIFICATION_RECORD.field("sent_at", java.time.LocalDateTime.class).desc()));
        List<NotificationRecordView> views = raw.records().stream()
                .map(NotificationRecordView::of).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /**
     * 统计摘要（与 page 相同筛选口径）。
     *
     * <p>M13 起 {@code SKIPPED}（全部接收者退订）单列、M17 起
     * {@code DIGEST_PENDING}（聚合中）单列：失败数 = 总数 − 成功 − 跳过 − 聚合中，
     * 保证「失败率」只反映真实发送故障，不把用户主动退订或正常频次治理误计为故障。</p>
     *
     * @return total / success / failure / skipped / digestPending / failureRate / lastSentAt
     */
    public Map<String, Object> summary(String channel, String status, String keyword,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        Condition cond = buildCondition(channel, status, keyword, startTime, endTime);
        Long total = dsl.selectCount().from(RECORD.table())
                .where(cond).fetchOne(0, Long.class);
        Long success = dsl.selectCount().from(RECORD.table())
                .where(DSL.and(cond, JooqTables.SYS_NOTIFICATION_RECORD.field("status", String.class)
                        .eq(NotificationRecordRules.STATUS_SUCCESS))).fetchOne(0, Long.class);
        Long skipped = dsl.selectCount().from(RECORD.table())
                .where(DSL.and(cond, JooqTables.SYS_NOTIFICATION_RECORD.field("status", String.class)
                        .eq(NotificationRecordRules.STATUS_SKIPPED))).fetchOne(0, Long.class);
        Long digestPending = dsl.selectCount().from(RECORD.table())
                .where(DSL.and(cond, JooqTables.SYS_NOTIFICATION_RECORD.field("status", String.class)
                        .eq(NotificationRecordRules.STATUS_DIGEST_PENDING))).fetchOne(0, Long.class);
        LocalDateTime lastSentAt = dsl.select(DSL.max(JooqTables.SYS_NOTIFICATION_RECORD.field("sent_at", java.time.LocalDateTime.class)))
                .from(RECORD.table())
                .where(cond).fetchOne(0, LocalDateTime.class);
        return buildSummary(total == null ? 0L : total, success == null ? 0L : success,
                skipped == null ? 0L : skipped, digestPending == null ? 0L : digestPending, lastSentAt);
    }

    /**
     * 摘要 Map 组装（纯函数，与 DB 解耦便于离线单测）。
     *
     * @param total         总记录数
     * @param success       成功数
     * @param skipped       跳过数（M13：全部接收者退订；不计入失败）
     * @param digestPending 聚合中数（M17：已进入 digest 队列等待摘要投递；不计入失败）
     * @param lastSentAt    最近一次发送时间（无记录为 null）
     */
    static Map<String, Object> buildSummary(long total, long success, long skipped,
                                            long digestPending, LocalDateTime lastSentAt) {
        long failure = total - success - skipped - digestPending;
        double failureRate = total == 0 ? 0.0 : (double) failure / total;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("success", success);
        out.put("failure", failure);
        out.put("skipped", skipped);
        out.put("digestPending", digestPending);
        out.put("failureRate", failureRate);
        out.put("lastSentAt", lastSentAt);
        return out;
    }

    /** 统一筛选条件构造：时间区间默认收敛到最近 7 天，防止全表扫描。 */
    private Condition buildCondition(String channel, String status, String keyword,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime begin = startTime == null ? end.minusDays(7) : startTime;
        if (begin.isAfter(end)) {
            begin = end;
        }
        List<Condition> conds = new ArrayList<>();
        conds.add(DSL.and(
                JooqTables.SYS_NOTIFICATION_RECORD.field("sent_at", java.time.LocalDateTime.class).ge(begin),
                JooqTables.SYS_NOTIFICATION_RECORD.field("sent_at", java.time.LocalDateTime.class).le(end)));
        String ch = NotificationRecordRules.normalizeChannel(channel);
        if (ch != null) {
            conds.add(JooqTables.SYS_NOTIFICATION_RECORD.field("channel", String.class).eq(ch));
        }
        String st = NotificationRecordRules.normalizeStatus(status);
        if (st != null) {
            conds.add(JooqTables.SYS_NOTIFICATION_RECORD.field("status", String.class).eq(st));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            conds.add(DSL.or(
                    JooqTables.SYS_NOTIFICATION_RECORD.field("channel", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_RECORD.field("template_code", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_RECORD.field("title_summary", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_RECORD.field("content_summary", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_RECORD.field("receiver_summary", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_RECORD.field("error_message", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_RECORD.field("trace_id", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_RECORD.field("operator_name", String.class).like(kw)));
        }
        return DSL.and(conds);
    }
}
