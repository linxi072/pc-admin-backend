-- =============================================================
-- V25 菜单排序接口登记：PUT /api/system/menus/sort
-- 目的：菜单拖拽排序 / 上移下移的批量持久化端点，登记接口资源并绑定 SUPER_ADMIN。
-- 说明：
--   - 权限码复用 system:menu:update（与 PUT /api/system/menus/{id} 同一写权限域），
--     不新增权限码，角色授权模型不变；
--   - id 段 900000000409（接 V21 的 900000000108~111 与 V22/V24 之后的 408）；
--   - INSERT IGNORE 幂等，重复执行结果一致；与 ApiResourceScanner 扫描结果一致，避免漂移。
-- =============================================================

INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES (900000000409, 0, '批量调整菜单排序（拖拽/上下移动）', 'system:menu:update', 'PUT', '/api/system/menus/sort', 'MenuController#reorder', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- SUPER_ADMIN 绑定（与 V17/V19/V21/V22 一致，role_id = 1）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, r.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource r
WHERE r.id = 900000000409
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = r.id);
