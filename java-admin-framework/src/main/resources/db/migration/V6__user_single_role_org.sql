-- V6 用户配置约束收敛：用户仅绑定「单个角色 + 单个部门」。
--
-- 背景：此前模型为多对多（sys_user_role 支持 user->N role、sys_user_org 支持 user->N org），
--       造成权限判定与数据范围计算需要做集合聚合，业务含义模糊、易出现并集越权。
-- 变更：把 role_id / org_id 直接落到 sys_user 实体列，关联表退化为过渡数据并清理。
--
-- 表名 / 列名与 com.acme.scaffold.jooq.JooqTables 中的注册严格一致。

-- 1) sys_user 增加单值外键列（role_id 角色、org_id 部门）
ALTER TABLE sys_user
    ADD COLUMN role_id BIGINT UNSIGNED NULL COMMENT '单一角色ID(收敛后不再允许多角色)' AFTER primary_org_id,
    ADD COLUMN org_id BIGINT UNSIGNED NULL COMMENT '单一部门ID(收敛后不再允许多部门)' AFTER role_id,
    ADD KEY idx_tenant_role (tenant_id, role_id),
    ADD KEY idx_tenant_org (tenant_id, org_id);

-- 2) 数据回填：多角色时取「数据范围最小(最严格)」的一条，保证降级后不放大权限。
--    优先级：自定义(CUSTOM) > 本部门及子部门(DEPT_AND_CHILD) > 本部门(DEPT/SELF) > 全部(ALL)。
--    仅当该角色在 sys_role_data_scope 上存在对应 resource_code 规则时才参与排序；
--    无规则的视为不限制(ALL)，排在最后。
UPDATE sys_user u
LEFT JOIN (
    SELECT ur.user_id,
           ur.role_id,
           ROW_NUMBER() OVER (
               PARTITION BY ur.user_id
               ORDER BY FIELD(
                   COALESCE((
                       SELECT MIN(r.scope_type)
                       FROM sys_role_data_scope r
                       WHERE r.role_id = ur.role_id
                         AND r.resource_code = 'system:user'
                   ), 'ALL'),
                   'CUSTOM', 'DEPT_AND_CHILD', 'DEPT', 'SELF', 'ALL'
               ),
               ur.role_id
           ) AS rn
    FROM sys_user_role ur
) picked ON picked.user_id = u.id AND picked.rn = 1
SET u.role_id = picked.role_id;

-- 3) 部门回填：多部门时取 is_primary 优先，其次取最小 org_id，保证确定性。
UPDATE sys_user u
LEFT JOIN (
    SELECT uo.user_id,
           uo.org_id,
           ROW_NUMBER() OVER (
               PARTITION BY uo.user_id
               ORDER BY uo.is_primary DESC, uo.org_id ASC
           ) AS rn
    FROM sys_user_org uo
) picked ON picked.user_id = u.id AND picked.rn = 1
SET u.org_id = picked.org_id;

-- 4) 兼容期保留 primary_org_id：与 org_id 保持一致，供尚未切换的旧代码读取。
UPDATE sys_user SET primary_org_id = org_id WHERE org_id IS NOT NULL;

-- 5) 清理多对多关联表（收敛后不再有查询/写入方引用）。
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_user_org;

-- 6) 约束说明：为兼容存量数据，role_id/org_id 暂不建外键与 NOT NULL，
--    「角色必填」由应用层 CreateUserRequest 校验与 UserService 强制保证；
--    存量未绑定角色的用户登录后无任何权限（权限聚合返回空集），符合最小权限原则。
