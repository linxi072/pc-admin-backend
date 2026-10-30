-- V66 表单提交管理菜单（M47 跨表单统一提交管理）：在「系统管理」(1002) 下新增「表单提交管理」入口，
-- 承载跨表单提交记录的统一检索（分页 / 统计 / 详情 / 逻辑删除）与导出中心模板触发。
-- 与既有「低代码表单试点」(id=1121，运行时渲染 / 提交 / 按单表单查记录) 互补：
-- 本页是表单提交数据的治理入口，视角从「单个表单」升级为「全部表单」。
--
-- 设计要点：
--   1) 后端接口前缀 /api/system/form-submissions；读权限复用 system:form-submission:read、
--      删除权限 system:form-submission:delete，均由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize
--      自动落库并授予 SUPER_ADMIN（本菜单仅负责导航可见性与角色绑定）；
--   2) route_path 必须与前端路由（system/form-submissions）严格一致，否则路由守卫会重定向到工作台；
--   3) 依赖：V47（sys_form_definition、低代码表单试点）、V53（sys_form_submission 落库）；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行；
--   5) sort_no=121 紧随 OAuth2 令牌管理(1131/120) 之后，置于系统管理目录末尾。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1132, 0, 1002, 'system:form-submission', '表单提交管理', 'MENU', '/system/form-submissions', 'system/FormSubmissionManageView', 'system:form-submission:read', 'List', 1, 0, 121, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read/delete 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1132;
