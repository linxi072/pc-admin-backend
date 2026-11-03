package com.acme.scaffold.common.plugin;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 Spring Bean 为「扩展点实现」（插件贡献）。
 *
 * <p>带 {@link Extension} 的 Bean 在启动期被 {@link ExtensionRegistry} 自动收集；
 * 也可由 {@code scaffold-plugin} 的 {@code PluginManager} 在运行时从外部 jar 加载并注册。
 * 该注解元标注为 {@link Component}，因此进程内（组件扫描路径下）的扩展 Bean 会被 Spring
 * 自动实例化并被注册表发现；外部 jar 中的扩展类则不走组件扫描，而由 {@code PluginManager} 显式加载。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Extension {

    /** 扩展点实现标识（默认取 Bean 名）。 */
    String name() default "";

    /** 版本。 */
    String version() default "1.0.0";

    /** 描述。 */
    String description() default "";

    /** 排序，越小越靠前。 */
    int order() default 0;
}
