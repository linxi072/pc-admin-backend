-- V18 内置工作流引用角色种子
-- 背景：内置审批流（leaveApproval / expense）的审批节点使用 ROLE=FINANCE（财务角色）作为受让人，
--       运行期 INITIATOR_MANAGER 节点还需 MANAGER 角色解析主管。基线（V1）仅播种 SUPER_ADMIN，
--       缺失二者会导致 BuiltinWorkflowSeeder 在应用启动时发布流程失败：
--       AssigneeResolver.validateConfig 抛 “审批节点 … 引用的角色不存在: FINANCE”，内置工作流无法发布。
-- 此处补齐 FINANCE / MANAGER 角色并绑定超级管理员，使内置流程可发布且运行期可解析出审批人
-- （本地演示直接用 admin 作为审批人；生产环境请按实际组织将角色分配给对应人员）。
-- 幂等：INSERT IGNORE 保证重复执行不报错；sys_role.id 非自增需显式指定，sys_user_role.id 自增省略。

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, sort_no, created_at, updated_at)
VALUES (2, 0, 'FINANCE', '财务', 'BUSINESS', 'ACTIVE', 2, NOW(3), NOW(3)),
       (3, 0, 'MANAGER', '主管', 'BUSINESS', 'ACTIVE', 3, NOW(3), NOW(3));

-- 绑定到超级管理员（user_id=1）；生产环境按需分配给真实财务/主管人员
INSERT IGNORE INTO sys_user_role (tenant_id, user_id, role_id, is_primary, created_at)
VALUES (0, 1, 2, 0, NOW(3)),
       (0, 1, 3, 0, NOW(3));
