package com.acme.scaffold.config.datasource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级数据源切换注解（声明式路由）。
 *
 * <p>标注后，方法执行期间路由到指定数据源；value 为空串表示主库。
 * 切面（{@link DataSourceSwitchAspect}）已置最高优先级，确保早于事务切面生效，
 * 从而 @Transactional 开启连接时已解析到目标数据源。
 *
 * <p>推荐用于「调用带 @Transactional 的服务方法」的外层编排方法；
 * 若需更细粒度或避免切面顺序顾虑，可直接使用 {@link DataSourceRouter#with(String, java.util.concurrent.Callable)}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithDataSource {

    /** 目标数据源名称；空串表示主数据源。 */
    String value() default "";
}
