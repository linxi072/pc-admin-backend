package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantIgnorable;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.CreateRoleRequest;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.entity.SysApiResourceDO;
import com.acme.scaffold.system.entity.SysMenuDO;
import com.acme.scaffold.system.service.OrgService;
import com.acme.scaffold.system.service.RoleService;
import com.acme.scaffold.system.service.UserService;
import com.acme.scaffold.system.tenant.dto.TenantProvisioningRequest;
import com.acme.scaffold.system.tenant.dto.TenantProvisioningView;
import com.acme.scaffold.system.tenant.dto.TenantView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户开通服务（M19 SaaS 开通 / 生产启用闭环）。
 *
 * <p><b>职责</b>：为已存在的租户初始化「默认部门 + 租户管理员角色（绑定全量菜单 + 接口）+ 首位管理员用户」，
 * 三者均归属到目标租户作用域（隔离生效时 {@code tenant_id} 正确落库，未生效时回落平台租户 0，与既有行为零回归）。</p>
 *
 * <p><b>作用域</b>：本方法标注 {@link TenantIgnorable}，在平台管理员跨租户旁路作用域下执行——
 * 既保证开头的「是否已开通」查重对全量租户可见（避免跨租户误判），又允许写入跨租户记录。
 * 内部创建子资源时通过 {@link TenantContext#runWith(long, boolean, Runnable)} 临时切换到目标租户作用域，
 * 使 {@code JooqWriters} 在 {@code enforce=true} 时把 {@code tenant_id} 正确注入到用户/角色/部门等业务表。</p>
 *
 * <p><b>幂等</b>：若 {@code TENANT_ADMIN_<tenantCode>} 角色已存在，视为已开通并拒绝重复开通（CONFLICT）。</p>
 *
 * <p><b>事务</b>：外层 {@code @Transactional} 包裹「租户查询 + 子资源创建」，任一环节失败整体回滚，
 * 杜绝「租户已建但管理员未建」的半开状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final DSLContext dsl;
    private final TenantService tenantService;

    private static final JooqTables.TableRef AR = JooqTables.toTableRef(Tables.SYS_API_RESOURCE);
    private final UserService userService;
    private final RoleService roleService;
    private final OrgService orgService;

    @Transactional
    @TenantIgnorable("租户开通：初始化管理员/角色/部门")
    public TenantProvisioningView provision(Long tenantId, TenantProvisioningRequest req) {
        String adminUsername = (req.adminUsername() == null || req.adminUsername().isBlank())
                ? TenantProvisioningRules.DEFAULT_ADMIN_USERNAME : req.adminUsername().trim();

        // 1) 先全额结构校验，失败零数据库交互
        List<String> errors = TenantProvisioningRules.validate(adminUsername, req.adminPassword(),
                req.adminDisplayName(), req.adminMobile(), req.adminEmail());
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }

        // 2) 租户必须存在（不存在抛 NOT_FOUND）
        TenantView tenant = tenantService.get(tenantId);
        String roleCode = TenantProvisioningRules.deriveRoleCode(tenant.tenantCode());
        String deptCode = TenantProvisioningRules.deriveDeptCode(tenant.tenantCode());

        // 3) 幂等守卫：@TenantIgnorable 作用域下全局可见，可跨租户查重
        if (roleService.existsByCode(roleCode)) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "该租户已开通（管理员角色 " + roleCode + " 已存在）");
        }

        // 4) 在目标租户作用域下创建 部门 / 角色 / 管理员
        final TenantProvisioningView[] holder = new TenantProvisioningView[1];
        TenantContext.runWith(tenantId, false, () -> {
            Long deptId = orgService.create(new CreateOrgRequest(0L, deptCode,
                    tenant.tenantName() + "-默认部门", "DEPARTMENT", 0, null, "ACTIVE"));

            // 菜单 / 接口为平台共享维表（tenant_id 恒 0），全量绑定到租户管理员角色
            Set<Long> menuIds = JooqWriters.fetchList(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                            JooqWriters.notDeleted(JooqTables.SYS_MENU)).stream()
                    .map(SysMenuDO::getId).collect(Collectors.toSet());
            Set<Long> apiIds = JooqWriters.fetchList(dsl, AR, SysApiResourceDO.class,
                            JooqWriters.notDeleted(AR)).stream()
                    .map(SysApiResourceDO::getId).collect(Collectors.toSet());

            Long roleId = roleService.create(new CreateRoleRequest(roleCode,
                    tenant.tenantName() + "-管理员", "BUSINESS", "ACTIVE", 0, menuIds, apiIds));

            Long adminId = userService.create(new CreateUserRequest(adminUsername, req.adminPassword(),
                    req.adminDisplayName(), req.adminMobile(), req.adminEmail(),
                    List.of(roleId), roleId, List.of(deptId), deptId, null));

            holder[0] = new TenantProvisioningView(tenantId, adminId, roleId, deptId, adminUsername, roleCode);
        });

        log.info("租户开通完成：tenantId={}, adminUserId={}, roleCode={}", tenantId, holder[0].adminUserId(), roleCode);
        return holder[0];
    }
}
