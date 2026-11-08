package com.acme.scaffold.ai.port;

import com.acme.scaffold.ai.dto.TableSpec;

/**
 * 大模型提供者端口：自然语言 → 表规格。
 *
 * <p>默认实现为离线启发式（{@link HeuristicLlmProvider}），确定性、零外部依赖、可过双门禁；
 * 真实大模型接入为条件化可选（{@link CloudLlmProvider}，需 ai.llm.enabled=true），
 * 由编排层在可用时优选，否则回退启发式。</p>
 */
public interface LlmProvider {

    /**
     * 解析自然语言提示词为表结构草案。
     *
     * @param prompt 自然语言描述（如「订单表含金额/状态」）
     * @return 表规格草案
     */
    TableSpec parse(String prompt);

    /**
     * 是否为默认（离线）提供者。云提供者返回 false，供编排层优选。
     */
    default boolean isDefault() {
        return false;
    }

    /**
     * 提供者标识（写入结果审计字段）。
     */
    String name();
}
