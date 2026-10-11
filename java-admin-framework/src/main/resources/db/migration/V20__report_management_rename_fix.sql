-- =============================================================
-- V20 报表管理重命名修正（兼容已执行过 V19 的历史库）
-- 目的：在「使用报表 → 报表管理」重命名中，保证已部署数据库的功能逻辑一致，
--       不影响原有数据展示与业务流程。
-- 说明：
--   - V19 已写入的菜单/接口资源行（id 固定为 1030 / 900000000101）通过 UPDATE 统一修正为
--     新标识：菜单名「报表管理」、路由 /report/management、权限码 report:read；
--     接口资源权限码 report:read、路径 /api/report/**、controller ReportController。
--   - 对尚未执行 V19 的全新库：V19 先插入旧值，本脚本随后 UPDATE 为新值（净结果一致），
--     且 UPDATE 为幂等（重复执行结果不变），不会破坏既有数据。
--   - 角色绑定（sys_role_menu / sys_role_api）基于 id 关联，本脚本不改动，保持 SUPER_ADMIN 可见可用。
-- =============================================================

UPDATE sys_menu
SET menu_code      = 'report-management',
    menu_name      = '报表管理',
    route_path     = '/report/management',
    permission_code = 'report:read',
    icon           = 'DataAnalysis',
    updated_at     = NOW(3)
WHERE id = 1030;

UPDATE sys_api_resource
SET resource_name   = '报表管理（使用统计 / 类别统计 / 状态统计）',
    permission_code = 'report:read',
    path_pattern    = '/api/report/**',
    controller_method = 'ReportController',
    updated_at     = NOW(3)
WHERE id = 900000000101;
