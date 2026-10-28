-- ============================================================
-- 模块参考 SQL：岗位管理
-- 说明：岗位表（并修复菜单 id 碰撞）
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V35__post_management.sql
-- ============================================================

-- V35 岗位管理（系统管理 → 岗位管理）
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:post:read 等）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 岗位表：编码租户内唯一；软删除 + 乐观锁，范式对齐 sys_role/sys_dict_type
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    post_code VARCHAR(64) NOT NULL,
    post_name VARCHAR(100) NOT NULL,
    post_sort INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(512) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_post_code (tenant_id, post_code),
    KEY idx_status_sort (tenant_id, status, post_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 用户-岗位关联（多对多）。无 deleted 列：分配为整体替换（物理删+插），范式对齐 sys_user_org
CREATE TABLE IF NOT EXISTS sys_user_post (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    post_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_post (tenant_id, user_id, post_id),
    KEY idx_post_user (tenant_id, post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3) 菜单：岗位管理（挂在「系统管理」1002 下，sort_no=110 承接导出中心 100 之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1091, 0, 1002, 'system:post', '岗位管理', 'MENU', '/system/post', 'system/PostView', 'system:post:read', 'Position', 1, 0, 110, 'ACTIVE', NOW(3), NOW(3));

-- 4) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1091;

