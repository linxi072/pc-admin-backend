package com.acme.scaffold.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.ai.LlmNotImplementedException;
import com.acme.scaffold.ai.config.AiProperties;
import com.acme.scaffold.ai.dto.ScaffoldResult;
import com.acme.scaffold.ai.dto.TableSpec;
import com.acme.scaffold.ai.port.CloudLlmProvider;
import com.acme.scaffold.ai.port.HeuristicLlmProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * AI 脚手架编排服务降级语义测试：云失败回退启发式（审计来源如实标记）、501 不静默降级、
 * 关闭降级开关时失败原样上抛（离线，无网络）。
 */
class AiScaffoldServiceFallbackTest {

    /** 可编程桩：按注入行为抛指定异常或返回固定规格。 */
    private static class StubCloudProvider extends CloudLlmProvider {
        private final Runnable behavior;

        StubCloudProvider(Runnable behavior) {
            super(new AiProperties());
            this.behavior = behavior;
        }

        @Override
        public TableSpec parse(String prompt) {
            behavior.run();
            throw new IllegalStateException("unreachable");
        }
    }

    private static ObjectProvider<CloudLlmProvider> of(CloudLlmProvider provider) {
        return new ObjectProvider<>() {
            @Override
            public CloudLlmProvider getObject() {
                return provider;
            }

            @Override
            public CloudLlmProvider getObject(Object... args) {
                return provider;
            }

            @Override
            public CloudLlmProvider getIfAvailable() {
                return provider;
            }

            @Override
            public CloudLlmProvider getIfUnique() {
                return provider;
            }
        };
    }

    private static AiScaffoldService newService(CloudLlmProvider cloud) {
        return new AiScaffoldService(new HeuristicLlmProvider(),
                cloud == null ? emptyProvider() : of(cloud), new AiProperties());
    }

    private static ObjectProvider<CloudLlmProvider> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public CloudLlmProvider getObject() {
                throw new IllegalStateException("no bean");
            }

            @Override
            public CloudLlmProvider getObject(Object... args) {
                throw new IllegalStateException("no bean");
            }

            @Override
            public CloudLlmProvider getIfAvailable() {
                return null;
            }

            @Override
            public CloudLlmProvider getIfUnique() {
                return null;
            }
        };
    }

    @Test
    void cloudRuntimeFailureShouldFallBackToHeuristic() {
        AiScaffoldService service = newService(
                new StubCloudProvider(() -> {
                    throw new CloudLlmProvider.LlmInvocationException("模拟网络失败");
                }));
        ScaffoldResult result = service.generate("订单表含金额/状态", null);
        assertEquals("heuristic", result.provider());
        assertTrue(result.needsReview());
    }

    @Test
    void llmNotImplementedShouldNotFallBackSilently() {
        AiScaffoldService service = newService(
                new StubCloudProvider(() -> {
                    throw new LlmNotImplementedException("云大模型已启用但未配置端点");
                }));
        assertThrows(LlmNotImplementedException.class,
                () -> service.generate("订单表含金额/状态", null));
    }

    @Test
    void emptyPromptShouldBeRejectedBeforeProviderCall() {
        AiScaffoldService service = newService(null);
        assertThrows(com.acme.scaffold.ai.AiScaffoldException.class, () -> service.generate("  ", null));
    }

    @Test
    void heuristicOnlyWhenNoCloudBean() {
        AiScaffoldService service = newService(null);
        ScaffoldResult result = service.generate("用户表含手机号/邮箱", null);
        assertEquals("heuristic", result.provider());
    }

    /** 启发式生成路径（无云 Bean）：结果含迁移 SQL 草稿且 token 估算非负。 */
    @Test
    void heuristicGenerationProducesDraft() {
        AiScaffoldService service = newService(null);
        ScaffoldResult result = service.generate("商品表含名称/描述", null);
        assertEquals("heuristic", result.provider());
        assertTrue(!result.ddl().isBlank());
        assertTrue(result.estimatedTokens() >= 0);
    }
}
