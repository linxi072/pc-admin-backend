package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.sensitive.SensitiveType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * 字段级数据权限执行器：按当前主体对各受控字段的列权限，对目标对象的敏感字段做脱敏/隐藏。
 *
 * <p><b>使用方式</b>：Service 在返回含敏感字段的（可变）DTO/实体前调用，例如
 * {@code applier.apply(userDto, "system:user", code -> permissions.contains(code))}。
 * 无权限的字段按策略处理：{@code MASK} 经 {@link SensitiveMasker} 脱敏（未指定敏感类型回落通用掩码），
 * {@code HIDE} 置空（null）。</p>
 *
 * <p><b>可变对象</b>：用反射就地读写字段脱敏/隐藏。<b>不可变 {@code record}</b>：本执行器不会就地改写，
 * 而是按受控字段清单经其规范构造器<b>重建一个脱敏副本</b>并返回（原 record 不变），调用方须使用返回值；
 * 重建失败（反射异常）时记 WARN 并回落原对象，不抛异常、不影响其它字段。</p>
 * <p>集合入参会逐元素处理（批次端点友好）。record 元素同样走重建副本路径。</p>
 *
 * <p><b>失败安全</b>：字段不存在/不可访问/类型为 record 不可写时仅跳过该字段，不抛异常、不影响其它字段。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColumnPermissionApplier {

    private final ColumnPermissionResourceRegistry registry;

    /** 便捷入口：从注册表按 resourceCode 取受控字段后执行脱敏/隐藏。 */
    public <T> T apply(T target, String resourceCode, Predicate<String> hasColumnPermission) {
        if (target == null) {
            return null;
        }
        List<ColumnPermissionFieldView> fields = registry.fieldsOf(resourceCode);
        if (fields.isEmpty()) {
            return target;
        }
        return apply(target, resourceCode, fields, hasColumnPermission);
    }

    /** 便捷入口（集合），逐元素执行脱敏/隐藏。 */
    public <T> List<T> applyAll(Collection<T> targets, String resourceCode, Predicate<String> hasColumnPermission) {
        if (targets == null) {
            return List.of();
        }
        List<ColumnPermissionFieldView> fields = registry.fieldsOf(resourceCode);
        return applyAll(targets, resourceCode, fields, hasColumnPermission);
    }

    /** 核心批量执行：显式传入受控字段清单，逐元素脱敏/隐藏。 */
    public <T> List<T> applyAll(Collection<T> targets, String resourceCode, List<ColumnPermissionFieldView> fields,
                                 Predicate<String> hasColumnPermission) {
        if (targets == null) {
            return List.of();
        }
        List<T> out = new ArrayList<>(targets.size());
        for (T t : targets) {
            out.add(apply(t, resourceCode, fields, hasColumnPermission));
        }
        return out;
    }

    /**
     * 核心执行：对 target 的每个受控字段，若当前主体不具有对应列权限码则按策略脱敏/隐藏。
     *
     * @param target              目标对象（可变 DTO/实体）
     * @param resourceCode        资源编码（与 @ColumnPermission 一致）
     * @param fields              受控字段清单（已解析的视图）
     * @param hasColumnPermission 列权限判定（传入某字段权限码，返回当前主体是否拥有明文可见权）
     */
    public <T> T apply(T target, String resourceCode, List<ColumnPermissionFieldView> fields,
                       Predicate<String> hasColumnPermission) {
        if (target == null || fields == null || fields.isEmpty()) {
            return target;
        }
        // record 不可变：经规范构造器重建脱敏副本（原对象不变），调用方须使用返回值。
        if (target.getClass().isRecord()) {
            return applyRecord(target, resourceCode, fields, hasColumnPermission);
        }
        for (ColumnPermissionFieldView f : fields) {
            String code = ColumnPermissionRules.columnPermissionCode(resourceCode, f.field());
            if (hasColumnPermission != null && hasColumnPermission.test(code)) {
                continue; // 当前主体拥有该列权限，保持明文
            }
            maskField(target, f);
        }
        return target;
    }

    /**
     * record 重建路径：经规范构造器重建一个脱敏副本（原 record 不可变、不被改写）。
     * 逐组件读取当前值，对受控且无权限的字段按策略脱敏/隐藏，其余原样传回构造器。
     */
    @SuppressWarnings("unchecked")
    private <T> T applyRecord(T target, String resourceCode, List<ColumnPermissionFieldView> fields,
                             Predicate<String> hasColumnPermission) {
        Class<?> clazz = target.getClass();
        RecordComponent[] components = clazz.getRecordComponents();
        if (components.length == 0) {
            return target;
        }
        Map<String, ColumnPermissionFieldView> byName = new LinkedHashMap<>();
        for (ColumnPermissionFieldView f : fields) {
            byName.put(f.field(), f);
        }
        Object[] args = new Object[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                RecordComponent rc = components[i];
                Object value = rc.getAccessor().invoke(target);
                ColumnPermissionFieldView f = byName.get(rc.getName());
                if (f != null) {
                    String code = ColumnPermissionRules.columnPermissionCode(resourceCode, f.field());
                    if (hasColumnPermission == null || !hasColumnPermission.test(code)) {
                        value = switch (f.strategy()) {
                            case HIDE -> null;
                            case MASK -> maskValue(value, f.sensitiveType());
                            default -> value;
                        };
                    }
                }
                args[i] = value;
            }
            Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return (T) ctor.newInstance(args);
        } catch (Exception e) {
            log.warn("字段级权限：record 重建失败（{}），回落原对象不脱敏: {}", clazz.getName(), e.getMessage());
            return target;
        }
    }

    private void maskField(Object target, ColumnPermissionFieldView f) {
        Field field = ReflectionUtils.findField(target.getClass(), f.field());
        if (field == null) {
            log.debug("字段级权限：目标类型 {} 不存在字段 {}，跳过", target.getClass().getName(), f.field());
            return;
        }
        ReflectionUtils.makeAccessible(field);
        Object value;
        try {
            value = ReflectionUtils.getField(field, target);
        } catch (Exception e) {
            log.warn("字段级权限：读取字段 {} 失败，跳过: {}", f.field(), e.getMessage());
            return;
        }
        if (value == null) {
            return;
        }
        Object masked = switch (f.strategy()) {
            case HIDE -> null;
            case MASK -> maskValue(value, f.sensitiveType());
            default -> value;
        };
        try {
            ReflectionUtils.setField(field, target, masked);
        } catch (Exception e) {
            log.warn("字段级权限：写入字段 {} 失败（不可变类型？），跳过: {}", f.field(), e.getMessage());
        }
    }

    private Object maskValue(Object value, SensitiveType type) {
        if (!(value instanceof String s)) {
            // 非字符串（数值/枚举等）无法按字符脱敏，统一置空（隐藏）。
            return null;
        }
        if (type != null && type != SensitiveType.NONE) {
            return SensitiveMasker.mask(s, type);
        }
        return genericMask(s);
    }

    /** 通用掩码：保留首 3 末 4，中间以 {@code *} 替换；短于 8 时保留首字符余下掩去。 */
    static String genericMask(String value) {
        if (value.length() <= 1) {
            return value;
        }
        if (value.length() <= 7) {
            return value.charAt(0) + "*".repeat(value.length() - 1);
        }
        int head = 3;
        int tail = 4;
        return value.substring(0, head) + "*".repeat(value.length() - head - tail) + value.substring(value.length() - tail);
    }
}
