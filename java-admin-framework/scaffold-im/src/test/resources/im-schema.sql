-- IM 模块集成测试用 H2 建表脚本（与 V75__im.sql 结构严格一致，不含菜单种子）。
-- H2 MySQL 兼容模式 + DATABASE_TO_LOWER：未引号标识符与列名统一小写，贴近生产 MySQL 语义。

CREATE TABLE "im_conversation" (
    "id"                 BIGINT        NOT NULL,
    "tenant_id"          BIGINT        NOT NULL DEFAULT 0,
    "conversation_type"  VARCHAR(20)   NOT NULL,
    "title"              VARCHAR(255)  NULL,
    "owner_id"           BIGINT        NULL,
    "status"             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    "version"            INT           NOT NULL DEFAULT 0,
    "deleted"            INT           NOT NULL DEFAULT 0,
    "created_by"         VARCHAR(64)   NULL,
    "created_at"         TIMESTAMP     NULL,
    "updated_by"         VARCHAR(64)   NULL,
    "updated_at"         TIMESTAMP     NULL,
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_im_conversation_tenant_type" ON "im_conversation" ("tenant_id", "conversation_type");
CREATE INDEX "idx_im_conversation_status"      ON "im_conversation" ("tenant_id", "status");

CREATE TABLE "im_conversation_member" (
    "id"                   BIGINT        NOT NULL,
    "tenant_id"            BIGINT        NOT NULL DEFAULT 0,
    "conversation_id"      BIGINT        NOT NULL,
    "user_id"              BIGINT        NOT NULL,
    "member_role"          VARCHAR(20)   NOT NULL DEFAULT 'MEMBER',
    "last_read_message_id" BIGINT        NULL,
    "status"               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    "version"              INT           NOT NULL DEFAULT 0,
    "deleted"              INT           NOT NULL DEFAULT 0,
    "created_at"           TIMESTAMP     NULL,
    "updated_at"           TIMESTAMP     NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "uk_im_member_conv_user" UNIQUE ("tenant_id", "conversation_id", "user_id")
);

CREATE INDEX "idx_im_member_tenant_conv" ON "im_conversation_member" ("tenant_id", "conversation_id");
CREATE INDEX "idx_im_member_tenant_user" ON "im_conversation_member" ("tenant_id", "user_id");

CREATE TABLE "im_message" (
    "id"              BIGINT        NOT NULL,
    "tenant_id"       BIGINT        NOT NULL DEFAULT 0,
    "conversation_id" BIGINT        NOT NULL,
    "sender_id"       BIGINT        NOT NULL,
    "content"         TEXT          NULL,
    "msg_type"        VARCHAR(20)   NOT NULL DEFAULT 'TEXT',
    "status"          VARCHAR(20)   NOT NULL DEFAULT 'SENT',
    "reply_to_id"     BIGINT        NULL,
    "version"         INT           NOT NULL DEFAULT 0,
    "deleted"         INT           NOT NULL DEFAULT 0,
    "created_by"      VARCHAR(64)   NULL,
    "created_at"      TIMESTAMP     NULL,
    "updated_by"      VARCHAR(64)   NULL,
    "updated_at"      TIMESTAMP     NULL,
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_im_message_tenant_conv" ON "im_message" ("tenant_id", "conversation_id");
CREATE INDEX "idx_im_message_created"     ON "im_message" ("tenant_id", "conversation_id", "created_at");
