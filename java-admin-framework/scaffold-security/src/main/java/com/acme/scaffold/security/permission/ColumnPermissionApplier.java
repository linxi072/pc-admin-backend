package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.sensitive.SensitiveType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 字段级数据权限执行器：按当前主体对各受控字段的列权限，对目标对象的敏感字段做脱敏/隐藏。
 *
 * <p><b>使用方式</b>：Service 在返回含敏感字段的（可变）DTO/实体前调用，例如
 * {@code applier.apply(userDto, "system:user", code -> permissions.contains(code))}。
 * 无权限的字段按策略处理：{@code MASK} 经 {@link SensitiveMasker} 脱敏（未指定敏感类型回落通用掩码），
 * {@code HIDE} 置空（null）。</p>
 *
 * <p><b>目标对象须为可变对象</b>：本执行器用反射读写字段；对 {@code record} 等不可变类型无法就地修改，
 * 会跳过该字段并记 WARN（建议对 record 视图改用「构造前脱敏」或 Jackson 序列化期脱敏）。
 * 集合入参会逐元素处理（批次端点友好）。</p>
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
        for (ColumnPermissionFieldView f : fields) {
            String code = ColumnPermissionRules.columnPermissionCode(resourceCode, f.field());
            if (hasColumnPermission != null && hasColumnPermission.test(code)) {
                continue; // 当前主体拥有该列权限，保持明文
            }
            maskField(target, f);
        }
        return target;
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
