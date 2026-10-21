package com.acme.scaffold.codegen.config;

import com.acme.scaffold.codegen.controller.CodeGenController;
import com.acme.scaffold.codegen.service.CodeGenService;
import com.acme.scaffold.codegen.service.TableSchemaService;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CodeGenProperties 注册与装配回归：曾遗漏 @EnableConfigurationProperties，
 * 导致 CodeGenService/CodeGenController 构造时找不到 CodeGenProperties bean，启动即失败。
 * 使用 ApplicationContextRunner（无 DB / 无 Web 容器）离线验证。
 */
class CodeGenConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CodeGenConfig.class)
            .withBean("dslContext", org.jooq.DSLContext.class, () -> DSL.using(SQLDialect.MYSQL))
            .withBean(TableSchemaService.class)
            .withBean(CodeGenService.class)
            .withBean(CodeGenController.class);

    @Test
    void propertiesBeanRegisteredAndBound() {
        runner.withPropertyValues(
                        "codegen.basePackage=com.acme.scaffold.demo",
                        "codegen.author=tester",
                        "codegen.webPath=web/src")
                .run(context -> {
                    CodeGenProperties props = context.getBean(CodeGenProperties.class);
                    assertEquals("com.acme.scaffold.demo", props.getBasePackage());
                    assertEquals("tester", props.getAuthor());
                    assertEquals("web/src", props.getWebPath());
                });
    }

    @Test
    void propertiesDefaultsWhenNotConfigured() {
        runner.run(context -> {
            CodeGenProperties props = context.getBean(CodeGenProperties.class);
            assertEquals("com.acme.scaffold", props.getBasePackage());
            assertEquals("scaffold", props.getAuthor());
            assertEquals("pc-admin-vue3-vite/src", props.getWebPath());
        });
    }

    @Test
    void serviceAndControllerWired() {
        // CodeGenService / CodeGenController 的构造依赖 TableSchemaService 与 CodeGenProperties，
        // 任一 bean 缺失都会导致上下文启动失败，本用例即回归覆盖。
        runner.run(context -> {
            assertNotNull(context.getBean(TableSchemaService.class));
            assertNotNull(context.getBean(CodeGenService.class));
            assertNotNull(context.getBean(CodeGenController.class));
        });
    }
}
