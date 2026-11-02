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
 * 字段级数据权限资源注册表：启动时扫描全量 Bean 方法上的 {@link ColumnPermission} 注解，
 * 归集「已声明字段级受控」的资源清单（含受控字段），供配置页动态渲染与运行时脱敏复用。
 *
 * <p><b>为何自动扫描而非硬编码清单</b>：与 {@link DataScopeResourceRegistry} 同思路——
 * 新增一个标注了 {@code @ColumnPermission} 的查询方法后，配置页与脱敏逻辑自动纳入该资源，
 * 新增受控资源零前端/零手动登记改动。</p>
 *
 * <p>同一 {@code resourceCode} 可能出现在多个方法上（如列表与详情），本注册表按字段名去重合并，
 * 保留首次出现的策略/敏感类型，避免重复条目。</p>
 *
 * <p>实现用 {@link BeanPostProcessor}：每个 Bean 初始化后扫描其（真实）类上的方法注解，
 * 写入有序 Map；扫描发生在 Spring 上下文刷新阶段，请求期只读，无并发写入。</p>
 */
@Slf4j
@Component
public class ColumnPermissionResourceRegistry implements BeanPostProcessor {

    /** resourceCode -> (resourceName, ordered fieldName -> fieldView)。 */
    private final Map<String, ResourceEntry> resources = new LinkedHashMap<>();

    private static final class ResourceEntry {
        String resourceName;
        final Map<String, ColumnPermissionFieldView> fields = new LinkedHashMap<>();
    }

    @Override
    @NonNull
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName)
            throws BeansException {
        registerFromClass(ClassUtils.getUserClass(bean));
        return bean;
    }

    /**
     * 扫描给定类（含父类、接口）的全部方法，提取 {@link ColumnPermission} 注解并登记。
     * 抽离为独立方法以便离线单测（见 {@code ColumnPermissionControllerTest}）。
     *
     * @param clazz 目标类（传 Bean 的真实用户类，规避 AOP 代理干扰）
     */
    public void registerFromClass(Class<?> clazz) {
        ReflectionUtils.doWithMethods(clazz,
                method -> {
                    ColumnPermission ann = AnnotationUtils.findAnnotation(method, ColumnPermission.class);
                    if (ann != null) {
                        register(ann);
                    }
                },
                method -> AnnotationUtils.findAnnotation(method, ColumnPermission.class) != null);
    }

    /** 登记一条 {@link ColumnPermission} 声明（resourceCode 去重、字段按名合并）。 */
    public void register(ColumnPermission ann) {
        String code = ann.resourceCode();
        if (code == null || code.isBlank()) {
            return;
        }
        ResourceEntry entry = resources.computeIfAbsent(code, k -> new ResourceEntry());
        if (entry.resourceName == null || entry.resourceName.isBlank()) {
            entry.resourceName = (ann.name() == null || ann.name().isBlank()) ? code : ann.name();
        }
        for (ColumnField f : ann.fields()) {
            if (f == null || f.name() == null || f.name().isBlank()) {
                continue;
            }
            entry.fields.putIfAbsent(f.name(),
                    new ColumnPermissionFieldView(f.name(),
                            (f.label() == null || f.label().isBlank()) ? f.name() : f.label(),
                            f.strategy() == null ? ColumnStrategy.MASK : f.strategy(),
                            f.sensitiveType()));
        }
    }

    /** 返回已发现的字段级权限资源清单（按首次发现顺序）。 */
    public List<ColumnPermissionResourceView> listResources() {
        List<ColumnPermissionResourceView> views = new ArrayList<>(resources.size());
        for (Map.Entry<String, ResourceEntry> e : resources.entrySet()) {
            views.add(new ColumnPermissionResourceView(e.getKey(), e.getValue().resourceName,
                    new ArrayList<>(e.getValue().fields.values())));
        }
        return views;
    }

    /** 返回某资源受控字段清单；未登记返回空列表。 */
    public List<ColumnPermissionFieldView> fieldsOf(String resourceCode) {
        ResourceEntry entry = resources.get(resourceCode);
        if (entry == null) {
            return List.of();
        }
        return new ArrayList<>(entry.fields.values());
    }

    /** 是否已登记某资源。 */
    public boolean contains(String resourceCode) {
        return resources.containsKey(resourceCode);
    }
}
