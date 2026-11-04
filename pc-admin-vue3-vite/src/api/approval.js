import { request } from './http'

// ==================== 审批中心门户（ApprovalPortalController，只读聚合） ====================

// GET /dashboard -> ApprovalPortalView { todoTasks, doneTasks, myInstances, startable, stats }
export const approvalDashboard = () => request({ method: 'GET', url: '/api/approval/portal/dashboard' })

// GET /todo -> TaskView[]
export const approvalTodo = () => request({ method: 'GET', url: '/api/approval/portal/todo' })

// GET /done -> TaskView[]
export const approvalDone = () => request({ method: 'GET', url: '/api/approval/portal/done' })

// GET /instances -> InstanceView[]
export const approvalMyInstances = () => request({ method: 'GET', url: '/api/approval/portal/instances' })

// GET /startable -> WorkflowDesignView[]
export const approvalStartable = () => request({ method: 'GET', url: '/api/approval/portal/startable' })

// GET /detail/{processInstanceId} -> InstanceDetailView
export const approvalDetail = (processInstanceId) =>
  request({ method: 'GET', url: `/api/approval/portal/detail/${processInstanceId}` })
