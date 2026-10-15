package com.acme.scaffold.system.log;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
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

    /** 登录日志可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> LOGIN_LOG_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "username", "username",
            "loginType", "login_type",
            "result", "result",
            "ipAddress", "ip_address",
            "occurredAt", "occurred_at");

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
     */
    public PageResult<LoginLogView> page(
            int page, int size, String username, String result, String loginType,
            String failureCode, String ipAddress, String keyword,
            LocalDateTime startTime, LocalDateTime endTime, String sortField, String sortDirection) {
        Condition cond = buildCondition(username, result, loginType, failureCode, ipAddress,
                keyword, startTime, endTime);
        var pageQuery = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<SysLoginLogDO> raw = JooqWriters.page(dsl, JooqTables.SYS_LOGIN_LOG,
                SysLoginLogDO.class, cond, pageQuery,
                JooqSorts.resolve(JooqTables.SYS_LOGIN_LOG, pageQuery, LOGIN_LOG_SORT_FIELDS,
                        JooqTables.SYS_LOGIN_LOG.field("occurred_at", LocalDateTime.class).desc()));
        List<LoginLogView> views = raw.records().stream().map(this::toView).toList();
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
        Long total = dsl.selectCount().from(JooqTables.SYS_LOGIN_LOG.table())
                .where(cond).fetchOne(0, Long.class);
        Long success = dsl.selectCount().from(JooqTables.SYS_LOGIN_LOG.table())
                .where(DSL.and(cond, JooqTables.SYS_LOGIN_LOG.field("result", String.class)
                        .eq("SUCCESS"))).fetchOne(0, Long.class);
        Integer distinctUsers = dsl.selectDistinct(
                        JooqTables.SYS_LOGIN_LOG.field("username", String.class))
                .from(JooqTables.SYS_LOGIN_LOG.table()).where(cond).fetch().size();
        LocalDateTime lastLoginAt = dsl.select(
                        JooqTables.SYS_LOGIN_LOG.field("occurred_at", LocalDateTime.class).max())
                .from(JooqTables.SYS_LOGIN_LOG.table())
                .where(DSL.and(cond, JooqTables.SYS_LOGIN_LOG.field("result", String.class)
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
                JooqTables.SYS_LOGIN_LOG.field("occurred_at", LocalDateTime.class).ge(begin),
                JooqTables.SYS_LOGIN_LOG.field("occurred_at", LocalDateTime.class).le(end)));
        if (username != null && !username.isBlank()) {
            conds.add(JooqTables.SYS_LOGIN_LOG.field("username", String.class)
                    .like("%" + username.trim() + "%"));
        }
        if (result != null && !result.isBlank()) {
            conds.add(JooqTables.SYS_LOGIN_LOG.field("result", String.class).eq(result.trim()));
        }
        if (loginType != null && !loginType.isBlank()) {
            conds.add(JooqTables.SYS_LOGIN_LOG.field("login_type", String.class).eq(loginType.trim()));
        }
        if (failureCode != null && !failureCode.isBlank()) {
            conds.add(JooqTables.SYS_LOGIN_LOG.field("failure_code", String.class)
                    .eq(failureCode.trim()));
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            conds.add(JooqTables.SYS_LOGIN_LOG.field("ip_address", String.class)
                    .like("%" + ipAddress.trim() + "%"));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            conds.add(DSL.or(
                    JooqTables.SYS_LOGIN_LOG.field("username", String.class).like(kw),
                    JooqTables.SYS_LOGIN_LOG.field("ip_address", String.class).like(kw),
                    JooqTables.SYS_LOGIN_LOG.field("failure_code", String.class).like(kw),
                    JooqTables.SYS_LOGIN_LOG.field("trace_id", String.class).like(kw)));
        }
        return DSL.and(conds);
    }

    private LoginLogView toView(SysLoginLogDO d) {
        return new LoginLogView(d.getId(), d.getUserId(), d.getUsername(), d.getLoginType(),
                d.getResult(), d.getFailureCode(), d.getIpAddress(), d.getUserAgent(),
                d.getTraceId(), d.getOccurredAt());
    }
}
