package com.acme.scaffold.security.log;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.entity.SysLoginLogDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录日志记录器：将登录成功 / 失败事件落入 {@code sys_login_log}，
 * 为「报表管理 - 登录统计」（登录次数、活跃用户趋势、异常登录记录）提供数据源。
 *
 * <p><b>独立事务（REQUIRES_NEW）</b>：登录主方法 {@code AuthApplicationService#login} 为事务方法，
 * 失败路径抛出业务异常会整体回滚——若失败日志与主事务同生共死，异常登录记录将永远丢失，
 * 统计口径出现「有失败计数、无失败明细」的割裂。故写入一律挂起主事务、以新事务提交。</p>
 *
 * <p>记录失败不影响登录主流程（仅 warn 日志），确保审计缺失不阻断业务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogRecorder {

    private final DSLContext dsl;

    /** 登录方式：当前仅口令登录（预留扩展：短信 / 扫码 / SSO）。 */
    public static final String LOGIN_TYPE_PASSWORD = "PASSWORD";
    /** 结果码：成功。 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    /** 结果码：失败。 */
    public static final String RESULT_FAILURE = "FAILURE";

    private static final int MAX_UA_LEN = 500;

    /**
     * 记录一次登录事件。
     *
     * @param username    用户名（用户不存在时亦记录尝试名，供异常登录分析）
     * @param userId      用户主键（用户不存在时为 null）
     * @param success     是否登录成功
     * @param failureCode 失败码（成功时传 null，如 BAD_CREDENTIALS / ACCOUNT_LOCKED）
     * @param ip          客户端 IP
     * @param userAgent   客户端 UA（超长截断至 500 字符）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, Long userId, boolean success, String failureCode,
                       String ip, String userAgent) {
        try {
            SysLoginLogDO row = new SysLoginLogDO();
            row.setUsername(truncate(username, 64));
            row.setUserId(userId);
            row.setLoginType(LOGIN_TYPE_PASSWORD);
            row.setResult(success ? RESULT_SUCCESS : RESULT_FAILURE);
            row.setFailureCode(truncate(failureCode, 64));
            row.setIpAddress(truncate(ip, 64));
            row.setUserAgent(truncate(userAgent, MAX_UA_LEN));
            row.setTraceId(MDC.get("traceId"));
            row.setOccurredAt(LocalDateTime.now());
            JooqWriters.insert(dsl, JooqTables.SYS_LOGIN_LOG, row);
        } catch (Exception e) {
            // 登录日志属审计旁路，写入失败不阻断登录主流程
            log.warn("登录日志写入失败 username={} success={} err={}", username, success, e.getMessage());
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
