package com.acme.scaffold.mybatis.dsl;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

/**
 * 取数结果断言工具：收敛「查询后判空抛 {@code NOT_FOUND}」的样板。
 *
 * <p>刻意放在 {@link SqlWriters} 之外，使生产代码仍直接调用 {@code SqlWriters.fetchById(...)} 等
 * 具体取数方法（单测中以 {@code mockStatic(SqlWriters.class)} 替身时这些取数方法仍可被 stub），
 * 仅把「判空抛错」这一通用守卫抽到此处，避免污染被 mock 的类。</p>
 */
public final class FetchSupport {

    private FetchSupport() {
    }

    /**
     * 断言取数结果非空：为 {@code null} 时抛出 {@link CommonErrorCode#NOT_FOUND}。
     *
     * <p>典型用法：<pre>{@code
     *   XxxDO x = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, T, XxxDO.class, id), "xxx 不存在");
     * }</pre>
     * 等价于先取数再判空抛错，仅作样板收敛，错误码与消息由调用方传入。</p>
     */
    public static <E> E requirePresent(E entity, String notFoundMsg) {
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, notFoundMsg);
        }
        return entity;
    }
}
