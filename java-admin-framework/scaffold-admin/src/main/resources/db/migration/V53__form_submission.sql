-- V53 表单提交落库（M22 正式立项）：sys_form_submission，承接 V47 低代码表单试点的
-- 「提交落库与绑定目标」决策点（target_type=FORM_SUBMISSION），将动态表单提交值
-- 以 JSON 持久化到独立提交表，不侵入业务表、不做运行时 DDL。
--
-- 设计要点：
--   1) 通用提交表：以 (form_definition_id, form_code) 关联表单定义，data_json 存提交值；
--      后端提交前经 FormSubmissionRules 按 schema 字段校验（必填/类型/长度/选项白名单/未知字段拒绝）。
--   2) 沿用全库约定：tenant_id 默认 0（与 FORM_DEFINITION 同口径），逻辑删除 deleted + 乐观锁 version，
--      created_at/updated_at 由 JooqWriters.insert 自动填充。
--   3) data_json 不在此做结构约束（schema 校验在应用层完成），便于后续字段演进。
--   4) 将 V47 试点表单 contact-feedback 的 target_type 由 NONE 置为 FORM_SUBMISSION，
--      使其正式具备提交能力（idempotent UPDATE，重复执行幂等）。
--   5) 接口资源 system:form-submission:create / system:form-submission:read 不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   6) 依赖：V47（sys_form_definition、菜单 id=1121 低代码表单试点）。

-- 1) 表：sys_form_submission（逻辑删除 + 乐观锁；按 (tenant_id, form_code, created_at) 建查询索引）
CREATE TABLE IF NOT EXISTS sys_form_submission (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    form_definition_id BIGINT UNSIGNED NOT NULL,
    form_code VARCHAR(64) NOT NULL,
    submitted_by VARCHAR(64) NULL,
    data_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_form_created (tenant_id, form_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 试点表单绑定目标落库：contact-feedback 由 NONE → FORM_SUBMISSION（幂等 UPDATE）
UPDATE sys_form_definition
SET target_type = 'FORM_SUBMISSION'
WHERE form_code = 'contact-feedback'
  AND target_type = 'NONE';
