-- V71 幂等记录管理菜单（M2 幂等模块运维侧面）：在「系统管理」(parent_id=1002) 下新增「幂等记录」入口，
-- 承载幂等记录分页 / 统计 / 过期清理三接口的前端页面（IdempotencyRecordView）。
-- 设计要点：
--   1) 后端 IdempotencyRecordController（/api/system/idempotency-records），读权限 system:idempotency:read、
--      清理权限 system:idempotency:manage，均经 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN；
--   2) route_path 须与前端路由（system/idempotency）严格一致，否则路由守卫会重定向到工作台；
--   3) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行；sort_no=122 置于 OAuth2 令牌(120) 之后。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1142, 0, 1002, 'system:idempotency', '幂等记录', 'MENU', '/system/idempotency', 'system/IdempotencyRecordView', 'system:idempotency:read', 'Key', 1, 0, 122, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1142;
