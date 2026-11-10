-- V82 菜单权限码对齐（若依规范 P0-1）：修正 2 条「菜单授予」与「接口要求」不一致的存量数据。
--
-- 背景：V32 基线种子为「部门管理」写入 system:department:read、为「接口资源管理」写入
--   system:api-resource:read，但后端实际 @PreAuthorize 分别为
--   system:org:read（OrgController L41）与 system:api:read（ApiResourceController L37）。
--   SUPER_ADMIN 全量放行掩盖了问题；普通角色按菜单授权后进入页面即 403。
--   前端无权限码硬编码（已全量核实），改数据不影响可见性逻辑。
--
-- 配套：V32 基线种子已同步改为正确值，全新初始化直接得到正确数据；
--   本脚本仅用于修正「V32 之前已经初始化」的既有库（INSERT IGNORE 不会覆盖旧行）。
--
-- 幂等：WHERE 以主键 id 限定且仅当目标值不同才更新，可重复执行；
--   刻意不写旧权限码字面量，避免被 check-permission.py 误采为菜单种子值。
-- 影响：2 行数据，无 DDL、无接口变更、无业务列变更。

UPDATE sys_menu
   SET permission_code = 'system:org:read', updated_at = NOW(3)
 WHERE id = 1013
   AND (permission_code IS NULL OR permission_code <> 'system:org:read');

UPDATE sys_menu
   SET permission_code = 'system:api:read', updated_at = NOW(3)
 WHERE id = 1014
   AND (permission_code IS NULL OR permission_code <> 'system:api:read');
