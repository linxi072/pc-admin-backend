package com.acme.scaffold.system.log;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.jooq.mapping.SnakeRecordMapper;
import com.acme.scaffold.security.entity.SysLoginLogDO;
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
 * 登录日志查询服务（V29 登录日志页）。
 *
 * <p>只读服务：登录日志由认证链路写入（{@code LoginLogRecorder}），此处仅提供
 * 分页查询与统计摘要，不做任何写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    /** 导出单次最大取数（防止导出大表时耗尽内存；超出部分截断并记录告警）。 */
    private static final int EXPORT_MAX_ROWS = 10_000;

    /** 登录日志可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> LOGIN_LOG_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "username", "username",
            "loginType", "login_type",
            "result", "result",
            "ipAddress", "ip_address",
            "occurredAt", "occurred_at");

    private static final JooqTables.TableRef LL = JooqTables.toTableRef(Tables.SYS_LOGIN_LOG);

    private final DSLContext dsl;

    /**
     * 分页查询登录日志。
     *
     * @param page          页码（从 1 开始）
     * @param size          每页条数
     * @param username      用户名模糊筛选，为空不过滤
     * @param result        结果精确筛选（SUCCESS / FAILURE），为空不过滤
     * @param loginType     登录类型精确筛选，为空不过滤
     * @param failureCode   失败码精确筛选，为空不过滤
     * @param ipAddress     IP 模糊筛选，为空不过滤
     * @param keyword       用户名/IP/失败码联合模糊筛选，为空不过滤
     * @param startTime     起始时间（含），为空默认 7 天前
     * @param endTime       结束时间（含），为空默认当前
     * @param sortField     排序字段（服务端白名单映射，白名单外忽略）
     * @param sortDirection 排序方向：ASC / DESC，为空按 occurred_at DESC
     * @param sensitiveVisible 是否回传未脱敏 IP（由 Controller 依据 {@code system:login-log:sensitive} 权限决定）
     */
    public PageResult<LoginLogView> page(
            int page, int size, String username, String result, String loginType,
            String failureCode, String ipAddress, String keyword,
            LocalDateTime startTime, LocalDateTime endTime, String sortField, String sortDirection,
            boolean sensitiveVisible) {
        Condition cond = buildCondition(username, result, loginType, failureCode, ipAddress,
                keyword, startTime, endTime);
        var pageQuery = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<SysLoginLogDO> raw = JooqWriters.page(dsl, LL,
                SysLoginLogDO.class, cond, pageQuery,
                JooqSorts.resolve(LL, pageQuery, LOGIN_LOG_SORT_FIELDS,
                        Tables.SYS_LOGIN_LOG.OCCURRED_AT.desc()));
        List<LoginLogView> views = raw.records().stream()
                .map(d -> LoginLogView.of(d, sensitiveVisible)).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /**
     * 统计摘要（与 page 相同筛选口径）。
     *
     * @return total / success / failure / failureRate / distinctUsers / lastLoginAt
     */
    public Map<String, Object> summary(String username, String result, String loginType,
                                       String failureCode, String ipAddress, String keyword,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        Condition cond = buildCondition(username, result, loginType, failureCode, ipAddress,
                keyword, startTime, endTime);
        Long total = dsl.selectCount().from(LL.table())
                .where(cond).fetchOne(0, Long.class);
        Long success = dsl.selectCount().from(LL.table())
                .where(DSL.and(cond, Tables.SYS_LOGIN_LOG.RESULT
                        .eq("SUCCESS"))).fetchOne(0, Long.class);
        Integer distinctUsers = dsl.selectDistinct(
                        Tables.SYS_LOGIN_LOG.USERNAME)
                .from(LL.table()).where(cond).fetch().size();
        LocalDateTime lastLoginAt = dsl.select(
                        Tables.SYS_LOGIN_LOG.OCCURRED_AT.max())
                .from(LL.table())
                .where(DSL.and(cond, Tables.SYS_LOGIN_LOG.RESULT
                        .eq("SUCCESS"))).fetchOne(0, LocalDateTime.class);

        long totalVal = total == null ? 0L : total;
        long successVal = success == null ? 0L : success;
        double failureRate = totalVal == 0 ? 0.0 : (double) (totalVal - successVal) / totalVal;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", totalVal);
        out.put("success", successVal);
        out.put("failure", totalVal - successVal);
        out.put("failureRate", failureRate);
        out.put("distinctUsers", distinctUsers);
        out.put("lastLoginAt", lastLoginAt);
        return out;
    }

    /** 统一筛选条件构造：时间区间默认收敛到最近 7 天，防止全表扫描。 */
    private Condition buildCondition(String username, String result, String loginType,
                                     String failureCode, String ipAddress, String keyword,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime begin = startTime == null ? end.minusDays(7) : startTime;
        if (begin.isAfter(end)) {
            begin = end;
        }
        List<Condition> conds = new ArrayList<>();
        conds.add(DSL.and(
                Tables.SYS_LOGIN_LOG.OCCURRED_AT.ge(begin),
                Tables.SYS_LOGIN_LOG.OCCURRED_AT.le(end)));
        if (username != null && !username.isBlank()) {
            conds.add(Tables.SYS_LOGIN_LOG.USERNAME
                    .like("%" + username.trim() + "%"));
        }
        if (result != null && !result.isBlank()) {
            conds.add(Tables.SYS_LOGIN_LOG.RESULT.eq(result.trim()));
        }
        if (loginType != null && !loginType.isBlank()) {
            conds.add(Tables.SYS_LOGIN_LOG.LOGIN_TYPE.eq(loginType.trim()));
        }
        if (failureCode != null && !failureCode.isBlank()) {
            conds.add(Tables.SYS_LOGIN_LOG.FAILURE_CODE
                    .eq(failureCode.trim()));
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            conds.add(Tables.SYS_LOGIN_LOG.IP_ADDRESS
                    .like("%" + ipAddress.trim() + "%"));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            conds.add(DSL.or(
                    Tables.SYS_LOGIN_LOG.USERNAME.like(kw),
                    Tables.SYS_LOGIN_LOG.IP_ADDRESS.like(kw),
                    Tables.SYS_LOGIN_LOG.FAILURE_CODE.like(kw),
                    Tables.SYS_LOGIN_LOG.TRACE_ID.like(kw)));
        }
        return DSL.and(conds);
    }

    /**
     * 导出用批量查询（只读）：与 {@link #page} 采用同一筛选口径与排序（{@code occurred_at DESC}），
     * 但不受分页大小上限（100）约束，供「导出中心」一次性取数。
     *
     * <p><b>安全</b>：导出文件会离开系统边界，故 IP <b>一律掩码</b>——即便导出者持有
     * {@code system:login-log:sensitive} 权限也不放行原文，避免审计数据随文件外流泄漏。</p>
     *
     * @param limit 最大返回行数（内部钳制在 1..{@value #EXPORT_MAX_ROWS}），超出则截断并记录告警
     * @return 登录日志视图列表（IP 已掩码）
     */
    public List<LoginLogView> listForExport(String username, String result, String loginType,
                                            String failureCode, String ipAddress, String keyword,
                                            LocalDateTime startTime, LocalDateTime endTime, int limit) {
        Condition cond = buildCondition(username, result, loginType, failureCode, ipAddress,
                keyword, startTime, endTime);
        int bounded = Math.min(Math.max(limit, 1), EXPORT_MAX_ROWS);
        // 多取一行用于判定是否被截断，便于提示用户缩小范围
        List<SysLoginLogDO> records = dsl.selectFrom(LL.table())
                .where(cond)
                .orderBy(Tables.SYS_LOGIN_LOG.OCCURRED_AT.desc())
                .limit(bounded + 1)
                .fetch(new SnakeRecordMapper<>(SysLoginLogDO.class));
        if (records.size() > bounded) {
            log.warn("登录日志导出达到行数上限 {}，已截断；请缩小时间范围或筛选条件后分批导出", bounded);
            records = records.subList(0, bounded);
        }
        return records.stream().map(d -> LoginLogView.of(d, false)).toList();
    }
}
