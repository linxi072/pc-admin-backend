-- M42 健康探针前端可视化页菜单：在「系统管理」(1002) 下新增「健康探针」入口，
-- 承载 /livez、/readyz 两个 k8s 风格根路径探针的可视化（存活/就绪状态 + 明细）。
-- 设计要点：
--   1) 后端探针端点已在 scaffold-monitor 的 ProbeController 提供（permitAll，无需鉴权）；
--   2) 读权限复用 system:monitor:read（由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN），
--      无需新增权限登记（与「系统监控」「缓存监控」「可观测中心」同权）；
--   3) route_path 须与前端路由（system/health）严格一致，否则路由守卫会重定向到工作台；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN，可重复执行；
--   5) vite dev 代理需转发 /livez、/readyz（见 pc-admin-vue3-vite/vite.config.js），
--      生产环境由同源后端直接处理根路径探针。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1129, 0, 1002, 'system:health', '健康探针', 'MENU', '/system/health', 'system/HealthView', 'system:monitor:read', 'Monitor', 1, 0, 119, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1129;
