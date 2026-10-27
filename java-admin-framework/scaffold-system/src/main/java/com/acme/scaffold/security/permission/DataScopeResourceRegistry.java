package com.acme.scaffold.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据权限资源注册表：启动时扫描全量 Bean 方法上的 {@link DataScope} 注解，
 * 收集「已接入数据过滤」的资源清单，供配置页动态渲染。
 *
 * <p><b>为什么自动扫描而不是硬编码清单</b>：此前前端 {@code SUPPORTED_RESOURCES} 把受支持的资源写死，
 * 新增一个带 {@code @DataScope} 的查询方法后，配置页不会自动出现该资源，导致「配了却不生效」的错觉。
 * 改为扫描后，凡是标注了 {@code @DataScope} 的方法都会被自动纳入配置范围，新增受控资源零前端改动。
 *
 * <p>实现用 {@link BeanPostProcessor}：每个 Bean 初始化后扫描其（真实）类上的方法注解，
 * 写入有序 Map（code -&gt; 展示名）。扫描发生在 Spring 上下文刷新阶段，请求期只读，无并发写入。
 */
@Slf4j
@Component
public class DataScopeResourceRegistry implements BeanPostProcessor {

    private final Map<String, String> resources = new LinkedHashMap<>();

    @Override
    @NonNull
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName)
            throws BeansException {
        registerFromClass(ClassUtils.getUserClass(bean));
        return bean;
    }

    /**
     * 扫描给定类（含父类、接口）的全部方法，提取 {@link DataScope} 注解并登记。
     * 抽离为独立方法以便离线单测（见 {@code DataScopeControllerTest}）。
     *
     * @param clazz 目标类（传 Bean 的真实用户类，规避 AOP 代理干扰）
     */
    public void registerFromClass(Class<?> clazz) {
        ReflectionUtils.doWithMethods(clazz,
                method -> {
                    DataScope ann = AnnotationUtils.findAnnotation(method, DataScope.class);
                    if (ann != null) {
                        String code = ann.resourceCode();
                        if (code == null || code.isBlank()) {
                            return;
                        }
                        String name = (ann.name() == null || ann.name().isBlank()) ? code : ann.name();
                        resources.putIfAbsent(code, name);
                    }
                },
                method -> AnnotationUtils.findAnnotation(method, DataScope.class) != null);
    }

    /** 返回已发现的数据权限资源清单（按首次发现顺序）。 */
    public List<DataScopeResourceView> listResources() {
        List<DataScopeResourceView> views = new ArrayList<>(resources.size());
        for (Map.Entry<String, String> entry : resources.entrySet()) {
            views.add(new DataScopeResourceView(entry.getKey(), entry.getValue()));
        }
        return views;
    }
}
