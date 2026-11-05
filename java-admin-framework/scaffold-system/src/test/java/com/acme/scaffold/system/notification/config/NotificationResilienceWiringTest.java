package com.acme.scaffold.system.notification.config;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.resilience.CircuitBreaker;
import com.acme.scaffold.system.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知熔断器装配契约回归测试（纯 JVM，无需 MySQL / Spring Boot 全上下文）。
 *
 * <p>背景：P7 为 5 个外部渠道新增独立 {@link CircuitBreaker} Bean 后，容器中同类型 Bean 变为 6 个。
 * {@link NotificationService#dispatchAsync} 的 {@code dispatchBreaker} 原先是纯按类型
 * {@code @Autowired(required=false)} 注入，多候选时抛 {@link NoUniqueBeanDefinitionException}，
 * 导致<b>完整 Spring 上下文无法启动</b>。常规 2,062 例单元测试均为 mock 单测、不拉起真实上下文，
 * 且 {@code *IT} 本地默认跳过，因此该缺陷直到「千万级 L4 HTTP 压测」首次拉起全上下文才暴露。</p>
 *
 * <p>本测试用最小 {@link AnnotationConfigApplicationContext} 复现「6 个同类型 Bean」的真实装配场景，
 * 固化三层契约：① 总闸/分闸各自独立；② 按渠道映射绑定到正确的独立实例（防 {@code @Qualifier} 写错串线）；
 * ③ 与生产注入点同构的消费者必须靠 {@code @Qualifier} 成功装配，且反射断言生产字段的注解未被移除。</p>
 *
 * <p>刻意<b>不</b>依赖完整 Boot 上下文或数据库，因而可进常规 surefire 门禁，任何后续新增同类型 Bean
 * 或删除限定注解都会被立即拦截。</p>
 */
class NotificationResilienceWiringTest {

    private static final String[] BREAKER_NAMES = {
            "notificationDispatchBreaker",
            "smsDispatchBreaker", "emailDispatchBreaker", "webhookDispatchBreaker",
            "wecomDispatchBreaker", "dingtalkDispatchBreaker"
    };

    /** 只加载生产配置类，构造真实的「6 个同类型 Bean」容器。 */
    private static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(NotificationResilienceConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void sixBreakersAreRegisteredAndIndependent() {
        try (AnnotationConfigApplicationContext ctx = context()) {
            assertEquals(6, ctx.getBeansOfType(CircuitBreaker.class).size(),
                    "总闸 + 5 个外部分闸应共 6 个 CircuitBreaker Bean");
            for (String name : BREAKER_NAMES) {
                assertNotNull(ctx.getBean(name, CircuitBreaker.class), "缺少 Bean: " + name);
            }
            // 任一实例都不得与其他实例共享，否则「单一下游故障隔离」语义失效
            for (String a : BREAKER_NAMES) {
                for (String b : BREAKER_NAMES) {
                    if (!a.equals(b)) {
                        assertTrue(ctx.getBean(a) != ctx.getBean(b),
                                a + " 与 " + b + " 必须是独立实例");
                    }
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void externalChannelBreakersMapBindsEachChannelToItsOwnBreaker() {
        try (AnnotationConfigApplicationContext ctx = context()) {
            Map<Channel, CircuitBreaker> map =
                    (Map<Channel, CircuitBreaker>) ctx.getBean("externalChannelBreakers");
            assertEquals(Set.of(Channel.SMS, Channel.EMAIL, Channel.WEBHOOK,
                            Channel.WECOM, Channel.DINGTALK), map.keySet(),
                    "映射应恰好覆盖 5 个外部渠道，进程内通道不在其列");

            Map<Channel, String> expected = Map.of(
                    Channel.SMS, "smsDispatchBreaker",
                    Channel.EMAIL, "emailDispatchBreaker",
                    Channel.WEBHOOK, "webhookDispatchBreaker",
                    Channel.WECOM, "wecomDispatchBreaker",
                    Channel.DINGTALK, "dingtalkDispatchBreaker");
            expected.forEach((channel, beanName) -> assertSame(
                    ctx.getBean(beanName), map.get(channel),
                    channel + " 必须绑定到 " + beanName + "，防止 @Qualifier 串线"));
        }
    }

    @Test
    void productionDispatchBreakerFieldIsQualified() throws Exception {
        Field field = NotificationService.class.getDeclaredField("dispatchBreaker");
        Qualifier qualifier = field.getAnnotation(Qualifier.class);
        assertNotNull(qualifier,
                "dispatchBreaker 在 6 个同类型 Bean 下必须带 @Qualifier，否则完整上下文启动失败");
        assertEquals("notificationDispatchBreaker", qualifier.value(),
                "总闸注入点必须限定为异步派发总闸，而非任一渠道分闸");
    }

    @Test
    void qualifiedConsumerResolvesToMasterBreakerDespiteSixCandidates() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(NotificationResilienceConfig.class, QualifiedConsumerConfig.class);
            ctx.refresh();
            QualifiedConsumer consumer = ctx.getBean(QualifiedConsumer.class);
            assertNotNull(consumer.dispatchBreaker, "限定注入必须成功，不得为 null");
            assertSame(ctx.getBean("notificationDispatchBreaker"), consumer.dispatchBreaker,
                    "生产式注入点必须拿到总闸实例");
            assertEquals(5, consumer.externalChannelBreakers.size());
        }
    }

    @Test
    void unqualifiedInjectionFailsWhichIsWhyQualifierIsMandatory() {
        // 负向契约：证明「按类型注入」在 6 候选下确实失败，解释 @Qualifier 为何是必需而非可选。
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(NotificationResilienceConfig.class, UnqualifiedConsumerConfig.class);
            Exception ex = assertThrows(Exception.class, ctx::refresh,
                    "无 @Qualifier 的按类型注入在 6 个候选下应失败");
            assertTrue(rootCauseChainContains(ex, NoUniqueBeanDefinitionException.class),
                    "根因应为 NoUniqueBeanDefinitionException，实际: " + ex);
        }
    }

    private static boolean rootCauseChainContains(Throwable t, Class<?> type) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (type.isInstance(cur)) {
                return true;
            }
        }
        return false;
    }

    /** 与 NotificationService 的注入声明同构的消费者，验证生产式装配在最小上下文中可成立。 */
    static class QualifiedConsumer {
        @Autowired(required = false)
        @Qualifier("notificationDispatchBreaker")
        CircuitBreaker dispatchBreaker;

        @Autowired(required = false)
        Map<Channel, CircuitBreaker> externalChannelBreakers;
    }

    @Configuration
    static class QualifiedConsumerConfig {
        @Bean
        QualifiedConsumer qualifiedConsumer() {
            return new QualifiedConsumer();
        }
    }

    /** 反面样本：不带 @Qualifier 的按类型注入。 */
    static class UnqualifiedConsumer {
        @Autowired(required = false)
        CircuitBreaker dispatchBreaker;
    }

    @Configuration
    static class UnqualifiedConsumerConfig {
        @Bean
        UnqualifiedConsumer unqualifiedConsumer() {
            return new UnqualifiedConsumer();
        }
    }
}
