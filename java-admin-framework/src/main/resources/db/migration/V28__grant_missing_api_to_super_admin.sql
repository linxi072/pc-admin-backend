-- =============================================================================
-- V28: 补授 V11 之后新增接口的 SUPER_ADMIN 授权（D-5 数据修复）
-- =============================================================================
-- 背景：
--   V11 一次性把当时全部接口授予 SUPER_ADMIN，但 ApiResourceService.sync() 历来
--   「只登记资源、不建角色授权」，与 ApiResourceAutoSyncRunner 注释承诺的
--   「新增接口无需手工登记也不会出现 403」相悖。后果：V11 之后上线的端点对
--   超级管理员一律 403，本轮业务测试实测实锤——
--     · system:audit:read            → 审计日志（页面/摘要/链路）超管不可访问
--     · workflow:definition:*        → 工作流设计器（增删改查/发布）超管不可访问
--     · workflow:task:claim          → 任务认领超管不可访问
--
-- 本脚本按权限码把这 7 个缺失授权补授给 SUPER_ADMIN（幂等，可重复执行）。
-- 代码侧同步修复：sync() 对「本次新插入」的资源自动授予 SUPER_ADMIN，
-- 新增接口不再依赖手工迁移补授（仅新插入行授予，人工收回的授权不受影响）。
--
-- 幂等性：INSERT IGNORE + NOT EXISTS 双重去重，重复执行结果一致。
-- =============================================================================

INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, r.id, a.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
         JOIN sys_api_resource a ON a.permission_code IN (
    'system:audit:read',
    'workflow:definition:create',
    'workflow:definition:read',
    'workflow:definition:update',
    'workflow:definition:delete',
    'workflow:definition:publish',
    'workflow:task:claim'
    )
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1
                  FROM sys_role_api ra
                  WHERE ra.role_id = r.id
                    AND ra.api_id = a.id);
