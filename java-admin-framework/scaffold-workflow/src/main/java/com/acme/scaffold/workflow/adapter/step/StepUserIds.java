package com.acme.scaffold.workflow.adapter.step;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流步骤委托共享的变量解析工具。
 *
 * <p>从 {@code CcStepDelegate} / {@code GenericStepDelegate} 提取的公共逻辑：
 * 两个委托此前各自维护一份逐字节相同的 {@code parseUserIds} 拷贝，任何编解码规则
 * 演进（如新增 Number 分支）都需要同步改两处，已有多处漂移风险。
 */
public final class StepUserIds {

    private StepUserIds() {
    }

    /**
     * 将流程变量中的接收人对象解析为 userId 列表。
     *
     * <p>兼容三种元素形态：{@code Long}（Flowable 直接传入）、数字字符串
     * （JSON 反序列化产物，非数字静默跳过）、其他 {@code Number}（如 Integer）。
     *
     * @param raw 流程变量值，null 或非 List 时返回空列表
     * @return 保序的 userId 列表（不含 null）
     */
    @SuppressWarnings("unchecked")
    public static List<Long> parseUserIds(Object raw) {
        List<Long> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Long l) {
                    result.add(l);
                } else if (o instanceof String s && !s.isBlank()) {
                    try {
                        result.add(Long.parseLong(s));
                    } catch (NumberFormatException ignored) {
                        // 忽略非数字元素
                    }
                } else if (o instanceof Number n) {
                    result.add(n.longValue());
                }
            }
        }
        return result;
    }
}
