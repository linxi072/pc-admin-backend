package com.acme.scaffold.config.datasource;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 编程式数据源路由服务（不依赖 AOP）。
 *
 * <p>在调用边界显式切换路由上下文，推荐用于跨库查询的业务编排层：
 * 在调用各 @Transactional 服务前设定目标库，规避注解切面与事务切面的顺序顾虑。
 *
 * <p>示例：
 * <pre>
 *   List&lt;Row&gt; rows = router.with("report", () -&gt; reportDsl.selectFrom(...).fetch());
 *   // 或直接使用具名 DSLContext
 *   DSLContext report = router.dsl("report");
 * </pre>
 */
@Service
public class DataSourceRouter {

    private final DataSourceRegistry registry;
    private final Map<String, DSLContext> dslContexts;

    public DataSourceRouter(DataSourceRegistry registry, Map<String, DSLContext> dslContexts) {
        this.registry = registry;
        this.dslContexts = dslContexts;
    }

    /** 获取指定名称数据源的 DSLContext（未配置则抛异常）。 */
    public DSLContext dsl(String name) {
        DSLContext ctx = dslContexts.get(name);
        if (ctx == null) {
            throw new IllegalArgumentException("未配置的数据源 DSLContext：" + name + "，已配置：" + dslContexts.keySet());
        }
        return ctx;
    }

    /** 获取主数据源 DSLContext。 */
    public DSLContext primaryDsl() {
        return dsl(registry.primaryName());
    }

    /** 在指定数据源上下文中执行有返回值任务，自动还原调用前路由状态。 */
    public <T> T with(String name, Callable<T> task) throws Exception {
        String previous = DataSourceContextHolder.get();
        try {
            DataSourceContextHolder.set(name);
            return task.call();
        } finally {
            restore(previous);
        }
    }

    /** 在指定数据源上下文中执行无返回值任务，自动还原调用前路由状态。 */
    public void with(String name, Runnable task) {
        String previous = DataSourceContextHolder.get();
        try {
            DataSourceContextHolder.set(name);
            task.run();
        } finally {
            restore(previous);
        }
    }

    private void restore(String previous) {
        if (previous == null) {
            DataSourceContextHolder.clear();
        } else {
            DataSourceContextHolder.set(previous);
        }
    }
}
