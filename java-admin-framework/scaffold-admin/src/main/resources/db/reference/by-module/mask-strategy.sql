-- ============================================================
-- 模块参考 SQL：脱敏策略中心
-- 说明：脱敏策略与规则
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V46__mask_strategy_center.sql
-- ============================================================

-- V46 脱敏策略中心（M21）：sys_mask_strategy，把「哪些逻辑字段用哪种脱敏类型、对哪些角色豁免」
-- 从散落各处的硬编码收口为可配置策略中心，统一经 MaskStrategyResolver 委托 SensitiveMasker 分派。
--
-- 设计要点：
--   1) 默认安全回落：未命中任何策略的字段，由调用方声明的 SensitiveType 驱动默认掩码
--      （与既有视图层脱敏口径一致），绝不因「未配置策略」而泄漏明文。
--   2) apply_scope：ALL=一律脱敏；ROLE_EXEMPT=命中 exempt_roles 的角色看明文、其余脱敏；
--      NONE=永不脱敏（显式放行，用于已脱敏存储字段）。sensitive_type 驱动 SensitiveMasker 算法。
--   3) 解析/命中/豁免判定集中在 system.maskstrategy.rules.MaskStrategyRules（纯函数），
--      实际脱敏委托 common.sensitive.SensitiveMasker，与 M1 审计落库、M20 审计增强共用同一脱敏内核。
--   4) 接口资源（system:mask-strategy:read / :manage）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   5) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。

-- 1) 表：sys_mask_strategy（逻辑删除 + 乐观锁；(tenant_id, strategy_code) 唯一约束兜底并发写入）
CREATE TABLE IF NOT EXISTS sys_mask_strategy (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_name VARCHAR(128) NOT NULL,
    field_key VARCHAR(128) NOT NULL,
    sensitive_type VARCHAR(32) NOT NULL,
    apply_scope VARCHAR(16) NOT NULL DEFAULT 'ROLE_EXEMPT',
    exempt_roles VARCHAR(512) NULL,
    priority INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, strategy_code),
    KEY idx_tenant_field (tenant_id, field_key),
    KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：脱敏策略中心（挂在「系统管理」1002 下，sort_no=110 紧随可观测/导出/导入中心之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1120, 0, 1002, 'system:mask-strategy', '脱敏策略中心', 'MENU', '/system/mask-strategies', 'system/MaskStrategy', 'system:mask-strategy:read', 'Lock', 1, 0, 110, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1120;

