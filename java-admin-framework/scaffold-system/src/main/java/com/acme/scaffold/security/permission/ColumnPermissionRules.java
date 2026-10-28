package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 字段级数据权限纯函数规则：权限码派生、名称回退、字段合并与基础校验。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link ColumnPermissionResourceRegistry}（归集去重）与
 * {@link ColumnPermissionApplier}（运行时脱敏）复用，保证「声明什么、查什么、按什么脱敏」三处口径同源。</p>
 */
public final class ColumnPermissionRules {

    /** 字段级权限码后缀分隔：完整码为 {@code <resourceCode>:column:<field>}。 */
    public static final String CODE_PREFIX = ":column:";

    private ColumnPermissionRules() {
    }

    /** 派生某资源某字段的列权限码，例如 system:user:column:mobile。 */
    public static String columnPermissionCode(String resourceCode, String field) {
        return resourceCode + CODE_PREFIX + field;
    }

    /** 资源展示名回退：空则回退为 resourceCode。 */
    public static String resolveName(String resourceCode, String name) {
        return (name == null || name.isBlank()) ? resourceCode : name;
    }

    /** 字段展示名回退：空则回退为 field。 */
    public static String resolveFieldLabel(String field, String label) {
        return (label == null || label.isBlank()) ? field : label;
    }

    /**
     * 合并多个 {@link ColumnField} 声明：按字段名去重（保留首次出现的策略/敏感类型），返回有序视图列表。
     * 用于同一 resourceCode 跨多个方法归集时消除重复。
     */
    public static List<ColumnPermissionFieldView> mergeFields(List<ColumnField> fields) {
        List<ColumnPermissionFieldView> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ColumnField f : fields) {
            if (f == null || f.name() == null || f.name().isBlank()) {
                continue;
            }
            if (!seen.add(f.name())) {
                continue;
            }
            out.add(new ColumnPermissionFieldView(f.name(),
                    resolveFieldLabel(f.name(), f.label()),
                    f.strategy() == null ? ColumnStrategy.MASK : f.strategy(),
                    f.sensitiveType()));
        }
        return out;
    }

    /**
     * 校验单个 {@link ColumnPermission} 声明结构：返回错误列表，空列表表示通过。
     * 仅校验结构（resourceCode 非空、至少声明一个字段、字段名非空且不重复），
     * 字段唯一性由注册表保证，故此处仅对声明本身做轻量校验。
     */
    public static List<String> validate(ColumnPermission ann) {
        List<String> errors = new ArrayList<>();
        if (ann == null) {
            errors.add("注解声明为空");
            return errors;
        }
        if (ann.resourceCode() == null || ann.resourceCode().isBlank()) {
            errors.add("resourceCode 必填");
        }
        if (ann.fields() == null || ann.fields().length == 0) {
            errors.add("fields 至少声明一个受控字段");
            return errors;
        }
        Set<String> names = new LinkedHashSet<>();
        for (ColumnField f : ann.fields()) {
            if (f == null || f.name() == null || f.name().isBlank()) {
                errors.add("存在字段名为空的受控字段");
                continue;
            }
            if (!names.add(f.name())) {
                errors.add("受控字段名重复: " + f.name());
            }
        }
        return errors;
    }
}
