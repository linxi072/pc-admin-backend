package com.acme.scaffold.job.domain;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jobrunr.scheduling.Schedule;
import org.jobrunr.scheduling.cron.CronExpression;

import java.time.Instant;
import java.time.ZoneId;

/**
 * 定时任务参数校验（纯函数，无 Spring / DB 依赖，便于离线单测）。
 *
 * <p>校验点：
 * <ul>
 *   <li>名称非空且不超长；taskKey 非空；触发类型非空；</li>
 *   <li><b>CRON</b>：表达式非空，且能通过 JobRunr 自身的
 *       {@link CronExpression#create(String)} 解析（支持 5 段分钟精度 / 6 段秒精度）；
 *       —— 复用调度引擎的解析器，保证「校验通过」即「可被调度」，杜绝格式分歧；</li>
 *   <li><b>SIMPLE</b>：间隔值 &gt; 0、单位非空，且折算后不短于 {@link Schedule#SMALLEST_SCHEDULE_IN_SECONDS} 秒；</li>
 *   <li><b>ONE_TIME</b>：执行时间必填且须晚于当前时间；</li>
 *   <li><b>params</b>：非空时须为合法 JSON。</li>
 * </ul>
 */
public final class JobValidator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int MAX_NAME_LEN = 128;
    private static final int MAX_TASK_KEY_LEN = 128;
    private static final int MAX_CRON_LEN = 128;
    private static final int MAX_PARAMS_LEN = 2048;

    private JobValidator() {
    }

    /**
     * 校验任务定义（创建与编辑共用）。
     *
     * @param now 当前时间（显式传入，便于单测构造确定性用例）
     */
    public static void validate(String jobName, String taskKey, JobTriggerType triggerType,
                                String cronExpression, Integer intervalValue, JobIntervalUnit intervalUnit,
                                Instant oneTimeAt, String paramsJson, Instant now) {
        if (jobName == null || jobName.isBlank()) {
            throw BusinessException.badRequest("任务名称不能为空");
        }
        if (jobName.length() > MAX_NAME_LEN) {
            throw BusinessException.badRequest("任务名称长度不能超过 " + MAX_NAME_LEN + " 个字符");
        }
        if (taskKey == null || taskKey.isBlank()) {
            throw BusinessException.badRequest("处理器（taskKey）不能为空");
        }
        if (taskKey.length() > MAX_TASK_KEY_LEN) {
            throw BusinessException.badRequest("处理器标识长度不能超过 " + MAX_TASK_KEY_LEN + " 个字符");
        }
        if (triggerType == null) {
            throw BusinessException.badRequest("触发类型不能为空");
        }
        switch (triggerType) {
            case CRON -> validateCron(cronExpression);
            case SIMPLE -> validateInterval(intervalValue, intervalUnit);
            case ONE_TIME -> validateOneTime(oneTimeAt, now);
        }
        validateParams(paramsJson);
    }

    /** 校验并解析 cron 表达式；非法时抛 {@code 400}。 */
    public static CronExpression parseCron(String cron) {
        validateCron(cron);
        return CronExpression.create(cron.trim());
    }

    private static void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            throw BusinessException.badRequest("请填写 cron 表达式");
        }
        if (cron.length() > MAX_CRON_LEN) {
            throw BusinessException.badRequest("cron 表达式长度不能超过 " + MAX_CRON_LEN + " 个字符");
        }
        try {
            CronExpression.create(cron.trim());
        } catch (RuntimeException e) {
            // InvalidCronExpressionException 等：转换为统一业务异常
            throw BusinessException.badRequest("cron 表达式非法：" + e.getMessage());
        }
    }

    private static void validateInterval(Integer intervalValue, JobIntervalUnit intervalUnit) {
        if (intervalValue == null || intervalValue <= 0) {
            throw BusinessException.badRequest("执行间隔必须为正整数");
        }
        if (intervalUnit == null) {
            throw BusinessException.badRequest("执行间隔单位不能为空");
        }
        long seconds;
        try {
            seconds = intervalUnit.toDuration(intervalValue).getSeconds();
        } catch (ArithmeticException e) {
            throw BusinessException.badRequest("执行间隔过大，请检查数值与单位");
        }
        if (seconds < Schedule.SMALLEST_SCHEDULE_IN_SECONDS) {
            throw BusinessException.badRequest("执行间隔不能小于 "
                    + Schedule.SMALLEST_SCHEDULE_IN_SECONDS + " 秒");
        }
    }

    private static void validateOneTime(Instant oneTimeAt, Instant now) {
        if (oneTimeAt == null) {
            throw BusinessException.badRequest("单次执行时间不能为空");
        }
        if (now != null && !oneTimeAt.isAfter(now)) {
            throw BusinessException.badRequest("单次执行时间必须晚于当前时间");
        }
    }

    private static void validateParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return;
        }
        if (paramsJson.length() > MAX_PARAMS_LEN) {
            throw BusinessException.badRequest("任务参数长度不能超过 " + MAX_PARAMS_LEN + " 个字符");
        }
        try {
            JSON.readTree(paramsJson);
        } catch (Exception e) {
            throw BusinessException.badRequest("任务参数必须是合法的 JSON 字符串");
        }
    }

    /** 供上层在需要时复用统一错误码。 */
    public static BusinessException invalid(String message) {
        return new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
    }

    /** 换算工具：本地时区下的 {@link Instant} → 便于断言与转换。 */
    static ZoneId zoneOrDefault(ZoneId zoneId) {
        return zoneId == null ? ZoneId.systemDefault() : zoneId;
    }
}
