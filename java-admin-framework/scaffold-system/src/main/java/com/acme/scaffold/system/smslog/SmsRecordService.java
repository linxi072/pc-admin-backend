package com.acme.scaffold.system.smslog;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.smslog.domain.SysSmsRecordDO;
import com.acme.scaffold.system.smslog.rules.SmsRecordRules;
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
 * 短信发送记录查询服务（V37 短信记录页）。
 *
 * <p>只读服务：记录由 {@link SmsRecordRecorder} 在短信渠道投递时写入，
 * 此处仅提供分页查询与统计摘要，不做任何写操作。</p>
 *
 * <p>筛选值一律经 {@link SmsRecordRules} 归一（状态/提供方白名单），
 * 非法值忽略而非报错——与登录日志页「容错查询」体验一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsRecordService {

    /** 短信记录可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> SMS_RECORD_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "batchNo", "batch_no",
            "status", "status",
            "provider", "provider",
            "mobileCount", "mobile_count",
            "operatorName", "operator_name",
            "sentAt", "sent_at");

    /** 桥接自代码生成层 {@code Tables.SYS_SMS_RECORD} 的类型安全表引用（B23）。 */
    private static final JooqTables.TableRef SMS_RECORD =
            JooqTables.SYS_SMS_RECORD;

    private final DSLContext dsl;

    /**
     * 分页查询短信发送记录。
     *
     * @param page          页码（从 1 开始）
     * @param size          每页条数
     * @param status        结果精确筛选（SUCCESS / FAILED），归一后非法值忽略
     * @param provider      网关精确筛选（LOG / HTTP），归一后非法值忽略
     * @param keyword       批次号/操作人/失败原因/traceId 联合模糊筛选，为空不过滤
     * @param startTime     起始时间（含），为空默认 7 天前
     * @param endTime       结束时间（含），为空默认当前
     * @param sortField     排序字段（服务端白名单映射，白名单外忽略）
     * @param sortDirection 排序方向：ASC / DESC，为空按 sent_at DESC
     */
    public PageResult<SmsRecordView> page(
            int page, int size, String status, String provider, String keyword,
            LocalDateTime startTime, LocalDateTime endTime, String sortField, String sortDirection) {
        Condition cond = buildCondition(status, provider, keyword, startTime, endTime);
        var pageQuery = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<SysSmsRecordDO> raw = JooqWriters.page(dsl, SMS_RECORD,
                SysSmsRecordDO.class, cond, pageQuery,
                JooqSorts.resolve(SMS_RECORD, pageQuery, SMS_RECORD_SORT_FIELDS,
                        JooqTables.SYS_SMS_RECORD.field("sent_at", java.time.LocalDateTime.class).desc()));
        List<SmsRecordView> views = raw.records().stream()
                .map(SmsRecordView::of).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /**
     * 统计摘要（与 page 相同筛选口径）。
     *
     * @return total / success / failure / failureRate / lastSentAt
     */
    public Map<String, Object> summary(String status, String provider, String keyword,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        Condition cond = buildCondition(status, provider, keyword, startTime, endTime);
        Long total = dsl.selectCount().from(SMS_RECORD.table())
                .where(cond).fetchOne(0, Long.class);
        Long success = dsl.selectCount().from(SMS_RECORD.table())
                .where(DSL.and(cond, JooqTables.SYS_SMS_RECORD.field("status", String.class)
                        .eq(SmsRecordRules.STATUS_SUCCESS))).fetchOne(0, Long.class);
        LocalDateTime lastSentAt = dsl.select(DSL.max(JooqTables.SYS_SMS_RECORD.field("sent_at", java.time.LocalDateTime.class)))
                .from(SMS_RECORD.table())
                .where(cond).fetchOne(0, LocalDateTime.class);
        return buildSummary(total == null ? 0L : total, success == null ? 0L : success, lastSentAt);
    }

    /**
     * 摘要 Map 组装（纯函数，与 DB 解耦便于离线单测）。
     *
     * @param total      总记录数
     * @param success    成功数
     * @param lastSentAt 最近一次发送时间（无记录为 null）
     */
    static Map<String, Object> buildSummary(long total, long success, LocalDateTime lastSentAt) {
        double failureRate = total == 0 ? 0.0 : (double) (total - success) / total;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("success", success);
        out.put("failure", total - success);
        out.put("failureRate", failureRate);
        out.put("lastSentAt", lastSentAt);
        return out;
    }

    /** 统一筛选条件构造：时间区间默认收敛到最近 7 天，防止全表扫描。 */
    private Condition buildCondition(String status, String provider, String keyword,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime begin = startTime == null ? end.minusDays(7) : startTime;
        if (begin.isAfter(end)) {
            begin = end;
        }
        List<Condition> conds = new ArrayList<>();
        conds.add(DSL.and(
                JooqTables.SYS_SMS_RECORD.field("sent_at", java.time.LocalDateTime.class).ge(begin),
                JooqTables.SYS_SMS_RECORD.field("sent_at", java.time.LocalDateTime.class).le(end)));
        String st = SmsRecordRules.normalizeStatus(status);
        if (st != null) {
            conds.add(JooqTables.SYS_SMS_RECORD.field("status", String.class).eq(st));
        }
        String pv = SmsRecordRules.normalizeProvider(provider);
        if (pv != null) {
            conds.add(JooqTables.SYS_SMS_RECORD.field("provider", String.class).eq(pv));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            conds.add(DSL.or(
                    JooqTables.SYS_SMS_RECORD.field("batch_no", String.class).like(kw),
                    JooqTables.SYS_SMS_RECORD.field("operator_name", String.class).like(kw),
                    JooqTables.SYS_SMS_RECORD.field("error_message", String.class).like(kw),
                    JooqTables.SYS_SMS_RECORD.field("trace_id", String.class).like(kw)));
        }
        return DSL.and(conds);
    }
}
