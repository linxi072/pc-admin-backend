package com.acme.scaffold.security.log;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志实体（对应 {@code sys_login_log}，DDL 见 V2__security.sql）。
 *
 * <p>登录成功 / 失败均落一条，为「报表管理 - 登录统计」提供数据源：
 * 登录次数、活跃用户趋势、异常登录记录。</p>
 */
@Data
public class SysLoginLogDO {

    private Long id;

    private Long tenantId = 0L;

    /** 用户主键（用户不存在时为 null，此时 username 为尝试登录名）。 */
    private Long userId;

    /** 用户名（冗余存储，用户删除后日志仍可读）。 */
    private String username;

    /** 登录方式：PASSWORD（预留短信 / 扫码 / SSO）。 */
    private String loginType;

    /** 结果：SUCCESS / FAILURE。 */
    private String result;

    /** 失败码（成功为 null，如 BAD_CREDENTIALS / ACCOUNT_LOCKED）。 */
    private String failureCode;

    private String ipAddress;

    /** 客户端 UA（超长截断至 500 字符）。 */
    private String userAgent;

    /** 链路追踪 ID。 */
    private String traceId;

    private LocalDateTime occurredAt;
}
