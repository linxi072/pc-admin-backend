package com.acme.scaffold;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Java 后端基础管理框架脚手架启动类。
 * 模块化单体：单进程部署，认证/系统/工作流/调度/审计/监控按包边界独立演进。
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class FrameworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrameworkApplication.class, args);
    }
}
