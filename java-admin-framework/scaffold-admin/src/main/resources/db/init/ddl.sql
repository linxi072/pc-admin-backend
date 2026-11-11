-- ============================================================
-- ddl.sql — 数据库结构初始化（全新空库专用 / 手工执行）
--
-- 【执行顺序】必须先执行本文件，再执行 dml.sql。
-- 【执行方式】
--   mysql -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 \
--     < ddl.sql
--   mysql -h127.0.0.1 -P3306 -uroot -p --default-character-set=utf8mb4 \
--     < dml.sql
-- 【库名 / 字符集】scaffold / utf8mb4 + utf8mb4_0900_ai_ci（MySQL 8）。
--   注意：本文件只含 CREATE TABLE 与 CREATE INDEX（纯结构定义）。
--   执行前请先建库：CREATE DATABASE IF NOT EXISTS `scaffold`
--     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--   （若服务端不支持 0900 排序规则，改为 utf8mb4_general_ci 并同步修改下方
--    建表语句中的显式 COLLATE。）
-- 【幂等性】仅适用于空库一次执行；CREATE TABLE / CREATE INDEX 重复执行会报错。
--   存量库升级请由 DBA 逐项评估差异。
-- 【来源与整理口径】由原 db/init/{00_create_database,01_full_schema}.sql 与
--   db/reference/schema_ddl.sql 归并整理而成，覆盖原版本链 V32~V85（53 个 SQL
--   迁移 + Java 迁移 V57 的等价内容）。本次整理将该版本链中的 ALTER TABLE
--   （ADD/DROP 列、ADD/DROP 索引）已并回对应 CREATE TABLE，DROP/RENAME 等
--   历史演进步骤被消解，最终每个表只保留一份 CREATE TABLE（含最终列与内联
--   主键/唯一/索引），附加索引以独立 CREATE INDEX 给出。原 58 个分散 SQL
--   文件已删除，版本历史可从 git 记录取回。
-- ============================================================


CREATE TABLE `sys_user` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `username` VARCHAR(64) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `display_name` VARCHAR(100) NOT NULL,
    `mobile` VARCHAR(32) NULL,
    `email` VARCHAR(128) NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `failed_login_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `locked_until` DATETIME(3) NULL,
    `password_changed_at` DATETIME(3) NULL,
    `password_expired` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `token_version` INT UNSIGNED NOT NULL DEFAULT 1,
    `mfa_enabled` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `last_login_at` DATETIME(3) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `avatar_url` VARCHAR(255) NULL COMMENT '头像地址(相对路径)',
    `notify_site_message` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '站内信通知开关',
    `notify_email` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '邮件通知开关',
    `notify_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '短信通知开关',
    `show_login_log` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否展示我的登录记录',
    `mask_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '对外展示时脱敏手机号',
    `discoverable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '允许被其他用户搜索到'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_org` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ancestors` VARCHAR(1000) NOT NULL DEFAULT '0',
    `org_code` VARCHAR(64) NOT NULL,
    `org_name` VARCHAR(128) NOT NULL,
    `org_type` VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT',
    `sort_no` INT NOT NULL DEFAULT 0,
    `leader_user_id` BIGINT UNSIGNED NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_org` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `org_id` BIGINT UNSIGNED NOT NULL,
    `is_primary` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否主部门',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_code` VARCHAR(64) NOT NULL,
    `role_name` VARCHAR(100) NOT NULL,
    `role_type` VARCHAR(20) NOT NULL DEFAULT 'BUSINESS',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `sort_no` INT NOT NULL DEFAULT 0,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `role_id` BIGINT UNSIGNED NOT NULL,
    `is_primary` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否主角色',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_menu` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `menu_code` VARCHAR(64) NOT NULL,
    `menu_name` VARCHAR(100) NOT NULL,
    `menu_type` VARCHAR(20) NOT NULL,
    `route_path` VARCHAR(255) NULL,
    `component_path` VARCHAR(255) NULL,
    `permission_code` VARCHAR(128) NULL,
    `icon` VARCHAR(64) NULL,
    `visible` TINYINT UNSIGNED NOT NULL DEFAULT 1,
    `keep_alive` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `external_url` VARCHAR(500) NULL,
    `sort_no` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `created_by` BIGINT UNSIGNED NULL COMMENT '创建人用户ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_api_resource` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `resource_name` VARCHAR(128) NOT NULL,
    `permission_code` VARCHAR(128) NOT NULL,
    `http_method` VARCHAR(16) NOT NULL,
    `path_pattern` VARCHAR(255) NOT NULL,
    `controller_method` VARCHAR(255) NULL,
    `auth_mode` VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `risk_level` VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_menu` (
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_id` BIGINT UNSIGNED NOT NULL,
    `menu_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_api` (
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_id` BIGINT UNSIGNED NOT NULL,
    `api_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_data_scope` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_id` BIGINT UNSIGNED NOT NULL,
    `resource_code` VARCHAR(128) NOT NULL,
    `scope_type` VARCHAR(20) NOT NULL,
    `combine_mode` VARCHAR(16) NOT NULL DEFAULT 'UNION',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_data_scope_org` (
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `rule_id` BIGINT UNSIGNED NOT NULL,
    `org_id` BIGINT UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_refresh_token` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `client_id` VARCHAR(64) NOT NULL,
    `session_id` VARCHAR(64) NOT NULL,
    `family_id` VARCHAR(64) NOT NULL,
    `token_hash` CHAR(64) NOT NULL,
    `device_id` VARCHAR(128) NULL,
    `user_agent` VARCHAR(500) NULL,
    `ip_address` VARCHAR(64) NULL,
    `issued_at` DATETIME(3) NOT NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `last_used_at` DATETIME(3) NULL,
    `revoked_at` DATETIME(3) NULL,
    `revoke_reason` VARCHAR(64) NULL,
    `replaced_by_id` BIGINT UNSIGNED NULL,
    `reuse_detected` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `version` INT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_password_history` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_login_log` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NULL,
    `username` VARCHAR(64) NULL,
    `login_type` VARCHAR(32) NOT NULL,
    `result` VARCHAR(20) NOT NULL,
    `failure_code` VARCHAR(64) NULL,
    `ip_address` VARCHAR(64) NULL,
    `user_agent` VARCHAR(500) NULL,
    `trace_id` VARCHAR(64) NULL,
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_operation_log` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `module_code` VARCHAR(64) NOT NULL,
    `operation_type` VARCHAR(64) NOT NULL,
    `operation_name` VARCHAR(128) NOT NULL,
    `operator_id` BIGINT UNSIGNED NULL,
    `operator_name` VARCHAR(100) NULL,
    `request_method` VARCHAR(16) NULL,
    `request_path` VARCHAR(500) NULL,
    `request_summary` JSON NULL,
    `result_summary` JSON NULL,
    `result_code` VARCHAR(64) NULL,
    `duration_ms` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address` VARCHAR(64) NULL,
    `trace_id` VARCHAR(64) NULL,
    `success` TINYINT UNSIGNED NOT NULL,
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wf_definition_ext` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `process_key` VARCHAR(128) NOT NULL,
    `process_name` VARCHAR(200) NOT NULL,
    `version` INT UNSIGNED NOT NULL,
    `deployment_id` VARCHAR(64) NOT NULL,
    `process_definition_id` VARCHAR(128) NOT NULL,
    `form_schema` JSON NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    `published_by` BIGINT UNSIGNED NULL,
    `published_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wf_node_config` (
    `id` BIGINT UNSIGNED NOT NULL,
    `definition_ext_id` BIGINT UNSIGNED NOT NULL,
    `activity_id` VARCHAR(128) NOT NULL,
    `node_name` VARCHAR(200) NOT NULL,
    `approval_mode` VARCHAR(20) NOT NULL DEFAULT 'ANY',
    `approval_ratio` DECIMAL(5,2) NULL,
    `assignee_type` VARCHAR(32) NOT NULL,
    `assignee_expression` VARCHAR(1000) NOT NULL,
    `reject_policy` VARCHAR(32) NOT NULL DEFAULT 'PREVIOUS',
    `reject_target_activity_id` VARCHAR(128) NULL,
    `allow_transfer` TINYINT UNSIGNED NOT NULL DEFAULT 1,
    `allow_delegate` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `timeout_minutes` INT UNSIGNED NULL,
    `config_json` JSON NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wf_instance_ext` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `process_instance_id` VARCHAR(64) NOT NULL,
    `process_definition_id` VARCHAR(128) NOT NULL,
    `business_type` VARCHAR(64) NOT NULL,
    `business_id` VARCHAR(128) NOT NULL,
    `title` VARCHAR(300) NOT NULL,
    `starter_user_id` BIGINT UNSIGNED NOT NULL,
    `starter_org_id` BIGINT UNSIGNED NULL,
    `current_activity_id` VARCHAR(128) NULL,
    `status` VARCHAR(20) NOT NULL,
    `started_at` DATETIME(3) NOT NULL,
    `finished_at` DATETIME(3) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wf_task_ext` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `task_id` VARCHAR(64) NOT NULL,
    `process_instance_id` VARCHAR(64) NOT NULL,
    `activity_id` VARCHAR(128) NOT NULL,
    `assignee_user_id` BIGINT UNSIGNED NULL,
    `original_assignee_user_id` BIGINT UNSIGNED NULL,
    `status` VARCHAR(20) NOT NULL,
    `approval_mode` VARCHAR(20) NOT NULL,
    `due_at` DATETIME(3) NULL,
    `claimed_at` DATETIME(3) NULL,
    `completed_at` DATETIME(3) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `delegator_user_id` BIGINT UNSIGNED NULL COMMENT '委派发起人（原审批人），非委派任务为 NULL',
    `delegation_state` VARCHAR(16) NULL COMMENT '委派状态：PENDING=已委派待办、RESOLVED=已办结交还、NULL=非委派',
    `activity_name` VARCHAR(255) NULL COMMENT '活动（节点）名称快照：任务创建期从引擎捕获，已办列表无需回查引擎即可展示可读节点名'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wf_approval_record` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `operation_id` VARCHAR(64) NOT NULL,
    `process_instance_id` VARCHAR(64) NOT NULL,
    `task_id` VARCHAR(64) NULL,
    `activity_id` VARCHAR(128) NULL,
    `action` VARCHAR(32) NOT NULL,
    `operator_user_id` BIGINT UNSIGNED NOT NULL,
    `from_user_id` BIGINT UNSIGNED NULL,
    `to_user_id` BIGINT UNSIGNED NULL,
    `opinion` VARCHAR(2000) NULL,
    `attachment_refs` JSON NULL,
    `snapshot` JSON NULL,
    `trace_id` VARCHAR(64) NULL,
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_type` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `dict_code` VARCHAR(64) NOT NULL,
    `dict_name` VARCHAR(100) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `sort_no` INT NOT NULL DEFAULT 0,
    `remark` VARCHAR(255) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_data` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `dict_type_code` VARCHAR(64) NOT NULL,
    `dict_label` VARCHAR(100) NOT NULL,
    `dict_value` VARCHAR(100) NOT NULL,
    `dict_sort` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `remark` VARCHAR(255) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_config` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `config_key` VARCHAR(64) NOT NULL,
    `config_name` VARCHAR(100) NOT NULL,
    `config_value` VARCHAR(500) NULL,
    `config_type` VARCHAR(20) NOT NULL DEFAULT 'STRING',
    `remark` VARCHAR(255) NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `default_value` VARCHAR(500) NULL COMMENT '参数默认值（工厂默认值，区别于当前生效值 config_value）',
    `editable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否可编辑：1=运行时可改，0=系统内置不可改'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_announcement` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `title` VARCHAR(200) NOT NULL,
    `content` MEDIUMTEXT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    `is_top` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `publish_at` DATETIME(3) NULL,
    `expire_at` DATETIME(3) NULL,
    `published_at` DATETIME(3) NULL,
    `offline_at` DATETIME(3) NULL,
    `publisher_id` BIGINT UNSIGNED NULL,
    `view_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `audience_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布时活跃用户快照（已读率分母）',
    `read_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读人数（markRead 成功时 +1）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_message` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `title` VARCHAR(200) NOT NULL,
    `content` MEDIUMTEXT NOT NULL,
    `msg_type` VARCHAR(20) NOT NULL DEFAULT 'NOTICE',
    `sender_id` BIGINT UNSIGNED NULL,
    `filter_role_ids` VARCHAR(500) NULL,
    `filter_org_ids` VARCHAR(500) NULL,
    `receiver_ids` VARCHAR(500) NULL,
    `total_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `read_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `sent_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_message_receipt` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `message_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `is_read` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `read_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_monitor_sample` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `cpu_usage` DECIMAL(5,2) NULL,
    `memory_usage` DECIMAL(5,2) NULL,
    `system_memory_usage` DECIMAL(5,2) NULL,
    `disk_usage` DECIMAL(5,2) NULL,
    `used_heap_bytes` BIGINT UNSIGNED NULL,
    `max_heap_bytes` BIGINT UNSIGNED NULL,
    `used_memory_bytes` BIGINT UNSIGNED NULL,
    `total_memory_bytes` BIGINT UNSIGNED NULL,
    `online_users` INT UNSIGNED NOT NULL DEFAULT 0,
    `active_sessions` INT UNSIGNED NOT NULL DEFAULT 0,
    `thread_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `sampled_at` DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wf_workflow_design` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `process_key` VARCHAR(128) NOT NULL,
    `process_name` VARCHAR(200) NOT NULL,
    `description` VARCHAR(500) NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `nodes_json` JSON NULL,
    `edges_json` JSON NULL,
    `form_schema` JSON NULL,
    `bpmn_xml` LONGTEXT NULL,
    `deployment_id` VARCHAR(64) NULL,
    `process_definition_id` VARCHAR(128) NULL,
    `published_by` BIGINT UNSIGNED NULL,
    `published_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_idempotency_record` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `api_scope` VARCHAR(128) NOT NULL,
    `user_id` BIGINT UNSIGNED NULL,
    `request_fingerprint` VARCHAR(64) NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    `response_snapshot` JSON NULL,
    `error_code` VARCHAR(64) NULL,
    `error_message` VARCHAR(500) NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_job` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `job_name` VARCHAR(128) NOT NULL,
    `job_group` VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    `task_key` VARCHAR(128) NOT NULL,
    `trigger_type` VARCHAR(16) NOT NULL,
    `cron_expression` VARCHAR(128) NULL,
    `interval_value` INT NULL,
    `interval_unit` VARCHAR(16) NULL,
    `one_time_at` DATETIME(3) NULL,
    `job_params` VARCHAR(2048) NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
    `scheduler_ref` VARCHAR(64) NULL,
    `last_fire_time` DATETIME(3) NULL,
    `next_fire_time` DATETIME(3) NULL,
    `total_runs` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `success_runs` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `failure_runs` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `last_status` VARCHAR(16) NULL,
    `last_error` VARCHAR(1024) NULL,
    `last_duration_ms` BIGINT UNSIGNED NULL,
    `remark` VARCHAR(255) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_job_log` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `job_id` BIGINT UNSIGNED NOT NULL,
    `job_name` VARCHAR(128) NULL,
    `task_key` VARCHAR(128) NULL,
    `trigger_type` VARCHAR(16) NULL,
    `fire_time` DATETIME(3) NOT NULL,
    `finished_time` DATETIME(3) NULL,
    `duration_ms` BIGINT UNSIGNED NULL,
    `status` VARCHAR(16) NOT NULL,
    `error_message` VARCHAR(1024) NULL,
    `trace_id` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_online_session` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `device_id` VARCHAR(128) NULL,
    `client_id` VARCHAR(64) NOT NULL DEFAULT 'web',
    `username` VARCHAR(64) NOT NULL,
    `display_name` VARCHAR(100) NULL,
    `login_ip` VARCHAR(64) NULL,
    `user_agent` VARCHAR(500) NULL,
    `login_at` DATETIME(3) NOT NULL,
    `last_active_at` DATETIME(3) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    `offline_reason` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_login_restriction` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `username` VARCHAR(64) NOT NULL,
    `display_name` VARCHAR(100) NULL,
    `reason` VARCHAR(255) NOT NULL,
    `duration_minutes` INT NOT NULL DEFAULT 0,
    `restricted_by` BIGINT UNSIGNED NULL,
    `restricted_by_name` VARCHAR(100) NULL,
    `restricted_at` DATETIME(3) NOT NULL,
    `expire_at` DATETIME(3) NULL,
    `released_at` DATETIME(3) NULL,
    `released_by` BIGINT UNSIGNED NULL,
    `release_type` VARCHAR(20) NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_file` (
    `id`            BIGINT UNSIGNED NOT NULL,
    `tenant_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `original_name` VARCHAR(255)    NOT NULL,
    `stored_name`   VARCHAR(255)    NOT NULL COMMENT '服务端生成的存储文件名（UUID.ext），不含目录',
    `storage_path`  VARCHAR(500)    NOT NULL COMMENT '上传时的绝对存储路径（记录用，读取以 baseDir+stored_name 为准）',
    `file_size`     BIGINT          NOT NULL,
    `content_type`  VARCHAR(128)    NULL,
    `source_module` VARCHAR(64)     NOT NULL COMMENT '来源功能模块，如 workflow/report/file-management',
    `biz_id`        VARCHAR(64)     NULL COMMENT '业务关联 ID（可选，如流程实例/报表 ID）',
    `uploader_id`   BIGINT UNSIGNED NULL,
    `uploader_name` VARCHAR(100)    NULL COMMENT '上传人姓名（冗余存储，用户删除后仍可读）',
    `download_count` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `version`       INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `content_hash` CHAR(64) NULL COMMENT '内容 SHA-256 十六进制（秒传去重锚点）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_post` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `post_code` VARCHAR(64) NOT NULL,
    `post_name` VARCHAR(100) NOT NULL,
    `post_sort` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `remark` VARCHAR(512) NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_post` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `post_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_sms_record` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `batch_no` VARCHAR(64) NOT NULL,
    `mobiles_masked` VARCHAR(1024) NOT NULL,
    `mobile_count` INT NOT NULL DEFAULT 0,
    `content_summary` VARCHAR(256) NOT NULL,
    `provider` VARCHAR(16) NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `error_message` VARCHAR(512) NULL,
    `operator_id` BIGINT UNSIGNED NULL,
    `operator_name` VARCHAR(64) NULL,
    `trace_id` VARCHAR(64) NULL,
    `sent_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_announcement_read` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `announcement_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `read_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_notification_template` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `template_code` VARCHAR(64) NOT NULL,
    `template_name` VARCHAR(100) NOT NULL,
    `channel` VARCHAR(16) NOT NULL,
    `title` VARCHAR(200) NULL,
    `content` TEXT NOT NULL,
    `msg_type` VARCHAR(16) NOT NULL DEFAULT 'NOTICE',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `remark` VARCHAR(255) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `deleted` INT NOT NULL DEFAULT 0,
    `created_by` BIGINT UNSIGNED NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT UNSIGNED NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_notification_record` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `channel` VARCHAR(16) NOT NULL,
    `template_code` VARCHAR(64) NULL,
    `msg_type` VARCHAR(32) NULL,
    `title_summary` VARCHAR(256) NOT NULL,
    `content_summary` VARCHAR(512) NOT NULL,
    `receiver_summary` VARCHAR(512) NOT NULL,
    `receiver_count` INT NOT NULL DEFAULT 0,
    `delivered` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(16) NOT NULL,
    `error_message` VARCHAR(512) NULL,
    `operator_id` BIGINT UNSIGNED NULL,
    `operator_name` VARCHAR(64) NULL,
    `trace_id` VARCHAR(64) NULL,
    `sent_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_notification_preference` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `channel` VARCHAR(16) NOT NULL,
    `msg_type` VARCHAR(32) NOT NULL DEFAULT 'NOTICE',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_notification_quiet_hours` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `start_time` TIME NOT NULL,
    `end_time` TIME NOT NULL,
    `alert_breakthrough` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_notification_digest` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `channel` VARCHAR(16) NOT NULL,
    `msg_type` VARCHAR(32) NOT NULL,
    `item_count` INT NOT NULL DEFAULT 0,
    `items_json` TEXT NULL,
    `first_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `last_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_mask_strategy` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `strategy_code` VARCHAR(64) NOT NULL,
    `strategy_name` VARCHAR(128) NOT NULL,
    `field_key` VARCHAR(128) NOT NULL,
    `sensitive_type` VARCHAR(32) NOT NULL,
    `apply_scope` VARCHAR(16) NOT NULL DEFAULT 'ROLE_EXEMPT',
    `exempt_roles` VARCHAR(512) NULL,
    `priority` INT NOT NULL DEFAULT 0,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `remark` VARCHAR(255) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_form_definition` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `form_code` VARCHAR(64) NOT NULL,
    `form_name` VARCHAR(128) NOT NULL,
    `schema_json` JSON NOT NULL,
    `target_type` VARCHAR(32) NOT NULL DEFAULT 'NONE',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `remark` VARCHAR(255) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_openapi_client` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `client_id` VARCHAR(64) NOT NULL,
    `client_name` VARCHAR(128) NOT NULL,
    `client_secret_hash` VARCHAR(128) NOT NULL,
    `scopes` VARCHAR(512) NOT NULL DEFAULT 'read',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    `secret_rotated_at` DATETIME(3) NULL,
    `expired_at` DATETIME(3) NULL,
    `remark` VARCHAR(255) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `redirect_uris` VARCHAR(1024) NULL COMMENT '授权码回调地址白名单（逗号分隔，空白=不约束）',
    `pkce_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制 PKCE（授权码流程）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_tenant` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `tenant_code` VARCHAR(64) NOT NULL,
    `tenant_name` VARCHAR(128) NOT NULL,
    `contact_name` VARCHAR(64) NULL,
    `contact_phone` VARCHAR(32) NULL,
    `contact_email` VARCHAR(128) NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    `expire_at` DATETIME(3) NULL,
    `remark` VARCHAR(255) NULL,
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` VARCHAR(64) NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_form_submission` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `form_definition_id` BIGINT UNSIGNED NOT NULL,
    `form_code` VARCHAR(64) NOT NULL,
    `submitted_by` VARCHAR(64) NULL,
    `data_json` JSON NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_column_permission` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_id` BIGINT UNSIGNED NOT NULL,
    `resource_code` VARCHAR(128) NOT NULL,
    `field` VARCHAR(128) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_oauth2_token` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `jti` VARCHAR(64) NOT NULL,
    `client_id` VARCHAR(128) NOT NULL,
    `user_id` BIGINT NULL,
    `username` VARCHAR(128) NULL,
    `scope` VARCHAR(512) NULL,
    `token_type` VARCHAR(20) NOT NULL,
    `issued_at` DATETIME(3) NOT NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `version` BIGINT NOT NULL DEFAULT 0,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_oauth2_consent` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id` BIGINT NOT NULL,
    `client_id` VARCHAR(64) NOT NULL,
    `scopes` VARCHAR(512) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    `expires_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cms_category` (
    `id`           BIGINT        NOT NULL,
    `tenant_id`    BIGINT        NOT NULL DEFAULT 0,
    `parent_id`    BIGINT        NULL,
    `category_code` VARCHAR(64)  NOT NULL,
    `category_name` VARCHAR(128) NOT NULL,
    `description`  VARCHAR(512)  NULL,
    `sort_no`      INT           NOT NULL DEFAULT 0,
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`      INT           NOT NULL DEFAULT 0,
    `deleted`      INT           NOT NULL DEFAULT 0,
    `created_by`   VARCHAR(64)   NULL,
    `created_at`   TIMESTAMP     NULL,
    `updated_by`   VARCHAR(64)   NULL,
    `updated_at`   TIMESTAMP     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cms_article` (
    `id`           BIGINT        NOT NULL,
    `tenant_id`    BIGINT        NOT NULL DEFAULT 0,
    `category_id`  BIGINT        NOT NULL,
    `title`        VARCHAR(255)  NOT NULL,
    `summary`      VARCHAR(512)  NULL,
    `content`      TEXT          NULL,
    `author`       VARCHAR(64)   NULL,
    `cover_url`    VARCHAR(512)  NULL,
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    `view_count`   BIGINT        NOT NULL DEFAULT 0,
    `published_at` TIMESTAMP     NULL,
    `offline_at`   TIMESTAMP     NULL,
    `version`      INT           NOT NULL DEFAULT 0,
    `deleted`      INT           NOT NULL DEFAULT 0,
    `created_by`   VARCHAR(64)   NULL,
    `created_at`   TIMESTAMP     NULL,
    `updated_by`   VARCHAR(64)   NULL,
    `updated_at`   TIMESTAMP     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `im_conversation` (
    `id`                 BIGINT        NOT NULL,
    `tenant_id`          BIGINT        NOT NULL DEFAULT 0,
    `conversation_type`  VARCHAR(20)   NOT NULL,
    `title`              VARCHAR(255)  NULL,
    `owner_id`           BIGINT        NULL,
    `status`             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`            INT           NOT NULL DEFAULT 0,
    `deleted`            INT           NOT NULL DEFAULT 0,
    `created_by`         VARCHAR(64)   NULL,
    `created_at`         TIMESTAMP     NULL,
    `updated_by`         VARCHAR(64)   NULL,
    `updated_at`         TIMESTAMP     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `im_conversation_member` (
    `id`                   BIGINT        NOT NULL,
    `tenant_id`            BIGINT        NOT NULL DEFAULT 0,
    `conversation_id`      BIGINT        NOT NULL,
    `user_id`              BIGINT        NOT NULL,
    `member_role`          VARCHAR(20)   NOT NULL DEFAULT 'MEMBER',
    `last_read_message_id` BIGINT        NULL,
    `status`               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`              INT           NOT NULL DEFAULT 0,
    `deleted`              INT           NOT NULL DEFAULT 0,
    `created_at`           TIMESTAMP     NULL,
    `updated_at`           TIMESTAMP     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `im_message` (
    `id`              BIGINT        NOT NULL,
    `tenant_id`       BIGINT        NOT NULL DEFAULT 0,
    `conversation_id` BIGINT        NOT NULL,
    `sender_id`       BIGINT        NOT NULL,
    `content`         TEXT          NULL,
    `msg_type`        VARCHAR(20)   NOT NULL DEFAULT 'TEXT',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'SENT',
    `reply_to_id`     BIGINT        NULL,
    `version`         INT           NOT NULL DEFAULT 0,
    `deleted`         INT           NOT NULL DEFAULT 0,
    `created_by`      VARCHAR(64)   NULL,
    `created_at`      TIMESTAMP     NULL,
    `updated_by`      VARCHAR(64)   NULL,
    `updated_at`      TIMESTAMP     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `schema_baseline` (
    `baseline_version` VARCHAR(32) NOT NULL,
    `source_count` INT NOT NULL DEFAULT 0,
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_alert_rule` (
    `id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `rule_name` VARCHAR(128) NOT NULL,
    `metric_key` VARCHAR(32) NOT NULL,
    `comparator` VARCHAR(8) NOT NULL,
    `threshold` DECIMAL(12, 4) NOT NULL,
    `severity` VARCHAR(16) NOT NULL DEFAULT 'WARNING',
    `for_duration_sec` INT NOT NULL DEFAULT 0,
    `consecutive_count` INT NOT NULL DEFAULT 1,
    `channel_type` VARCHAR(16) NOT NULL DEFAULT 'INBOX',
    `channel_config` VARCHAR(1024) NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 0,
    `remark` VARCHAR(255) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` BIGINT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by` BIGINT NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dashboard_template` (
    `id`             BIGINT        NOT NULL,
    `tenant_id`      BIGINT        NOT NULL DEFAULT 0,
    `template_code`  VARCHAR(64)   NOT NULL,
    `template_name`  VARCHAR(128)  NOT NULL,
    `description`    VARCHAR(512)  NULL,
    `layout_json`    TEXT          NULL,
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    `view_count`     BIGINT        NOT NULL DEFAULT 0,
    `version`        INT           NOT NULL DEFAULT 0,
    `deleted`        INT           NOT NULL DEFAULT 0,
    `created_by`     VARCHAR(64)   NULL,
    `created_at`     TIMESTAMP     NULL,
    `updated_by`     VARCHAR(64)   NULL,
    `updated_at`     TIMESTAMP     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- 索引（独立 CREATE INDEX） ----------

CREATE INDEX `idx_user_revoked` ON `sys_refresh_token` (`user_id`, `revoked_at`);
CREATE INDEX `idx_tenant_status_deleted` ON `wf_workflow_design` (tenant_id, status, deleted);
CREATE INDEX `idx_content_hash` ON `sys_file` (`content_hash`);
CREATE INDEX `idx_cms_category_tenant_parent` ON `cms_category` (`tenant_id`, `parent_id`);
CREATE INDEX `idx_cms_category_code` ON `cms_category` (`tenant_id`, `category_code`);
CREATE INDEX `idx_cms_category_status` ON `cms_category` (`tenant_id`, `status`);
CREATE INDEX `idx_cms_article_tenant_category` ON `cms_article` (`tenant_id`, `category_id`);
CREATE INDEX `idx_cms_article_status` ON `cms_article` (`tenant_id`, `status`);
CREATE INDEX `idx_cms_article_title` ON `cms_article` (`tenant_id`, `title`);
CREATE INDEX `idx_im_conversation_tenant_type` ON `im_conversation` (`tenant_id`, `conversation_type`);
CREATE INDEX `idx_im_conversation_status` ON `im_conversation` (`tenant_id`, `status`);
CREATE INDEX `idx_im_member_tenant_conv` ON `im_conversation_member` (`tenant_id`, `conversation_id`);
CREATE INDEX `idx_im_member_tenant_user` ON `im_conversation_member` (`tenant_id`, `user_id`);
CREATE INDEX `idx_im_message_tenant_conv` ON `im_message` (`tenant_id`, `conversation_id`);
CREATE INDEX `idx_im_message_created` ON `im_message` (`tenant_id`, `conversation_id`, `created_at`);
CREATE INDEX `idx_dashboard_template_tenant_code` ON `sys_dashboard_template` (`tenant_id`, `template_code`);
CREATE INDEX `idx_dashboard_template_status` ON `sys_dashboard_template` (`tenant_id`, `status`);
CREATE INDEX `idx_dashboard_template_name` ON `sys_dashboard_template` (`tenant_id`, `template_name`);
CREATE INDEX `idx_operator_occurred` ON `sys_operation_log` (`operator_id`, `occurred_at`, `id`);
CREATE INDEX `idx_occurred_id` ON `sys_operation_log` (`occurred_at`, `id`);
