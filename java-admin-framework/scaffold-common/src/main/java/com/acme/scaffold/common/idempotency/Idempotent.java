package com.acme.scaffold.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等写接口标注：同一次业务操作重复提交时，只产生一次效果。
 *
 * <p><b>使用方式</b>：客户端在请求头带 {@code Idempotency-Key: <唯一键>}（建议 UUID）。
 * 服务端按 {@code (tenant_id, key, scope)} 去重：
 * 首次执行并缓存结果；重复请求命中已成功记录时<b>重放首次响应</b>；
 * 命中处理中记录时返回 409；命中失败记录时允许重试。
 *
 * <p><b>适用边界</b>：仅用于「写且不可重复」的操作（审批、转办、群发、下单、支付回调）。
 * 查询接口不要加——会白白占用一行记录并拖慢响应。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 作用域；为空时取切点签名，避免不同接口共用同一 key 互相干扰。 */
    String scope() default "";

    /** 记录保留时长（秒），超时由清理任务删除。默认 24 小时。 */
    int ttlSeconds() default 24 * 60 * 60;

    /**
     * 是否强制要求请求头。审批、转办等「重复一次就是事故」的接口设为 true；
     * 内部也可能被复用的服务方法设为 false（无 key 时直接放行，保持原语义）。
     */
    boolean requireKey() default false;

    /** requireKey=true 且请求头缺失时的提示语。 */
    String message() default "缺少 Idempotency-Key 请求头，请勿重复提交";
}
