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

// GET /api/workflow/instances/mine -> List<InstanceView>
export const myInstances = () =>
  request({ method: 'GET', url: '/api/workflow/instances/mine' })

// GET /api/workflow/instances/{pid}/records -> List<ApprovalRecordView>
export const instanceRecords = (pid) =>
  request({ method: 'GET', url: `/api/workflow/instances/${pid}/records` })

// POST /api/workflow/instances/start -> String(processInstanceId)
export const startProcess = (body) =>
  request({ method: 'POST', url: '/api/workflow/instances/start', data: body })
