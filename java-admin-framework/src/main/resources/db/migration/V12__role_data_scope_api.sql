-- =============================================================================
-- V12: 角色数据权限配置接口资源登记
-- =============================================================================
-- 背景：
--   本轮补齐了角色数据权限的配置入口（RoleController 新增 2 个端点）。
--   V11 已把当时的 61 个接口落库，但这两个端点是 V11 之后新增的，
--   若不登记，SUPER_ADMIN 也无法调用（hasAuthority 找不到对应权限码 -> 403）。
--
-- 权限码复用既有值，不新增：
--   查询 -> system:role:read
--   保存 -> system:role:update
-- 与角色 CRUD 的读写分离保持一致：看数据权限不需要改角色，改数据权限才需要。
--
-- 数据来源：由 ApiResourceScanner 真实扫描 Spring MVC 路由表导出，非手工编写。
--
-- 幂等性：
--   走 INSERT IGNORE（依赖主键幂等），重复执行结果一致。
--   同时补齐 sys_role_api 授权，保证 SUPER_ADMIN 拿到新权限码。
-- =============================================================================

-- 1) 接口资源登记
INSERT IGNORE INTO sys_api_resource
    (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method,
     auth_mode, status, risk_level)
VALUES (900000000062, 0, '角色数据权限列表', 'system:role:read', 'GET',
        '/api/system/roles/{id}/data-scopes', 'RoleController#listDataScopes', 'REQUIRED', 'ACTIVE', 'NORMAL');

INSERT IGNORE INTO sys_api_resource
    (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method,
     auth_mode, status, risk_level)
VALUES (900000000063, 0, '保存角色数据权限（覆盖式）', 'system:role:update', 'PUT',
        '/api/system/roles/{id}/data-scopes', 'RoleController#saveDataScope', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- 2) 补齐 SUPER_ADMIN 授权（V11 的全量授权语句只覆盖当时已存在的资源）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, r.id, a.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
         JOIN sys_api_resource a
WHERE r.role_code = 'SUPER_ADMIN'
  AND a.id IN (900000000062, 900000000063)
  AND NOT EXISTS (SELECT 1
                  FROM sys_role_api ra
                  WHERE ra.role_id = r.id
                    AND ra.api_id = a.id);
