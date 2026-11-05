package com.acme.scaffold.common.util;

import java.util.function.Supplier;

/**
 * 字段合并工具：在「部分更新」场景中，将未传入的字段回落为已有实体的当前值。
 *
 * <p>典型用法：{@code dto.setXxx(MergeFields.orKeep(req::xxx, existing::getXxx));}
 * 等价于内联三元 {@code req.xxx() != null ? req.xxx() : existing.getXxx()}，
 * 但消除了每个更新方法内重复书写的样板。</p>
 */
public final class MergeFields {

    private MergeFields() {
    }

    /**
     * 合并或保留：{@code incoming} 非 null 时取其值，否则保留 {@code existing} 的当前值。
     *
     * <p>采用短路求值——仅当 {@code incoming} 为 null 时才调用 {@code existing} 供应方，
     * 与原三元 {@code incoming != null ? incoming : existing} 行为逐字等价
     * （即使 {@code existing} 供应方可能为 null 亦不会在其非必要时被触发）。</p>
     *
     * @param incoming 入参字段供应方（如 {@code req::name}）
     * @param existing 已有实体字段供应方（如 {@code existing::getName}）
     * @param <T>      字段类型
     * @return 合并后的字段值
     */
    public static <T> T orKeep(Supplier<T> incoming, Supplier<T> existing) {
        T v = incoming.get();
        return v != null ? v : existing.get();
    }
}
