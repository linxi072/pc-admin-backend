package com.acme.scaffold;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Java 后端基础管理框架脚手架启动类。
 * 模块化单体：单进程部署，认证/系统/工作流/调度/审计/监控按包边界独立演进。
 *
 * <p>排除 {@link QuartzAutoConfiguration}：本项目的 {@code job.config.QuartzConfig}
 * 自行装配 RAMJobStore 调度器并注册同名 {@code quartzScheduler} Bean。由于
 * {@code spring-context-support}（邮件通知依赖）使 Boot 的 Quartz 自动配置条件成立，
 * 不排除会与之发生 BeanDefinitionOverrideException，导致应用启动失败。
 * 自建调度不依赖 QRTZ_* 表 / DataSource / Spring 事务，无需 Boot 自动配置。
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication(exclude = QuartzAutoConfiguration.class)
public class FrameworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrameworkApplication.class, args);
    }
}
