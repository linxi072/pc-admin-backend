package com.acme.scaffold.ai.port;

import com.acme.scaffold.ai.AiScaffoldException;
import com.acme.scaffold.ai.dto.TableSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 条件化可选云大模型提供者：仅当 {@code ai.llm.enabled=true} 时装配。
 *
 * <p>默认离线构建不激活（避免网络依赖破坏 {@code mvn -o}）。接入真实大模型时，在此实现
 * 调用 {@code ai.llm.endpoint} 并解析返回为 {@link TableSpec}；当前为占位接线，未配置端点时
 * 抛出明确异常，提示开发者补齐云接入。编排层在可用时优选本提供者，否则回退启发式。</p>
 */
@Component
@ConditionalOnProperty(prefix = "ai.llm", name = "enabled", havingValue = "true")
public class CloudLlmProvider implements LlmProvider {

    @Value("${ai.llm.endpoint:}")
    private String endpoint;

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public String name() {
        return "cloud";
    }

    @Override
    public TableSpec parse(String prompt) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new AiScaffoldException("云大模型未配置 ai.llm.endpoint，无法生成表结构");
        }
        // TODO 真实大模型调用：POST endpoint，解析 JSON 为 TableSpec。
        throw new AiScaffoldException("云大模型接入尚未实现（占位）：请基于 endpoint=" + endpoint + " 补齐调用");
    }
}
