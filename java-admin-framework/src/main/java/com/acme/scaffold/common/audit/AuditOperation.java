package com.acme.scaffold.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。标注在应用服务或 Controller 方法上，切面将自动记录操作日志。
 * 注意：requestSummary/resultSummary 仅记录白名单字段并脱敏，禁止记录密码、Token、Cookie 等敏感信息。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {

    /** 模块编码，例如 system、workflow。 */
    String module();

    /** 操作类型，例如 CREATE、UPDATE、DELETE、APPROVE。 */
    String type();

    /** 操作名称，例如 创建用户。 */
    String name();

    /** 是否记录入参（默认脱敏后记录）。 */
    boolean recordParams() default true;

    /** 是否记录返回结果（默认不记录）。 */
    boolean recordResult() default false;
}
