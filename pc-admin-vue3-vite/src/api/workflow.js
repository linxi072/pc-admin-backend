import { request } from './http'

// GET /api/workflow/tasks/mine -> List<TaskView>
export const myTasks = () =>
  request({ method: 'GET', url: '/api/workflow/tasks/mine' })

// POST /api/workflow/tasks/complete -> void
export const completeTask = (body) =>
  request({ method: 'POST', url: '/api/workflow/tasks/complete', data: body })

// POST /api/workflow/tasks/transfer -> void
export const transferTask = (body) =>
  request({ method: 'POST', url: '/api/workflow/tasks/transfer', data: body })

// POST /api/workflow/tasks/delegate -> void（委派任务：临时委托他人办理）
export const delegateTask = (body) =>
  request({ method: 'POST', url: '/api/workflow/tasks/delegate', data: body })

// POST /api/workflow/tasks/resolve-delegate -> void（被委托人办结交还原审批人）
export const resolveDelegateTask = (body) =>
  request({ method: 'POST', url: '/api/workflow/tasks/resolve-delegate', data: body })

// GET /api/workflow/instances/mine -> List<InstanceView>
export const myInstances = () =>
  request({ method: 'GET', url: '/api/workflow/instances/mine' })

// GET /api/workflow/instances/{pid}/records -> List<ApprovalRecordView>
export const instanceRecords = (pid) =>
  request({ method: 'GET', url: `/api/workflow/instances/${pid}/records` })

// POST /api/workflow/instances/start -> String(processInstanceId)
export const startProcess = (body) =>
  request({ method: 'POST', url: '/api/workflow/instances/start', data: body })

// ---------------- 自定义工作流定义 ----------------

// POST /api/workflow/definitions -> WorkflowDesignView（新建草稿）
export const createDefinition = (body) =>
  request({ method: 'POST', url: '/api/workflow/definitions', data: body })

// PUT /api/workflow/definitions/{id} -> WorkflowDesignView（保存草稿，全量覆盖节点与连线）
export const saveDefinition = (id, body) =>
  request({ method: 'PUT', url: `/api/workflow/definitions/${id}`, data: body })

// GET /api/workflow/definitions -> List<WorkflowDesignView>
export const listDefinitions = () =>
  request({ method: 'GET', url: '/api/workflow/definitions' })

// GET /api/workflow/definitions/{id} -> WorkflowDesignView
export const getDefinition = (id) =>
  request({ method: 'GET', url: `/api/workflow/definitions/${id}` })

// GET /api/workflow/definitions/published/{processKey} -> WorkflowDesignView | null
// 用于前端发起表单联动：返回非 null 表示该 processKey 已发布设计（隐藏审批人/主管选择框）
export const getPublishedDefinition = (processKey) =>
  request({ method: 'GET', url: `/api/workflow/definitions/published/${processKey}` })

// DELETE /api/workflow/definitions/{id} -> void
export const deleteDefinition = (id) =>
  request({ method: 'DELETE', url: `/api/workflow/definitions/${id}` })

// POST /api/workflow/definitions/{id}/publish -> WorkflowDesignView
export const publishDefinition = (id) =>
  request({ method: 'POST', url: `/api/workflow/definitions/${id}/publish` })

// POST /api/workflow/definitions/{id}/unpublish -> WorkflowDesignView
export const unpublishDefinition = (id) =>
  request({ method: 'POST', url: `/api/workflow/definitions/${id}/unpublish` })

// GET /api/workflow/definitions/{id}/bpmn -> { processKey, bpmnXml }
export const getDefinitionBpmn = (id) =>
  request({ method: 'GET', url: `/api/workflow/definitions/${id}/bpmn` })

// ---------------- 流程监控（M23） ----------------

// GET /api/workflow/monitor/running-instances -> PageResult<RunningInstanceView>
export const runningInstances = (params) =>
  request({ method: 'GET', url: '/api/workflow/monitor/running-instances', params })

// GET /api/workflow/monitor/overdue-tasks -> PageResult<OverdueTaskView>
export const overdueTasks = (params) =>
  request({ method: 'GET', url: '/api/workflow/monitor/overdue-tasks', params })

// GET /api/workflow/monitor/definition-versions/{processKey} -> List<DefinitionVersionView>
export const definitionVersions = (processKey) =>
  request({ method: 'GET', url: `/api/workflow/monitor/definition-versions/${processKey}` })

// GET /api/workflow/monitor/stats -> MonitorStatsView
export const monitorStats = () =>
  request({ method: 'GET', url: '/api/workflow/monitor/stats' })

// POST /api/workflow/monitor/tasks/{taskId}/remind -> void
export const remindTask = (taskId) =>
  request({ method: 'POST', url: `/api/workflow/monitor/tasks/${taskId}/remind` })

// ---------------- 流程实例治理（M23 续 /api/workflow/admin） ----------------

// POST /api/workflow/admin/instances/{processInstanceId}/reassign?toUserId= -> void
// 将实例的全部活跃任务重新指派给目标办理人（管理员视角，可越过当前办理人）
export const reassignInstance = (processInstanceId, toUserId) =>
  request({
    method: 'POST',
    url: `/api/workflow/admin/instances/${processInstanceId}/reassign`,
    params: { toUserId }
  })

// POST /api/workflow/admin/instances/{processInstanceId}/suspend -> void
// 挂起流程实例（冻结在途任务，引擎态 suspended）
export const suspendInstance = (processInstanceId) =>
  request({
    method: 'POST',
    url: `/api/workflow/admin/instances/${processInstanceId}/suspend`
  })

// POST /api/workflow/admin/instances/{processInstanceId}/resume -> void
// 恢复（取消挂起）流程实例
export const resumeInstance = (processInstanceId) =>
  request({
    method: 'POST',
    url: `/api/workflow/admin/instances/${processInstanceId}/resume`
  })

// POST /api/workflow/admin/instances/{processInstanceId}/migrate?toProcessDefinitionId= -> void
// 将运行实例迁移到目标流程定义版本（同流程不同版本热升级）
export const migrateInstance = (processInstanceId, toProcessDefinitionId) =>
  request({
    method: 'POST',
    url: `/api/workflow/admin/instances/${processInstanceId}/migrate`,
    params: { toProcessDefinitionId }
  })
