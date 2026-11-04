package com.acme.scaffold.common.validation;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.util.Objects;

/**
 * 领域级入参校验器（F1 修复基础组件）。
 *
 * <p>提供零依赖、可单测的通用校验原语，作为各 Service 入口字段级校验的统一范式，
 * 替代散落各处的「非空即写」或 {@code IllegalArgumentException} 式隐式校验，消除漏校验面。</p>
 *
 * <p>设计约定：校验失败统一抛 {@link BusinessException#VALIDATION_ERROR}（与全局异常处理器对齐），
 * 错误信息由调用方按业务语义给出，便于前端精准提示。</p>
 */
public final class DomainValidators {

    private DomainValidators() {
    }

    /**
     * 枚举/白名单成员校验：{@code value} 必须存在于 {@code allowed} 中，否则抛校验异常。
     *
     * @param value    待校验值（允许为 null，但 null 必然不在白名单内）
     * @param allowed  合法值集合
     * @param message  校验失败信息
     * @param <T>      值类型（常为枚举）
     * @return 通过校验的 {@code value}（便于链式书写）
     */
    public static <T> T requireIn(T value, T[] allowed, String message) {
        if (allowed != null) {
            for (T a : allowed) {
                if (Objects.equals(a, value)) {
                    return value;
                }
            }
        }
        throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * 枚举名白名单校验：{@code name} 必须等于 {@code allowed} 中某个枚举的 {@code name()}，
     * 否则抛校验异常。专用于「字符串 → 枚举」入口（如模板渠道名 → {@code Channel}），
     * 非法名显式抛 {@link CommonErrorCode#VALIDATION_ERROR}（语义优于 {@code Enum#valueOf} 的
     * {@code IllegalArgumentException}）。通过校验后再调用 {@code Enum#valueOf} 即必然成功。
     *
     * @param name     待校验的枚举名（允许为 null，必然不在白名单内）
     * @param allowed  合法枚举集合（通常为 {@code SomeEnum.values()}）
     * @param message  校验失败信息
     */
    public static void requireIn(String name, Enum<?>[] allowed, String message) {
        if (allowed != null && name != null) {
            for (Enum<?> a : allowed) {
                if (a.name().equals(name)) {
                    return;
                }
            }
        }
        throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
    }

    /**
     * long 数值范围校验（闭区间 [min, max]）。
     */
    public static long requireRange(long value, long min, long max, String message) {
        if (value < min || value > max) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    /**
     * int 数值范围校验（闭区间 [min, max]）。
     */
    public static int requireRange(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    /**
     * long 非负校验（≥ 0）。
     */
    public static long requireNonNegative(long value, String message) {
        if (value < 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    /**
     * int 非负校验（≥ 0）。
     */
    public static int requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }
}
