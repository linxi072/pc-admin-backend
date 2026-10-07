package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 保存角色数据权限规则入参。
 *
 * <p>整体覆盖式保存：Service 会先清空该角色该资源下的旧规则再写入，
 * 因此前端只需提交「当前生效的完整规则集」，无需处理增量 diff。
 *
 * @param resourceCode 资源编码，例如 {@code system:user}
 * @param scopeType    范围类型：ALL / SELF / DEPT / DEPT_AND_CHILD / CUSTOM
 * @param orgIds       自定义机构集合；仅 {@code CUSTOM} 时生效，其余类型必须为空
 */
public record SaveDataScopeRequest(
        @NotNull(message = "资源编码不能为空")
        @Size(max = 128, message = "资源编码长度不能超过 128")
        String resourceCode,

        @NotNull(message = "数据范围类型不能为空")
        DataScopeTypeView scopeType,

        @Size(max = 500, message = "自定义机构数量不能超过 500")
        List<Long> orgIds) {

    /**
     * 数据范围的传输对象枚举（与持久层的 {@code scope_type} 字符串对应）。
     *
     * <p>独立于 {@code security.permission.DataScopeType}，避免把内部枚举直接暴露为接口契约 ——
     * 内部枚举一旦增删枚举值就会破坏前端契约。
     */
    public enum DataScopeTypeView {
        /** 全部数据。 */
        ALL,
        /** 仅本人数据。 */
        SELF,
        /** 本机构数据。 */
        DEPT,
        /** 本机构及下属机构。 */
        DEPT_AND_CHILD,
        /** 自定义机构集合。 */
        CUSTOM
    }
}
