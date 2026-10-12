-- V23 工作流设计表补逻辑删除列：修复「删除即物理消失、前端陈旧行操作报不存在」的数据一致性缺陷。
-- 背景：wf_workflow_design 原无 deleted 列，JooqWriters.delete 的逻辑删除退化为物理删除，
-- 删除后行不可追溯，且同一 processKey 可被无痕重建（seeder 反复播种产生新 id）。
-- 本迁移补列后，服务层删除走 deleted=1 逻辑删除；既有行默认 0（可见），不影响存量数据。
ALTER TABLE wf_workflow_design
    ADD COLUMN deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER version,
    ADD KEY idx_tenant_status_deleted (tenant_id, status, deleted);
