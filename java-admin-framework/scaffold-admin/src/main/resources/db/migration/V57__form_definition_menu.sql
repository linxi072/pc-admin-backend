-- V57 表单定义管理菜单（M22 深化）：在「系统管理」(1002) 下新增「表单定义管理」入口，
-- 承载表单定义 CRUD + JSON-Schema 可视化编辑 + 实时预览（复用前端 DynamicForm 组件）。
-- 与既有「低代码表单试点」(id=1121) 互补：本页负责定义生命周期管理，试点页负责运行时
-- 渲染 / 提交 / 提交记录查看。
--
-- 设计要点：
--   1) 后端接口前缀 /api/system/form-definitions；读权限复用 system:form-definition:read
--      （由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN）；
--      写权限 system:form-definition:create / update / delete 同样自动登记。
--   2) route_path 必须与前端路由（system/form-definition）严格一致，否则路由守卫会重定向到工作台。
--   3) 依赖：V47（sys_form_definition、低代码表单试点菜单 1121）、V53（提交落库）。
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行。
--   5) sort_no=117 紧随数据权限(1127/116) 之后，置于系统管理目录末尾。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1128, 0, 1002, 'system:form-definition', '表单定义管理', 'MENU', '/system/form-definition', 'system/FormDefinitionView', 'system:form-definition:read', 'Document', 1, 0, 117, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1128;
