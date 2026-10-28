-- ============================================================
-- 模块参考 SQL：数据权限
-- 说明：数据权限配置菜单
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V56__data_scope_menu.sql
-- ============================================================

-- V56 数据权限配置菜单（M31）：把后端 DataScopeResourceRegistry 自动发现的可配置资源，
-- 通过本页以「角色 × 资源」矩阵形式可视化配置。后端仅新增只读发现端点
-- GET /api/system/data-scope/resources（权限 system:data-scope:read，由 ApiResourceAutoSyncRunner
-- 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN）；保存仍复用 RoleController 的
-- PUT /api/system/roles/{id}/data-scopes（权限 system:role:update）。
--
-- 设计要点：
--   1) 资源清单由后端启动时扫描 @DataScope 自动得到，新增受控资源无需改前端常量即出现在本页。
--   2) 挂在「系统管理」目录(1002)下，sort_no=116 紧随 实时推送(1126 / 115) 之后。
--   3) route_path 必须与前端路由（system/data-scope）严格一致，否则路由守卫会重定向到工作台。
--   4) 依赖：V32（系统管理目录 id=1002）、DataScopeResourceRegistry（新增）。
--   5) 幂等：INSERT IGNORE + NOT EXISTS（menu_code 唯一），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1127, 0, 1002, 'system:data-scope', '数据权限', 'MENU', '/system/data-scope', 'system/DataScopeView', 'system:data-scope:read', 'Lock', 1, 0, 116, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1127;

