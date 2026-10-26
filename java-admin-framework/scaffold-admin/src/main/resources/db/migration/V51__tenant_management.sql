-- V51 租户管理域（M19 P2）：sys_tenant，把「平台下有哪些租户、各自联系人/状态/有效期」从口头约定
-- 收口为可治理的租户注册表（tenant_code 唯一 + 状态机 ENABLED/DISABLED + 可选过期）。
--
-- 设计要点：
--   1) 多租户隔离整体策略（M19）：共享表 + tenant_id 列 + jOOQ 拦截强制隔离（P3 落地）。
--      sys_tenant 本身是「租户维度表」，由平台管理员统一维护，所有行 tenant_id 恒为 0（平台租户），
--      查询不按当前租户过滤——它是隔离的「被治理对象」而非「被隔离数据」。
--   2) 结构校验集中在 common.tenant.TenantRules（纯函数）：tenant_code 格式、tenant_name 长度、
--      状态白名单、联系人电话/邮箱格式、过期时间须晚于当前。
--   3) 接口资源（system:tenant:read / :manage）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   4) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
--   5) 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。

-- 1) 表：sys_tenant（逻辑删除 + 乐观锁；(tenant_code) 唯一约束兜底并发注册）
CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NULL,
    contact_phone VARCHAR(32) NULL,
    contact_email VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    expire_at DATETIME(3) NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：租户管理（挂在「系统管理」1002 下，sort_no=113 紧随开放平台客户端 112 之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1124, 0, 1002, 'system:tenant', '租户管理', 'MENU', '/system/tenants', 'system/TenantView', 'system:tenant:read', 'OfficeBuilding', 1, 0, 113, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1124;
