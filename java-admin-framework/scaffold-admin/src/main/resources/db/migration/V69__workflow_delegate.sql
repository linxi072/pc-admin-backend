-- V69 工作流委派（V1.0.5 #901）：wf_task_ext 增加委派发起人与委派状态列，支撑「临时委托办理、办结交还原审批人」。
--
-- 设计要点：
--   1) wf_task_ext 增两列（与既有 wf_* 表 BIGINT UNSIGNED / VARCHAR 约定一致）：
--        - delegator_user_id ：委派发起人（原审批人）。任务被委派给 assignee_user_id 期间记录真正的负责人，
--          被委托人办结交还后 assignee_user_id 还原为该值。
--        - delegation_state  ：委派状态。PENDING=已委派待办、RESOLVED=被委托人已办结交还、NULL=非委派任务。
--   2) 委派/办结交还的写操作复用既有引擎端口 delegate/resolve 与 wf_approval_record（action=DELEGATE/DELEGATE_RESOLVE），
--      零新表、零新接口权限手工登记（由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN）。
--   3) 本迁移向前追加，幂等依赖 Flyway 版本控制；重复执行会因列已存在报错，需按 Flyway 标准流程 baseline/repair。

ALTER TABLE wf_task_ext
    ADD COLUMN delegator_user_id BIGINT UNSIGNED NULL COMMENT '委派发起人（原审批人），非委派任务为 NULL';

ALTER TABLE wf_task_ext
    ADD COLUMN delegation_state VARCHAR(16) NULL COMMENT '委派状态：PENDING=已委派待办、RESOLVED=已办结交还、NULL=非委派';
