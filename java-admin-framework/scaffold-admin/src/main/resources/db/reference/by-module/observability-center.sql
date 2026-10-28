-- ============================================================
-- 模块参考 SQL：可观测中心
-- 说明：链路/N+1/幂等/告警收件箱；修复 V35/V36 菜单碰撞
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V39__observability_center.sql
-- ============================================================

-- V39 可观测中心菜单种子 + 导入中心菜单 ID 碰撞修复。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:observability:read / system:kafka:read / system:idempotency:read 等）
-- 不在此手工登记，由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- =============================================================
-- 1) 历史缺陷修复：V35 与 V36 菜单 ID 碰撞（1091）
--
-- V35__post_management.sql 以 id=1091 插入「岗位管理」（menu_code='system:post'），
-- V36__import_center.sql 同样以 id=1091 插入「导入中心」（menu_code='system:import'）。
-- sys_menu 主键为 id，两条迁移均用 INSERT IGNORE：Flyway 顺序执行下 V36 的插入
-- 因主键冲突被静默丢弃——全新部署中「导入中心」菜单及其 SUPER_ADMIN 绑定缺失，
-- 路由守卫按菜单树鉴权会将 /system/import 重定向到工作台，页面不可达。
--
-- 已应用的迁移不可修改（Flyway checksum 校验会失败），故在本迁移中修复：
-- 以未占用的新 id=1093 重新播种「导入中心」。uk_tenant_menu_code (tenant_id, menu_code)
-- 唯一键保证幂等：若目标环境已手工补过该菜单，INSERT IGNORE 自然跳过，不会产生重复行。
-- =============================================================
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1093, 0, 1002, 'system:import', '导入中心', 'MENU', '/system/import', 'system/ImportCenter', 'system:import:read', 'Upload', 1, 0, 101, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE menu_code = 'system:import';

-- =============================================================
-- 2) 新增：可观测中心（运维面统一入口，前端 ObservabilityCenterView 五 Tab）
--
-- 路由 /system/observability；页面级权限取 system:monitor:read（监控域基础权限，
-- 与「系统监控」同权，覆盖 N+1 疑似与告警收件箱两个 Tab）；链路追踪 / Kafka 监控 /
-- 幂等记录三个 Tab 由后端按 system:observability:read / system:kafka:read /
-- system:idempotency:read 独立鉴权，前端对 403 展示降级空态（最小授权，不越权放行）。
-- =============================================================
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1094, 0, 1002, 'system:observability-center', '可观测中心', 'MENU', '/system/observability', 'system/ObservabilityCenter', 'system:monitor:read', 'DataLine', 1, 0, 103, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1094;

