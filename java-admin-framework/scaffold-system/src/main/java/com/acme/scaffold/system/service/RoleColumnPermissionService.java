package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.security.permission.ColumnPermissionRules;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.entity.SysRoleColumnPermissionDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色字段级（列级）数据权限配置服务（M41）。
 *
 * <p>与 {@link RoleDataScopeService}（行级）对称：此处管理「角色 × 资源 × 字段」的明文可见权。
 * 此前 {@code @ColumnPermission} 注解与 {@code ColumnPermissionResourceRegistry} 已能自动发现受控字段，
 * 但「按角色分配可见权」这一环缺失（仅有只读资源端点），导致字段级权限形同虚设；
 * 本服务补齐这一配置入口，使 {@code sys_role_column_permission} 真正可用。</p>
 *
 * <p>写入语义为<b>整体覆盖</b>：按 roleId 先删后插，天然幂等，且避免前端做三态 diff。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleColumnPermissionService {

    /** 单角色允许的字段权限码数量上限，防止误传超大列表拖垮事务。 */
    private static final int MAX_CODES = 2000;

    private static final JooqTables.TableRef ROLE = JooqTables.toTableRef(Tables.SYS_ROLE);

    private final DSLContext dsl;
    private final TokenVersionService tokenVersionService;

    /** 桥接自代码生成层 {@code Tables.SYS_ROLE_COLUMN_PERMISSION} 的类型安全表引用（B21）。 */
    private static final JooqTables.TableRef RCP =
            JooqTables.toTableRef(Tables.SYS_ROLE_COLUMN_PERMISSION);

    /**
     * 查询某角色已配置的全部字段权限码（形如 {@code system:user:column:mobile}）。
     *
     * @param roleId 角色 ID
     * @return 权限码集合；未配置时返回空集合（页面据此展示「该角色对这些字段脱敏/隐藏」）
     */
    public Set<String> codes(Long roleId) {
        requireRole(roleId);
        List<SysRoleColumnPermissionDO> rows = JooqWriters.fetchList(dsl, RCP,
                SysRoleColumnPermissionDO.class,
                Tables.SYS_ROLE_COLUMN_PERMISSION.ROLE_ID.eq(roleId));
        return rows.stream()
                .map(r -> ColumnPermissionRules.columnPermissionCode(r.getResourceCode(), r.getField()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 解析一组角色 ID 实际拥有的字段权限码集合（多角色取并集）。
     *
     * <p>供运行时脱敏使用：把「当前用户所属角色的字段权限」解析为权限码集合，
     * 再由 {@code ColumnPermissionApplier} 的判定谓词消费。</p>
     */
    public Set<String> grantedCodes(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<SysRoleColumnPermissionDO> rows = JooqWriters.fetchList(dsl, RCP,
                SysRoleColumnPermissionDO.class,
                Tables.SYS_ROLE_COLUMN_PERMISSION.ROLE_ID.in(roleIds));
        return rows.stream()
                .map(r -> ColumnPermissionRules.columnPermissionCode(r.getResourceCode(), r.getField()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 保存（覆盖）某角色的字段权限码集合。
     *
     * @param roleId 角色 ID
     * @param codes  权限码集合（形如 {@code system:user:column:mobile}），空集合表示「全部字段脱敏/隐藏」
     */
    @Transactional
    public void save(Long roleId, Set<String> codes) {
        requireRole(roleId);
        Set<String> normalized = normalize(codes);

        // 覆盖式写入：先清旧记录，再插新记录
        deleteAll(roleId);
        for (String code : normalized) {
            ColumnPermissionRules.ColumnCode parsed = ColumnPermissionRules.parseCode(code);
            if (parsed == null) {
                // 非法码（格式错误或字段为空）直接跳过，避免脏数据；记录日志便于排查前端误传
                log.warn("字段权限保存跳过非法权限码: roleId={}, code={}", roleId, code);
                continue;
            }
            SysRoleColumnPermissionDO row = new SysRoleColumnPermissionDO();
            row.setRoleId(roleId);
            row.setResourceCode(parsed.resourceCode());
            row.setField(parsed.field());
            JooqWriters.insert(dsl, RCP, row);
        }
        log.info("字段权限已更新: roleId={}, codeCount={}", roleId, normalized.size());
        // 字段可见权变更会影响持有该角色用户的敏感字段展示：递增其 token 版本，
        // 否则用户手里的旧凭证仍会带着过期的权限继续访问
        tokenVersionService.bumpRoleHolders(List.of(roleId));
    }

    /** 去重并保持提交顺序，同时剔除 null/空白与非法码。 */
    private Set<String> normalize(Set<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        if (codes == null) {
            return out;
        }
        for (String c : codes) {
            if (c == null || c.isBlank()) {
                continue;
            }
            out.add(c.trim());
        }
        if (out.size() > MAX_CODES) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "字段权限码数量不能超过 " + MAX_CODES + " 个");
        }
        return out;
    }

    /** 删除该角色的全部字段权限记录。 */
    private void deleteAll(Long roleId) {
        dsl.delete(RCP.table())
                .where(RCP.field("role_id", Long.class).eq(roleId))
                .execute();
    }

    /** 校验角色存在，返回角色 ID。 */
    private Long requireRole(Long roleId) {
        SysRoleDO role = JooqWriters.fetchById(dsl, ROLE, SysRoleDO.class, roleId);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        return roleId;
    }
}
