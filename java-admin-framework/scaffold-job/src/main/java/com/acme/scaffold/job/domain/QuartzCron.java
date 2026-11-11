package com.acme.scaffold.job.domain;

import java.text.ParseException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import org.quartz.CronExpression;

/**
 * Quartz cron 归一化与解析（纯函数，无 Spring / DB 依赖）。
 *
 * <p>业务侧历史 cron 采用 5 段分钟精度（例如「每 5 分钟」写作 0/5 加四个通配位，原 JobRunr 口径），
 * 而 Quartz 要求 6/7 段（秒 分 时 日 月 周 [年]）且「日」与「周」不能同时为 {@code *}。
 * 本类在**保留用户可见输入不变**的前提下，把业务 cron 转换为 Quartz cron：
 * <ul>
 *   <li>5 段 → 前置秒位 {@code 0}，得到 6 段 Quartz 格式；</li>
 *   <li>6 / 7 段 → 视为已是 Quartz 格式，仅处理日/周冲突；</li>
 *   <li>字段索引固定为 {@code [秒, 分, 时, 日(3), 月(4), 周(5), 年(6)]}；
 *       「周」是第 6 段（下标 5），7 段表达式末尾还有「年」，不可误把「月」当「周」；</li>
 *   <li>周位为 {@code *}/{@code ?} 时统一置 {@code ?}（日位为 {@code ?} 则回填 {@code *}）；
 *       周位为具体值时把日位置 {@code ?}；日位与周位同时为具体值时交给 Quartz 校验失败。</li>
 * </ul>
 *
 * <p>校验（{@link JobValidator}）、下次执行时间计算（{@link JobNextFire}）与真实注册
 * （{@code QuartzSchedulerPort}）共用本类，确保「校验通过」即「可被调度」，杜绝格式分歧。
 */
public final class QuartzCron {

    /** 业务最小间隔（秒），沿用原调度引擎的 5 秒约束，避免高频空转。 */
    public static final int SMALLEST_SCHEDULE_IN_SECONDS = 5;

    private QuartzCron() {
    }

    /** 把业务 cron 归一化为 Quartz cron；格式非法时抛 {@link IllegalArgumentException}。 */
    public static String normalize(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("cron 表达式不能为空");
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 7) {
            throw new IllegalArgumentException("cron 表达式须为 5/6/7 段，实际 " + parts.length + " 段");
        }
        String[] q = parts.length == 5
                ? new String[]{"0", parts[0], parts[1], parts[2], parts[3], parts[4]}
                : parts.clone();
        // Quartz 不允许 day-of-month 与 day-of-week 同时为具体值，且至少一侧须为 '?'。
        // 注意：6/7 段表达式中「周」是下标 5，下标 4 是「月」；早期实现误把月当周，
        // 会把 '?' 写进月位并触发 "'?' can only be specified for Day-of-Month or Day-of-Week"。
        String dom = q[3];
        String dow = q[5];
        if ("*".equals(dow) || "?".equals(dow)) {
            if ("?".equals(dom)) {
                q[3] = "*";   // 避免日/周同时为 '?'
            }
            q[5] = "?";
        } else if ("*".equals(dom)) {
            q[3] = "?";
        }
        // dom 与 dow 同为具体值时保持原样，由 Quartz / JobValidator 明确拒绝。
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < q.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(q[i]);
        }
        return sb.toString();
    }

    /** 解析并返回 Quartz 原生表达式对象；非法时抛 {@link ParseException}。 */
    public static CronExpression parse(String cron) throws ParseException {
        return new CronExpression(normalize(cron));
    }

    /** 表达式是否可被 Quartz 接受。 */
    public static boolean isValid(String cron) {
        try {
            parse(cron);
            return true;
        } catch (RuntimeException | ParseException e) {
            return false;
        }
    }

    /** 计算指定时刻之后的下一次触发时间；无法计算返回 {@code null}。 */
    public static Instant nextAfter(String cron, Instant now, ZoneId zone) {
        if (cron == null || cron.isBlank() || now == null) {
            return null;
        }
        try {
            CronExpression expr = parse(cron);
            expr.setTimeZone(TimeZone.getTimeZone(zone == null ? ZoneId.systemDefault() : zone));
            Date next = expr.getNextValidTimeAfter(Date.from(now));
            return next == null ? null : next.toInstant();
        } catch (RuntimeException | ParseException e) {
            return null;
        }
    }
}
