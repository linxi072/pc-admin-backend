-- 工作流模块集成测试用 H2 建表脚本（与 V32__consolidated_schema.sql 中 wf 表结构严格一致，不含菜单种子）。
-- H2 MySQL 兼容模式 + DATABASE_TO_LOWER：未引号标识符与列名统一小写，贴近生产 MySQL 语义。
-- wf_task_ext 已含 activity_name 快照列（R7 已办列表需求）。

CREATE TABLE "wf_task_ext" (
    "id"                          BIGINT        NOT NULL,
    "tenant_id"                   BIGINT        NOT NULL DEFAULT 0,
    "task_id"                     VARCHAR(64)   NOT NULL,
    "process_instance_id"         VARCHAR(64)   NOT NULL,
    "activity_id"                 VARCHAR(128)  NOT NULL,
    "activity_name"               VARCHAR(255)  NULL,
    "assignee_user_id"            BIGINT        NULL,
    "original_assignee_user_id"   BIGINT        NULL,
    "delegator_user_id"           BIGINT        NULL,
    "delegation_state"            VARCHAR(20)   NULL,
    "status"                      VARCHAR(20)   NOT NULL,
    "approval_mode"               VARCHAR(20)   NOT NULL,
    "due_at"                      TIMESTAMP     NULL,
    "claimed_at"                  TIMESTAMP     NULL,
    "completed_at"                TIMESTAMP     NULL,
    "version"                     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY ("id"),
    UNIQUE ("task_id")
);

CREATE INDEX "idx_wf_task_assignee_status"    ON "wf_task_ext" ("tenant_id", "assignee_user_id", "status");
CREATE INDEX "idx_wf_task_instance_activity"  ON "wf_task_ext" ("process_instance_id", "activity_id");

CREATE TABLE "wf_instance_ext" (
    "id"                    BIGINT        NOT NULL,
    "tenant_id"             BIGINT        NOT NULL DEFAULT 0,
    "process_instance_id"   VARCHAR(64)   NOT NULL,
    "process_definition_id" VARCHAR(128)  NOT NULL,
    "business_type"         VARCHAR(64)   NOT NULL,
    "business_id"           VARCHAR(128)  NOT NULL,
    "title"                 VARCHAR(300)  NOT NULL,
    "starter_user_id"       BIGINT        NOT NULL,
    "starter_org_id"        BIGINT        NULL,
    "current_activity_id"   VARCHAR(128)  NULL,
    "status"                VARCHAR(20)   NOT NULL,
    "started_at"            TIMESTAMP     NOT NULL,
    "finished_at"           TIMESTAMP     NULL,
    "version"               INT           NOT NULL DEFAULT 0,
    PRIMARY KEY ("id"),
    UNIQUE ("process_instance_id")
);

CREATE INDEX "idx_wf_instance_starter_status" ON "wf_instance_ext" ("tenant_id", "starter_user_id", "status");
CREATE INDEX "idx_wf_instance_status_started" ON "wf_instance_ext" ("tenant_id", "status", "started_at");
