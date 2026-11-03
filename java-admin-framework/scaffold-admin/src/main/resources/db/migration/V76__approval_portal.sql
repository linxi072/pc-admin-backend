-- V76 审批中心门户（ROADMAP R7）：wf_task_ext 补 activity_name 快照列 + 审批中心菜单种子。
-- 设计要点：
--   1) activity_name 为任务创建期从工作流引擎捕获的活动（节点）名称快照，
--      待办/已办列表直接读取，无需回查引擎即可展示可读节点名；
--   2) ALTER 风格沿用 V69（workflow_delegate），标识符保留 snake_case 小写（H2 元数据库名小写、
--      生产 MySQL 大小写敏感，本仓既有迁移均以小写书写，保持一致）；
--   3) 审批中心为前端聚合页（待办/已办/我发起/可发起 + 统计），统一菜单码
--      approval:portal:view，由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动登记并授予 SUPER_ADMIN；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

ALTER TABLE wf_task_ext
    ADD COLUMN activity_name VARCHAR(255) NULL COMMENT '活动（节点）名称快照：任务创建期从引擎捕获，已办列表无需回查引擎即可展示可读节点名';

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9501, 0, 0, 'approval', '审批中心', 'CATALOG', NULL, NULL, NULL, 'Tickets', 1, 0, 95, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9502, 0, 9501, 'approval:portal', '审批工作台', 'MENU', '/approval/portal', 'approval/PortalView', 'approval:portal:view', 'Tickets', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9502;
