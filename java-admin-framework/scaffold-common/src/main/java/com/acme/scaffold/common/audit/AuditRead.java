package com.acme.scaffold.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 读操作审计注解（M20 增强）。标注在敏感「读」接口（如导出、PII 详情查看）上，
 * 由 {@link AuditAspect} 的读审计切面统一记录——前提是全局开关 {@code app.audit.audit-reads=true} 且通过采样。
 *
 * <p>与 {@link AuditOperation}（写操作审计）对称但更克制：
 * <ul>
 *   <li>默认 {@code recordParams=false}（读操作入参通常无敏感写意图，但可能带批量 ID，故不默认落库）；</li>
 *   <li>读审计受全局开关与采样率双重控制，默认完全关闭，避免读流量放大审计量；</li>
 *   <li>仅当「需要留痕谁读取了敏感数据」时，才在对应端点显式标注本注解（opt-in）。</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditRead {

    /** 模块编码，例如 audit、system。 */
    String module();

    /** 操作类型，读审计统一建议用 READ。 */
    String type() default "READ";

    /** 操作名称，例如 查询审计日志列表。 */
    String name();

    /** 是否记录入参（默认不记录，避免读流量放大）。 */
    boolean recordParams() default false;

    /** 是否记录返回结果（默认不记录）。 */
    boolean recordResult() default false;
}
