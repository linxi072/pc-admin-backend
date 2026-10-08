-- V14 自定义工作流定义接口资源与权限码（供 @PreAuthorize("hasAuthority(...)") 与 JWT 鉴权使用）。
-- 全部语句幂等，重复执行结果一致。
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
    (900000000401, 0, '新建工作流草稿', 'workflow:definition:create', 'POST', '/api/workflow/definitions', 'WorkflowDefinitionController#create', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000402, 0, '工作流定义列表', 'workflow:definition:read', 'GET', '/api/workflow/definitions', 'WorkflowDefinitionController#list', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000403, 0, '工作流定义详情', 'workflow:definition:read', 'GET', '/api/workflow/definitions/{id}', 'WorkflowDefinitionController#get', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000404, 0, '保存工作流草稿', 'workflow:definition:update', 'PUT', '/api/workflow/definitions/{id}', 'WorkflowDefinitionController#save', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000405, 0, '删除工作流草稿', 'workflow:definition:delete', 'DELETE', '/api/workflow/definitions/{id}', 'WorkflowDefinitionController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000406, 0, '发布工作流', 'workflow:definition:publish', 'POST', '/api/workflow/definitions/{id}/publish', 'WorkflowDefinitionController#publish', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000407, 0, '取消发布工作流', 'workflow:definition:publish', 'POST', '/api/workflow/definitions/{id}/unpublish', 'WorkflowDefinitionController#unpublish', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000408, 0, '查看工作流 BPMN', 'workflow:definition:read', 'GET', '/api/workflow/definitions/{id}/bpmn', 'WorkflowDefinitionController#bpmn', 'REQUIRED', 'ACTIVE', 'NORMAL')
ON DUPLICATE KEY UPDATE resource_name = VALUES(resource_name), permission_code = VALUES(permission_code);
