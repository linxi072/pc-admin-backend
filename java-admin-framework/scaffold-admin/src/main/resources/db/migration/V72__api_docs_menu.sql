-- V72 API 文档中心菜单（#972）：复用 Springdoc 标准端点 /v3/api-docs 的纯前端浏览器，
-- 在「系统管理」(parent_id=1002) 下新增「API 文档」入口，承载 ApiDocView。
-- 设计要点：
--   1) 纯前端模块，后端无需新增 Controller；springdoc 已在 scaffold-framework 启用（local 开放、prod 关闭）；
--   2) route_path 须与前端路由（system/api-docs）严格一致，否则路由守卫会重定向到工作台；
--   3) 权限码 system:api-docs:read 预留：当前后端未对 /v3/api-docs 挂独立 @PreAuthorize（local 开放），
--      若后续加鉴权，ApiResourceAutoSyncRunner 会扫描并自动授予 SUPER_ADMIN；菜单此处先绑定 SUPER_ADMIN 保证可见；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行；sort_no=123 置于幂等记录(122) 之后。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1143, 0, 1002, 'system:api-docs', 'API 文档', 'MENU', '/system/api-docs', 'system/ApiDocView', 'system:api-docs:read', 'Document', 1, 0, 123, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1143;
