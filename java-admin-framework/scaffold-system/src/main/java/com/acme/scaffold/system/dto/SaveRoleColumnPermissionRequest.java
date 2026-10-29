package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * 保存角色字段级（列级）数据权限入参。
 *
 * <p>整体覆盖式保存：Service 会先清空该角色的全部字段权限再写入，因此前端只需提交
 * 「当前生效的完整权限码集合」（每个元素形如 {@code system:user:column:mobile}），无需处理增量 diff。</p>
 *
 * @param codes 该角色拥有明文可见权的字段权限码集合；空集合表示「全部脱敏/隐藏」
 */
public record SaveRoleColumnPermissionRequest(
        @NotNull(message = "{validation.columnPermission.codes.notNull}")
        Set<String> codes) {
}
