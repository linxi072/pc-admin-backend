-- V5 系统管理扩展模块：部门(机构)已由 V1 提供，此处新增「字典管理」与「系统变量」表及种子数据。
-- 表名 / 列名与 com.acme.scaffold.jooq.JooqTables 中的注册严格一致。

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(255) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_dict_code (tenant_id, dict_code),
    KEY idx_status_sort (tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    dict_type_code VARCHAR(64) NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_sort INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(255) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_type_value (tenant_id, dict_type_code, dict_value),
    KEY idx_type_sort (tenant_id, dict_type_code, dict_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    config_key VARCHAR(64) NOT NULL,
    config_name VARCHAR(100) NOT NULL,
    config_value VARCHAR(500) NULL,
    config_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    remark VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config_key (tenant_id, config_key),
    KEY idx_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 种子数据：常用字典与系统参数（幂等，重复执行安全）
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, sort_no, remark, created_at, updated_at)
VALUES
    (1, 0, 'sys_normal_disable', '系统开关', 'ACTIVE', 1, '正常 / 停用', NOW(3), NOW(3)),
    (2, 0, 'sys_user_sex', '用户性别', 'ACTIVE', 2, '性别字典', NOW(3), NOW(3)),
    (3, 0, 'sys_yes_no', '是否标识', 'ACTIVE', 3, '是否字典', NOW(3), NOW(3));

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_code, dict_label, dict_value, dict_sort, status, remark, created_at, updated_at)
VALUES
    (1, 0, 'sys_normal_disable', '正常', '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (2, 0, 'sys_normal_disable', '停用', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (3, 0, 'sys_user_sex', '男', '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (4, 0, 'sys_user_sex', '女', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (5, 0, 'sys_yes_no', '是', 'Y', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (6, 0, 'sys_yes_no', '否', 'N', 2, 'ACTIVE', NULL, NOW(3), NOW(3));

INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, config_type, remark, status, created_at, updated_at)
VALUES
    (1, 0, 'sys.title', '系统标题', '管理框架', 'STRING', '前端展示标题', 'ACTIVE', NOW(3), NOW(3)),
    (2, 0, 'sys.max.login.fail', '最大登录失败次数', '5', 'INT', '超过则锁定账户', 'ACTIVE', NOW(3), NOW(3)),
    (3, 0, 'sys.captcha.enabled', '是否启用验证码', 'true', 'BOOLEAN', '登录验证码开关', 'ACTIVE', NOW(3), NOW(3));
