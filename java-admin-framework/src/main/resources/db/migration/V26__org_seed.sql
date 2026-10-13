-- =============================================================
-- V26 组织/用户种子：部门总部 + admin 主部门关联 + 测试用户 zhangsan
-- 目的：修复「干净库（CI 从零 Flyway）缺组织种子」导致的链路断裂：
--   1) AssigneeResolver.resolveManager（INITIATOR_MANAGER 受让人）拿发起人主部门，
--      sys_user_org 为空时抛 NOT_FOUND「发起人所在主部门未知」，发起任何含该节点的
--      流程（内置 leaveApproval 的会签节点）即 404；
--   2) 集成测试基线数据缺失（UserIntegrationIT 需部门种子、MessageIntegrationIT 需 ≥2 用户）。
-- 说明：
--   - sys_org/sys_user_org/sys_user 的种子在此前迁移中只有建表（V1）无数据，属既有缺口；
--   - zhangsan 复制 admin 的 password_hash（同库内 SELECT，避免硬编码 BCrypt），
--     仅作测试/演示账号，可正常登录（密码同 admin）；
--   - 全部 INSERT IGNORE（按主键/唯一键幂等），重复执行结果一致；
--   - 已存在组织数据的库（如本机 local 库）不受影响。
-- =============================================================

-- 1) 部门种子：总部（id=1，org_code 租户内唯一）
INSERT IGNORE INTO sys_org (id, tenant_id, parent_id, ancestors, org_code, org_name, org_type, sort_no, status)
VALUES (1, 0, 0, '0', 'HQ', '总部', 'COMPANY', 1, 'ACTIVE');

-- 2) admin（id=1）主部门关联：使 INITIATOR_MANAGER 解析可拿到部门，
--    且 admin 已绑定 MANAGER/FINANCE 角色（sys_user_role 种子），部门内主管解析非空
INSERT IGNORE INTO sys_user_org (tenant_id, user_id, org_id, is_primary)
VALUES (0, 1, 1, 1);

-- 3) 测试用户 zhangsan（id=2）：满足「至少 2 个用户」的消息/审批场景；
--    password_hash 复制 admin（同库 SELECT，登录密码与 admin 相同）
INSERT IGNORE INTO sys_user (id, username, password_hash, display_name)
SELECT 2, 'zhangsan', password_hash, '张三' FROM sys_user WHERE id = 1;
