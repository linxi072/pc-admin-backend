-- =====================================================================================
--  java-admin-framework 全量数据库执行文件（幂等 / 可重复执行）
-- =====================================================================================
--  用途：在**空库**上一次性建好全部业务表与种子数据；也可在**已建库**上重复执行以补齐
--        缺失的表 / 列 / 索引 / 种子数据，全程不报错、不破坏既有数据。
--
--  与 Flyway 迁移脚本的关系：
--    Flyway 仍为应用启动时的默认路径（db/migration/V1 ~ V9），本文件是**独立等价副本**，
--    用于「手工初始化 / 快速重建 / 校验迁移结果」三场景。二者表结构严格一致。
--
--  适用：MySQL 8.0+（使用 utf8mb4_0900_ai_ci 排序规则、CTE / 窗口函数、JSON 类型）
--  执行：mysql -u root -p < scaffold_full_init.sql
--        或在 Navicat / DataGrip 中整份运行；无需手工预处理，可反复执行。
--
--  幂等实现要点：
--    1) 建表统一使用 CREATE TABLE IF NOT EXISTS
--    2) 种子数据统一使用 INSERT IGNORE（依赖主键 / 唯一键去重）
--    3) 变更类 DDL（加列 / 加索引 / 删表）先查 information_schema 再决定是否执行，
--       通过 PREPARE / EXECUTE 动态执行 —— 因此**无需 DELIMITER**，兼容性最好
--    4) 依赖旧表的数据回填，先判断旧表是否存在，不存在则跳过
--
--  执行结果自查（末尾会打印各表数量）：
--    SELECT 'sys_user' t, COUNT(*) c FROM sys_user
--    UNION ALL SELECT 'sys_org', COUNT(*) FROM sys_org
--    UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role
--    UNION ALL SELECT 'sys_menu', COUNT(*) FROM sys_menu
--    UNION ALL SELECT 'sys_config', COUNT(*) FROM sys_config
--    UNION ALL SELECT 'sys_dict_type', COUNT(*) FROM sys_dict_type
--    UNION ALL SELECT 'sys_dict_data', COUNT(*) FROM sys_dict_data;
-- =====================================================================================


-- =====================================================================================
--  0. 库与字符集
-- =====================================================================================
CREATE DATABASE IF NOT EXISTS `scaffold`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `scaffold`;

-- 说明：不使用 sql_mode 放宽或关闭外键检查，避免掩盖真实问题。
--      本文件全部对象均为 InnoDB，DDL 顺序为「先建表后插数据」，无外键依赖。


-- =====================================================================================
--  1. RBAC 核心表（对应 V1__baseline.sql）
-- =====================================================================================

-- 1.1 用户
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`                 BIGINT UNSIGNED NOT NULL,
    `tenant_id`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `username`           VARCHAR(64)     NOT NULL,
    `password_hash`      VARCHAR(255)    NOT NULL,
    `display_name`       VARCHAR(100)    NOT NULL,
    `mobile`             VARCHAR(32)     NULL,
    `email`              VARCHAR(128)    NULL,
    `primary_org_id`     BIGINT UNSIGNED NULL,
    -- 单一角色ID（V6 收敛后不再允许多角色）。为兼容存量数据暂不建外键与 NOT NULL，
    -- 「角色必填」由应用层 CreateUserRequest(@NotNull) 与 UserService 强制保证。
    `role_id`            BIGINT UNSIGNED NULL COMMENT '单一角色ID(收敛后不再允许多角色)',
    -- 单一部门ID（V6 收敛后不再允许多部门）
    `org_id`             BIGINT UNSIGNED NULL COMMENT '单一部门ID(收敛后不再允许多部门)',
    `status`             VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `failed_login_count` INT UNSIGNED    NOT NULL DEFAULT 0,
    `locked_until`       DATETIME(3)     NULL,
    `password_changed_at` DATETIME(3)    NULL,
    `password_expired`   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `token_version`      INT UNSIGNED    NOT NULL DEFAULT 1,
    `mfa_enabled`        TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `last_login_at`      DATETIME(3)     NULL,
    `version`            INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`            TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`         BIGINT UNSIGNED NULL,
    `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`         BIGINT UNSIGNED NULL,
    `updated_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`),
    UNIQUE KEY `uk_tenant_mobile` (`tenant_id`, `mobile`),
    KEY `idx_org_status` (`tenant_id`, `primary_org_id`, `status`),
    KEY `idx_updated_at` (`updated_at`),
    -- V6 新增的筛选索引（重复执行时下方会先判存再建）
    KEY `idx_tenant_role` (`tenant_id`, `role_id`),
    KEY `idx_tenant_org` (`tenant_id`, `org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 1.2 组织机构（部门）
CREATE TABLE IF NOT EXISTS `sys_org` (
    `id`              BIGINT UNSIGNED NOT NULL,
    `tenant_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `parent_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ancestors`       VARCHAR(1000)    NOT NULL DEFAULT '0' COMMENT '物化路径，逗号分隔，根为0',
    `org_code`        VARCHAR(64)     NOT NULL,
    `org_name`        VARCHAR(128)    NOT NULL,
    `org_type`        VARCHAR(32)     NOT NULL DEFAULT 'DEPARTMENT',
    `sort_no`         INT             NOT NULL DEFAULT 0,
    `leader_user_id`  BIGINT UNSIGNED NULL,
    `status`          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `version`         INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`         TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`      BIGINT UNSIGNED NULL,
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`      BIGINT UNSIGNED NULL,
    `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_org_code` (`tenant_id`, `org_code`),
    KEY `idx_parent` (`tenant_id`, `parent_id`, `sort_no`),
    KEY `idx_ancestors` (`tenant_id`, `ancestors`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 1.3 角色
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT UNSIGNED NOT NULL,
    `tenant_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_code`   VARCHAR(64)     NOT NULL,
    `role_name`   VARCHAR(100)    NOT NULL,
    `role_type`   VARCHAR(20)     NOT NULL DEFAULT 'BUSINESS',
    `status`      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `sort_no`     INT             NOT NULL DEFAULT 0,
    `version`     INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`     TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`  BIGINT UNSIGNED NULL,
    `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`  BIGINT UNSIGNED NULL,
    `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_role_code` (`tenant_id`, `role_code`),
    KEY `idx_status_sort` (`tenant_id`, `status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 1.4 菜单
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id`              BIGINT UNSIGNED NOT NULL,
    `tenant_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `parent_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `menu_code`       VARCHAR(64)     NOT NULL,
    `menu_name`       VARCHAR(100)    NOT NULL,
    `menu_type`       VARCHAR(20)     NOT NULL,
    `route_path`      VARCHAR(255)    NULL,
    `component_path`  VARCHAR(255)    NULL,
    `permission_code` VARCHAR(128)    NULL,
    `icon`            VARCHAR(64)     NULL,
    `visible`         TINYINT UNSIGNED NOT NULL DEFAULT 1,
    `keep_alive`      TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `external_url`    VARCHAR(500)    NULL,
    `sort_no`         INT             NOT NULL DEFAULT 0,
    `status`          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `deleted`         TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_menu_code` (`tenant_id`, `menu_code`),
    KEY `idx_parent_sort` (`tenant_id`, `parent_id`, `sort_no`),
    KEY `idx_permission` (`tenant_id`, `permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 1.5 接口资源
CREATE TABLE IF NOT EXISTS `sys_api_resource` (
    `id`                BIGINT UNSIGNED NOT NULL,
    `tenant_id`         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `resource_name`     VARCHAR(128)    NOT NULL,
    `permission_code`   VARCHAR(128)    NOT NULL,
    `http_method`       VARCHAR(16)     NOT NULL,
    `path_pattern`      VARCHAR(255)    NOT NULL,
    `controller_method` VARCHAR(255)    NULL,
    `auth_mode`         VARCHAR(20)     NOT NULL DEFAULT 'REQUIRED',
    `status`            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `risk_level`        VARCHAR(16)     NOT NULL DEFAULT 'NORMAL',
    `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_method_path` (`tenant_id`, `http_method`, `path_pattern`),
    KEY `idx_permission` (`tenant_id`, `permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 1.6 角色-菜单 / 角色-接口资源（多对多，保持不变）
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `tenant_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_id`    BIGINT UNSIGNED NOT NULL,
    `menu_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`tenant_id`, `role_id`, `menu_id`),
    KEY `idx_menu_role` (`tenant_id`, `menu_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_role_api` (
    `tenant_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_id`    BIGINT UNSIGNED NOT NULL,
    `api_id`     BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`tenant_id`, `role_id`, `api_id`),
    KEY `idx_api_role` (`tenant_id`, `api_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 1.7 数据范围规则（单角色模型下，每角色每资源一条规则）
CREATE TABLE IF NOT EXISTS `sys_role_data_scope` (
    `id`            BIGINT UNSIGNED NOT NULL,
    `tenant_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `role_id`       BIGINT UNSIGNED NOT NULL,
    `resource_code` VARCHAR(128)    NOT NULL,
    `scope_type`    VARCHAR(20)     NOT NULL COMMENT 'ALL/SELF/DEPT/DEPT_AND_CHILD/CUSTOM',
    `combine_mode`  VARCHAR(16)     NOT NULL DEFAULT 'UNION',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_resource` (`tenant_id`, `role_id`, `resource_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_role_data_scope_org` (
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `rule_id`   BIGINT UNSIGNED NOT NULL,
    `org_id`    BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`tenant_id`, `rule_id`, `org_id`),
    KEY `idx_org_rule` (`tenant_id`, `org_id`, `rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 注意：V6 收敛后的 sys_user_role / sys_user_org **不再创建**，见第 3 节兼容处理。


-- =====================================================================================
--  2. 安全与审计表（对应 V2__security.sql）
-- =====================================================================================

-- 2.1 刷新令牌（监控模块的「在线会话」口径来源：未吊销且未过期）
CREATE TABLE IF NOT EXISTS `sys_refresh_token` (
    `id`              BIGINT UNSIGNED NOT NULL,
    `tenant_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id`         BIGINT UNSIGNED NOT NULL,
    `client_id`       VARCHAR(64)     NOT NULL,
    `session_id`      VARCHAR(64)     NOT NULL,
    `family_id`       VARCHAR(64)     NOT NULL,
    `token_hash`      CHAR(64)        NOT NULL,
    `device_id`       VARCHAR(128)    NULL,
    `user_agent`      VARCHAR(500)    NULL,
    `ip_address`      VARCHAR(64)     NULL,
    `issued_at`       DATETIME(3)     NOT NULL,
    `expires_at`      DATETIME(3)     NOT NULL,
    `last_used_at`    DATETIME(3)     NULL,
    `revoked_at`      DATETIME(3)     NULL,
    `revoke_reason`   VARCHAR(64)     NULL,
    `replaced_by_id`  BIGINT UNSIGNED NULL,
    `reuse_detected`  TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `version`         INT UNSIGNED    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token_hash` (`token_hash`),
    KEY `idx_user_expiry` (`tenant_id`, `user_id`, `expires_at`),
    KEY `idx_family` (`tenant_id`, `family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2.2 口令历史
CREATE TABLE IF NOT EXISTS `sys_password_history` (
    `id`            BIGINT UNSIGNED NOT NULL,
    `tenant_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id`       BIGINT UNSIGNED NOT NULL,
    `password_hash` VARCHAR(255)    NOT NULL,
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`tenant_id`, `user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2.3 登录日志
CREATE TABLE IF NOT EXISTS `sys_login_log` (
    `id`           BIGINT UNSIGNED NOT NULL,
    `tenant_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `user_id`      BIGINT UNSIGNED NULL,
    `username`     VARCHAR(64)     NULL,
    `login_type`   VARCHAR(32)     NOT NULL,
    `result`       VARCHAR(20)     NOT NULL,
    `failure_code` VARCHAR(64)     NULL,
    `ip_address`   VARCHAR(64)     NULL,
    `user_agent`   VARCHAR(500)    NULL,
    `trace_id`     VARCHAR(64)     NULL,
    `occurred_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`tenant_id`, `user_id`, `occurred_at`),
    KEY `idx_ip_time` (`ip_address`, `occurred_at`),
    KEY `idx_result_time` (`result`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2.4 操作日志（监控模块的「异常日志」来源：success=0）
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id`               BIGINT UNSIGNED NOT NULL,
    `tenant_id`        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `module_code`      VARCHAR(64)     NOT NULL,
    `operation_type`   VARCHAR(64)     NOT NULL,
    `operation_name`   VARCHAR(128)    NOT NULL,
    `operator_id`      BIGINT UNSIGNED NULL,
    `operator_name`    VARCHAR(100)    NULL,
    `request_method`   VARCHAR(16)     NULL,
    `request_path`     VARCHAR(500)    NULL,
    `request_summary`  JSON            NULL,
    `result_summary`   JSON            NULL,
    `result_code`      VARCHAR(64)     NULL,
    `duration_ms`      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`       VARCHAR(64)     NULL,
    `trace_id`         VARCHAR(64)     NULL,
    `success`          TINYINT UNSIGNED NOT NULL,
    `occurred_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_module_time` (`tenant_id`, `module_code`, `occurred_at`),
    KEY `idx_operator_time` (`tenant_id`, `operator_id`, `occurred_at`),
    KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================================================
--  3. 用户单角色/单部门收敛 —— 兼容既有库（对应 V6__user_single_role_org.sql）
-- =====================================================================================
--  空库执行时：第 1 节已直接建出含 role_id / org_id 的 sys_user，本节全部跳过（安全空转）。
--  旧库执行时：若尚存 V1 的 sys_user_role / sys_user_org，则先加列 → 回填 → 删表。
--
--  重要：回填时多角色取「数据范围最小(最严格)」的一条，避免收敛后权限被放大（越权）。
--        优先级 CUSTOM > DEPT_AND_CHILD > DEPT/SELF > ALL。

-- 3.1 补列：role_id（若 sys_user 已存在但缺该列则补上）
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `role_id` BIGINT UNSIGNED NULL COMMENT ''单一角色ID(收敛后不再允许多角色)'' AFTER `primary_org_id`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.2 补列：org_id
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `org_id` BIGINT UNSIGNED NULL COMMENT ''单一部门ID(收敛后不再允许多部门)'' AFTER `role_id`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.3 补索引：idx_tenant_role
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_role` (`tenant_id`, `role_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.4 补索引：idx_tenant_org
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_org` (`tenant_id`, `org_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.5 角色回填（仅当旧关联表 sys_user_role 存在时执行；已收敛的库自动跳过）
--     多角色时按数据范围最小(最严格)挑选：CUSTOM > DEPT_AND_CHILD > DEPT/SELF > ALL
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'UPDATE sys_user u
         LEFT JOIN (
             SELECT ur.user_id, ur.role_id,
                    ROW_NUMBER() OVER (
                        PARTITION BY ur.user_id
                        ORDER BY FIELD(
                            COALESCE((
                                SELECT MIN(r.scope_type)
                                FROM sys_role_data_scope r
                                WHERE r.role_id = ur.role_id
                                  AND r.resource_code = ''system:user''
                            ), ''ALL''),
                            ''CUSTOM'', ''DEPT_AND_CHILD'', ''DEPT'', ''SELF'', ''ALL''
                        ),
                        ur.role_id
                    ) AS rn
             FROM sys_user_role ur
         ) picked ON picked.user_id = u.id AND picked.rn = 1
         SET u.role_id = picked.role_id
         WHERE u.role_id IS NULL',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.6 部门回填（仅当旧关联表 sys_user_org 存在时执行）
--     多部门时取 is_primary 优先，其次最小 org_id，保证确定性。
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'UPDATE sys_user u
         LEFT JOIN (
             SELECT uo.user_id, uo.org_id,
                    ROW_NUMBER() OVER (
                        PARTITION BY uo.user_id
                        ORDER BY uo.is_primary DESC, uo.org_id ASC
                    ) AS rn
             FROM sys_user_org uo
         ) picked ON picked.user_id = u.id AND picked.rn = 1
         SET u.org_id = picked.org_id
         WHERE u.org_id IS NULL',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_org'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.7 兼容期：primary_org_id 与 org_id 对齐（供尚未切换的旧代码读取）
UPDATE `sys_user` SET `primary_org_id` = `org_id` WHERE `org_id` IS NOT NULL AND `primary_org_id` <=> `org_id` = 0;

-- 3.8 删除已废弃的多对多关联表（收敛后不再有查询/写入方引用）
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_user_org`;


-- =====================================================================================
--  4. 工作流扩展表（对应 V3__workflow.sql）
--     Flowable 自身的 ACT_* 表由引擎自动创建，不在本文件范围内
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `wf_definition_ext` (
    `id`                       BIGINT UNSIGNED NOT NULL,
    `tenant_id`                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `process_key`              VARCHAR(128)    NOT NULL,
    `process_name`             VARCHAR(200)    NOT NULL,
    `version`                  INT UNSIGNED    NOT NULL,
    `deployment_id`            VARCHAR(64)     NOT NULL,
    `process_definition_id`    VARCHAR(128)    NOT NULL,
    `form_schema`              JSON            NULL,
    `status`                   VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    `published_by`             BIGINT UNSIGNED NULL,
    `published_at`             DATETIME(3)     NULL,
    `created_at`               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_version` (`tenant_id`, `process_key`, `version`),
    UNIQUE KEY `uk_engine_definition` (`process_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `wf_node_config` (
    `id`                           BIGINT UNSIGNED NOT NULL,
    `definition_ext_id`            BIGINT UNSIGNED NOT NULL,
    `activity_id`                  VARCHAR(128)    NOT NULL,
    `node_name`                    VARCHAR(200)    NOT NULL,
    `approval_mode`                VARCHAR(20)     NOT NULL DEFAULT 'ANY',
    `approval_ratio`               DECIMAL(5,2)    NULL,
    `assignee_type`                VARCHAR(32)     NOT NULL,
    `assignee_expression`          VARCHAR(1000)   NOT NULL,
    `reject_policy`                VARCHAR(32)     NOT NULL DEFAULT 'PREVIOUS',
    `reject_target_activity_id`    VARCHAR(128)    NULL,
    `allow_transfer`               TINYINT UNSIGNED NOT NULL DEFAULT 1,
    `allow_delegate`               TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `timeout_minutes`              INT UNSIGNED    NULL,
    `config_json`                  JSON            NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_definition_activity` (`definition_ext_id`, `activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `wf_instance_ext` (
    `id`                       BIGINT UNSIGNED NOT NULL,
    `tenant_id`                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `process_instance_id`      VARCHAR(64)     NOT NULL,
    `process_definition_id`    VARCHAR(128)    NOT NULL,
    `business_type`            VARCHAR(64)     NOT NULL,
    `business_id`              VARCHAR(128)    NOT NULL,
    `title`                    VARCHAR(300)    NOT NULL,
    `starter_user_id`          BIGINT UNSIGNED NOT NULL,
    `starter_org_id`           BIGINT UNSIGNED NULL,
    `current_activity_id`      VARCHAR(128)    NULL,
    `status`                   VARCHAR(20)     NOT NULL,
    `started_at`               DATETIME(3)     NOT NULL,
    `finished_at`              DATETIME(3)     NULL,
    `version`                  INT UNSIGNED    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_instance` (`process_instance_id`),
    UNIQUE KEY `uk_business_instance` (`tenant_id`, `business_type`, `business_id`),
    KEY `idx_starter_status` (`tenant_id`, `starter_user_id`, `status`),
    KEY `idx_status_started` (`tenant_id`, `status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `wf_task_ext` (
    `id`                       BIGINT UNSIGNED NOT NULL,
    `tenant_id`                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `task_id`                  VARCHAR(64)     NOT NULL,
    `process_instance_id`      VARCHAR(64)     NOT NULL,
    `activity_id`              VARCHAR(128)    NOT NULL,
    `assignee_user_id`         BIGINT UNSIGNED NULL,
    `original_assignee_user_id` BIGINT UNSIGNED NULL,
    `status`                   VARCHAR(20)     NOT NULL,
    `approval_mode`            VARCHAR(20)     NOT NULL,
    `due_at`                   DATETIME(3)     NULL,
    `claimed_at`               DATETIME(3)     NULL,
    `completed_at`             DATETIME(3)     NULL,
    `version`                  INT UNSIGNED    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_assignee_status` (`tenant_id`, `assignee_user_id`, `status`),
    KEY `idx_instance_activity` (`process_instance_id`, `activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `wf_approval_record` (
    `id`                    BIGINT UNSIGNED NOT NULL,
    `tenant_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `operation_id`          VARCHAR(64)     NOT NULL,
    `process_instance_id`   VARCHAR(64)     NOT NULL,
    `task_id`               VARCHAR(64)     NULL,
    `activity_id`           VARCHAR(128)    NULL,
    `action`                VARCHAR(32)     NOT NULL,
    `operator_user_id`      BIGINT UNSIGNED NOT NULL,
    `from_user_id`          BIGINT UNSIGNED NULL,
    `to_user_id`            BIGINT UNSIGNED NULL,
    `opinion`               VARCHAR(2000)   NULL,
    `attachment_refs`       JSON            NULL,
    `snapshot`              JSON            NULL,
    `trace_id`              VARCHAR(64)     NULL,
    `occurred_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_operation_id` (`operation_id`),
    KEY `idx_instance_time` (`tenant_id`, `process_instance_id`, `occurred_at`),
    KEY `idx_task_time` (`task_id`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================================================
--  5. 字典管理与系统变量（对应 V5__system_modules.sql）
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `id`          BIGINT UNSIGNED NOT NULL,
    `tenant_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `dict_code`   VARCHAR(64)     NOT NULL,
    `dict_name`   VARCHAR(100)    NOT NULL,
    `status`      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `sort_no`     INT             NOT NULL DEFAULT 0,
    `remark`      VARCHAR(255)    NULL,
    `version`     INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`     TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`  BIGINT UNSIGNED NULL,
    `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`  BIGINT UNSIGNED NULL,
    `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_dict_code` (`tenant_id`, `dict_code`),
    KEY `idx_status_sort` (`tenant_id`, `status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
    `id`              BIGINT UNSIGNED NOT NULL,
    `tenant_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `dict_type_code`  VARCHAR(64)     NOT NULL,
    `dict_label`      VARCHAR(100)    NOT NULL,
    `dict_value`      VARCHAR(100)    NOT NULL,
    `dict_sort`       INT             NOT NULL DEFAULT 0,
    `status`          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `remark`          VARCHAR(255)    NULL,
    `version`         INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`         TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`      BIGINT UNSIGNED NULL,
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`      BIGINT UNSIGNED NULL,
    `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_type_value` (`tenant_id`, `dict_type_code`, `dict_value`),
    KEY `idx_type_sort` (`tenant_id`, `dict_type_code`, `dict_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`           BIGINT UNSIGNED NOT NULL,
    `tenant_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `config_key`   VARCHAR(64)     NOT NULL,
    `config_name`  VARCHAR(100)    NOT NULL,
    `config_value` VARCHAR(500)    NULL,
    `config_type`  VARCHAR(20)     NOT NULL DEFAULT 'STRING',
    `remark`       VARCHAR(255)    NULL,
    `status`       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    `version`      INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`      TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`   BIGINT UNSIGNED NULL,
    `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`   BIGINT UNSIGNED NULL,
    `updated_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_config_key` (`tenant_id`, `config_key`),
    KEY `idx_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================================================
--  6. 系统公告（对应 V7__announcement.sql）
--     状态机：DRAFT(草稿) -> PUBLISHED(已发布) -> OFFLINE(已下线)
--     定时生效由查询侧 isEffective(now) 判定，过期自动下线由 AnnouncementExpiryJob 负责。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `sys_announcement` (
    `id`           BIGINT UNSIGNED NOT NULL,
    `tenant_id`    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `title`        VARCHAR(200)    NOT NULL,
    `content`      MEDIUMTEXT      NOT NULL,
    `status`       VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
    `is_top`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '置顶',
    `publish_at`   DATETIME(3)     NULL COMMENT '定时生效时间(空=发布即生效)',
    `expire_at`    DATETIME(3)     NULL COMMENT '有效期截止(空=长期有效)',
    `published_at` DATETIME(3)     NULL COMMENT '实际发布时间',
    `offline_at`   DATETIME(3)     NULL COMMENT '下线时间',
    `publisher_id` BIGINT UNSIGNED NULL,
    `view_count`   INT UNSIGNED    NOT NULL DEFAULT 0,
    `version`      INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`      TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`   BIGINT UNSIGNED NULL,
    `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`   BIGINT UNSIGNED NULL,
    `updated_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status_pub` (`tenant_id`, `status`, `publish_at`),
    KEY `idx_tenant_top` (`tenant_id`, `is_top`, `publish_at`),
    KEY `idx_expire` (`tenant_id`, `status`, `expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================================================
--  7. 站内信（对应 V8__message.sql）
--     sys_message = 发件主表；sys_message_receipt = 收件明细（已读状态与回执时间）
--     uk_msg_user 保证批量投递不重复。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `sys_message` (
    `id`              BIGINT UNSIGNED NOT NULL,
    `tenant_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `title`           VARCHAR(200)    NOT NULL,
    `content`         MEDIUMTEXT      NOT NULL,
    `msg_type`        VARCHAR(20)     NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE/ALERT/SYSTEM',
    `sender_id`       BIGINT UNSIGNED NULL,
    `filter_role_ids` VARCHAR(500)    NULL COMMENT '批量筛选角色快照(审计用)',
    `filter_org_ids`  VARCHAR(500)    NULL COMMENT '批量筛选部门快照(审计用)',
    `receiver_ids`    VARCHAR(500)    NULL COMMENT '单条发送的显式接收人',
    `total_count`     INT UNSIGNED    NOT NULL DEFAULT 0,
    `read_count`      INT UNSIGNED    NOT NULL DEFAULT 0,
    `sent_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `version`         INT UNSIGNED    NOT NULL DEFAULT 0,
    `deleted`         TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_by`      BIGINT UNSIGNED NULL,
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_by`      BIGINT UNSIGNED NULL,
    `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_tenant_sent` (`tenant_id`, `sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_message_receipt` (
    `id`         BIGINT UNSIGNED NOT NULL,
    `tenant_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `message_id` BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `is_read`    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `read_at`    DATETIME(3)     NULL COMMENT '已读回执时间',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_user` (`tenant_id`, `message_id`, `user_id`),
    KEY `idx_user_read` (`tenant_id`, `user_id`, `is_read`),
    KEY `idx_msg` (`tenant_id`, `message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================================================
--  8. 系统监控采样（对应 V9__monitor_sample.sql）
--     由 MonitorSampleJob(JobRunr,每分钟)周期写入，供时间范围趋势查询。
-- =====================================================================================

CREATE TABLE IF NOT EXISTS `sys_monitor_sample` (
    `id`                   BIGINT UNSIGNED NOT NULL,
    `tenant_id`            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `cpu_usage`            DECIMAL(5,2)    NULL,
    `memory_usage`         DECIMAL(5,2)    NULL COMMENT 'JVM 堆内存使用率',
    `system_memory_usage`  DECIMAL(5,2)    NULL COMMENT '物理内存使用率',
    `disk_usage`           DECIMAL(5,2)    NULL,
    `used_heap_bytes`      BIGINT UNSIGNED NULL,
    `max_heap_bytes`       BIGINT UNSIGNED NULL,
    `used_memory_bytes`    BIGINT UNSIGNED NULL,
    `total_memory_bytes`   BIGINT UNSIGNED NULL,
    `online_users`         INT UNSIGNED    NOT NULL DEFAULT 0,
    `active_sessions`      INT UNSIGNED    NOT NULL DEFAULT 0,
    `thread_count`         INT UNSIGNED    NOT NULL DEFAULT 0,
    `sampled_at`           DATETIME(3)     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_time` (`tenant_id`, `sampled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================================================
--  9. 种子数据（全部使用 INSERT IGNORE，可重复执行）
-- =====================================================================================

-- 9.1 超级管理员角色
INSERT IGNORE INTO `sys_role`
    (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `status`, `sort_no`, `created_at`, `updated_at`)
VALUES
    (1, 0, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ACTIVE', 1, NOW(3), NOW(3));

-- 9.2 超级管理员账号（默认口令 admin123，与 V4 修正后的 hash 一致）
INSERT IGNORE INTO `sys_user`
    (`id`, `tenant_id`, `username`, `password_hash`, `display_name`, `status`,
     `role_id`, `token_version`, `version`, `created_at`, `updated_at`)
VALUES
    (1, 0, 'admin',
     '{bcrypt}$2a$10$HXuNze85xFb7XsvExix/mO/s49oO6K7L7NbFDKGqdXhGa1IsDnvXO',
     '超级管理员', 'ACTIVE', 1, 1, 0, NOW(3), NOW(3));

-- 9.3 字典分类
INSERT IGNORE INTO `sys_dict_type`
    (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `sort_no`, `remark`, `created_at`, `updated_at`)
VALUES
    (1, 0, 'sys_normal_disable', '系统开关',   'ACTIVE', 1, '正常 / 停用', NOW(3), NOW(3)),
    (2, 0, 'sys_user_sex',       '用户性别',   'ACTIVE', 2, '性别字典',   NOW(3), NOW(3)),
    (3, 0, 'sys_yes_no',         '是否标识',   'ACTIVE', 3, '是否字典',   NOW(3), NOW(3));

-- 9.4 字典数据
INSERT IGNORE INTO `sys_dict_data`
    (`id`, `tenant_id`, `dict_type_code`, `dict_label`, `dict_value`,
     `dict_sort`, `status`, `remark`, `created_at`, `updated_at`)
VALUES
    (1, 0, 'sys_normal_disable', '正常', '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (2, 0, 'sys_normal_disable', '停用', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (3, 0, 'sys_user_sex',       '男',   '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (4, 0, 'sys_user_sex',       '女',   '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (5, 0, 'sys_yes_no',         '是',   'Y', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (6, 0, 'sys_yes_no',         '否',   'N', 2, 'ACTIVE', NULL, NOW(3), NOW(3));

-- 9.5 系统参数
INSERT IGNORE INTO `sys_config`
    (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`,
     `config_type`, `remark`, `status`, `created_at`, `updated_at`)
VALUES
    (1, 0, 'sys.title',            '系统标题',         '管理框架',  'STRING',  '前端展示标题',   'ACTIVE', NOW(3), NOW(3)),
    (2, 0, 'sys.max.login.fail',   '最大登录失败次数', '5',         'INT',     '超过则锁定账户', 'ACTIVE', NOW(3), NOW(3)),
    (3, 0, 'sys.captcha.enabled',  '是否启用验证码',   'true',      'BOOLEAN', '登录验证码开关', 'ACTIVE', NOW(3), NOW(3));


-- =====================================================================================
--  10. 历史库口令修正（对应 V4__fix_admin_password.sql）
--     仅当仍是 V1 的占位 hash 时才更新，绝不覆盖用户已自行修改的密码。
--     幂等：重复执行时因 hash 已变更而匹配不到，天然空转。
-- =====================================================================================
UPDATE `sys_user`
SET `password_hash`      = '{bcrypt}$2a$10$HXuNze85xFb7XsvExix/mO/s49oO6K7L7NbFDKGqdXhGa1IsDnvXO',
    `password_changed_at` = NOW(3)
WHERE `username`     = 'admin'
  AND `password_hash` = '{bcrypt}$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96wNz/9H1P4F1q0qU3eZ0q7e2m';


-- =====================================================================================
--  11. 结构自检：确认单角色收敛已生效（role_id/org_id 存在，旧关联表已删除）
-- =====================================================================================
SELECT 'sys_user.role_id 列' AS `check_item`,
       COUNT(*) AS `ok`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
UNION ALL
SELECT 'sys_user.org_id 列',
       COUNT(*)
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
UNION ALL
SELECT '旧表 sys_user_role 已删除(应为0)',
       COUNT(*)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
UNION ALL
SELECT '旧表 sys_user_org 已删除(应为0)',
       COUNT(*)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_org';


-- =====================================================================================
--  12. 数据自检：输出各核心表行数
-- =====================================================================================
SELECT 'sys_user'       AS `table_name`, COUNT(*) AS `rows` FROM `sys_user`
UNION ALL SELECT 'sys_org',         COUNT(*) FROM `sys_org`
UNION ALL SELECT 'sys_role',        COUNT(*) FROM `sys_role`
UNION ALL SELECT 'sys_menu',        COUNT(*) FROM `sys_menu`
UNION ALL SELECT 'sys_api_resource',COUNT(*) FROM `sys_api_resource`
UNION ALL SELECT 'sys_dict_type',   COUNT(*) FROM `sys_dict_type`
UNION ALL SELECT 'sys_dict_data',   COUNT(*) FROM `sys_dict_data`
UNION ALL SELECT 'sys_config',      COUNT(*) FROM `sys_config`
UNION ALL SELECT 'sys_announcement',COUNT(*) FROM `sys_announcement`
UNION ALL SELECT 'sys_message',     COUNT(*) FROM `sys_message`
UNION ALL SELECT 'sys_monitor_sample', COUNT(*) FROM `sys_monitor_sample`
UNION ALL SELECT 'wf_definition_ext',   COUNT(*) FROM `wf_definition_ext`
UNION ALL SELECT 'wf_instance_ext',    COUNT(*) FROM `wf_instance_ext`;

-- =====================================================================================
--  文件结束。默认管理员：admin / admin123（生产环境请立即修改）
-- =====================================================================================