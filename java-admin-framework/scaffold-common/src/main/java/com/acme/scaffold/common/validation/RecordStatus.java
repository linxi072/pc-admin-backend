package com.acme.scaffold.common.validation;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.util.Locale;
import java.util.Set;

/**
 * 记录型实体状态枚举与归一器（F1 全量铺开基础组件）。
 *
 * <p>统一管理「状态字段」的合法值域与归一口径，替代散落在各 Service 的
 * 「非空即原样写库」隐性旁路，杜绝非法状态（如 {@code "active"} 小写抖动、
 * {@code "FOO"} 越界值）原样落库的脏数据入口。</p>
 *
 * <p>归一规则（{@link #normalize}）：
 * <ul>
 *     <li>null / 空白 → {@link #DEFAULT}（回落默认 {@code "ACTIVE"}，与原 create 流程
 *     {@code status == null ? "ACTIVE" : status} 语义完全一致）；</li>
 *     <li>否则 trim + 大写（大小写不敏感）后做白名单校验，通过则返回规范大写名，
 *     非法值抛 {@link CommonErrorCode#VALIDATION_ERROR}。</li>
 * </ul>
 * </p>
 *
 * <p>合法值域取自全仓记录型实体的既有状态字面量并集
 * （{@code Sys*DO} 默认 {@code ACTIVE}、字典导入任务用 {@code ACTIVE/INACTIVE}、
 * 用户禁用事件用 {@code ACTIVE/LOCKED} 等），对全部既有合法值严格 behavior-preserving，
 * 仅拒绝垃圾值。</p>
 */
public enum RecordStatus {

    ACTIVE,
    DISABLED,
    INACTIVE,
    LOCKED;

    /** 合法状态名白名单（规范大写）。 */
    public static final Set<String> NAMES = Set.of("ACTIVE", "DISABLED", "INACTIVE", "LOCKED");

    /** 默认状态（create 流程未指定状态时回落此值）。 */
    public static final String DEFAULT = "ACTIVE";

    /**
     * 归一状态字符串：null / 空白回落 {@link #DEFAULT}，否则 trim + 大写后做白名单校验。
     *
     * @param raw 原始状态（允许 null，按默认回落）
     * @return 规范大写状态名（必为 {@link #NAMES} 之一）
     * @throws BusinessException 当 raw 非 null 且非空白，但不属于合法白名单时
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        DomainValidators.requireIn(normalized, RecordStatus.values(),
                "记录状态非法（须为 ACTIVE/DISABLED/INACTIVE/LOCKED）");
        return normalized;
    }

    /**
     * 合并状态：update 场景中「入参状态为 null 则保留原值，否则按白名单归一」。
     *
     * <p>等价于 {@code incoming == null ? existing : normalize(incoming)}，仅作样板收敛。
     * 注意：与 {@link #normalize} 不同，本方法对 null 入参回落 {@code existing}（保留原值），
     * 而非回落 {@link #DEFAULT}——这正是 update「未传状态即不动」语义所需要的。</p>
     *
     * @param incoming 入参状态（允许 null，此时保留 existing）
     * @param existing 既有状态（回落值）
     * @return 规范大写状态名，或 incoming 为 null 时的 existing
     */
    public static String mergeOrKeep(String incoming, String existing) {
        return incoming == null ? existing : normalize(incoming);
    }
}
