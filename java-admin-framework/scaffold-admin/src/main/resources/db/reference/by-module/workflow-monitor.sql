-- ============================================================
-- 模块参考 SQL：工作流监控
-- 说明：工作流监控视图/表
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V49__workflow_monitor.sql
-- ============================================================

-- V49 流程监控菜单（M23）：零表结构变更——监控数据来自既有 wf_instance_ext / wf_task_ext /
-- wf_approval_record 与 Flowable 引擎查询，本迁移仅播种「流程监控」菜单并绑定 SUPER_ADMIN。
--
-- 设计要点：
--   1) 监控能力（M23，M4 续）：运行实例分页 / 超时任务分页 / 定义版本链 / 统计摘要（只读）
--      + 任务催办（唯一写操作，实时推送 + 最小间隔防轰炸，催办记录复用 wf_approval_record
--      的 action=REMIND，零新表）。
--   2) 菜单挂「工作流」目录（id=1003，来自 data_dml.sql 基础菜单），sort_no=40 紧随工作流设计（30）之后。
--   3) 接口资源（workflow:monitor:read / workflow:task:remind）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   4) 催办最小间隔经 app.workflow.monitor.remind-min-interval-minutes 配置（默认 30 分钟）。

-- 1) 菜单：流程监控
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1123, 0, 1003, 'workflow:monitor', '流程监控', 'MENU', '/workflow/monitor', 'workflow/Monitor', 'workflow:monitor:read', 'DataLine', 1, 0, 40, 'ACTIVE', NOW(3), NOW(3));

-- 2) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1123;

