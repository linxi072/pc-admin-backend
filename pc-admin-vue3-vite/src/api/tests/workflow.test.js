import { describe, expect, it, vi, beforeEach } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import {
  myTasks, completeTask, transferTask, delegateTask, resolveDelegateTask,
  myInstances, instanceRecords, startProcess,
  createDefinition, saveDefinition, listDefinitions, getDefinition,
  getPublishedDefinition, deleteDefinition, publishDefinition, unpublishDefinition, getDefinitionBpmn,
  runningInstances, overdueTasks, definitionVersions, monitorStats, remindTask,
  suspendDefinition, activateDefinition,
  reassignInstance, suspendInstance, resumeInstance, migrateInstance
} from '../workflow.js'

// 锁定工作流 API 全部 27 个端点的前后端同形（方法/URL/参数形状）。
// URL 断言与 src/api/workflow.js 实现保持一致；契约扫描器已排除 src/api/tests/ 夹具目录。

describe('工作流 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  // 无参 GET
  it.each([
    [myTasks, '/api/workflow/tasks/mine'],
    [myInstances, '/api/workflow/instances/mine'],
    [listDefinitions, '/api/workflow/definitions'],
    [monitorStats, '/api/workflow/monitor/stats']
  ])('无参 GET 端点同形：%s', (fn, url) => {
    fn()
    expect(request).toHaveBeenCalledWith({ method: 'GET', url })
  })

  // 带 query 的 GET
  it.each([
    [runningInstances, '/api/workflow/monitor/running-instances'],
    [overdueTasks, '/api/workflow/monitor/overdue-tasks']
  ])('分页 GET 端点同形：%s', (fn, url) => {
    const params = { page: 1, size: 20 }
    fn(params)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url, params })
  })

  // 路径参数 GET（反引号模板串）
  it('路径参数 GET 端点同形（记录/定义/BPMN/版本/已发布定义）', () => {
    instanceRecords('pi-1')
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/workflow/instances/pi-1/records` })
    getDefinition('d-1')
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/workflow/definitions/d-1` })
    getPublishedDefinition('key-1')
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/workflow/definitions/published/key-1` })
    getDefinitionBpmn('d-1')
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/workflow/definitions/d-1/bpmn` })
    definitionVersions('key-1')
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/workflow/monitor/definition-versions/key-1` })
  })

  // 无参 POST（治理类）
  it('无参 POST 治理端点同形（发布/下线/催办/挂起激活）', () => {
    publishDefinition('d-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/workflow/definitions/d-1/publish` })
    unpublishDefinition('d-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/workflow/definitions/d-1/unpublish` })
    remindTask('t-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/workflow/monitor/tasks/t-1/remind` })
    suspendDefinition('def-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/workflow/monitor/definitions/def-1/suspend` })
    activateDefinition('def-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/workflow/monitor/definitions/def-1/activate` })
  })

  // 路径参数 + query 的写端点（reassign/migrate）
  it('实例治理 reassign/migrate 端点同形（query 传参）', () => {
    reassignInstance('pi-1', 42)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/workflow/admin/instances/pi-1/reassign`,
      params: { toUserId: 42 }
    })
    migrateInstance('pi-1', 'def-2')
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/workflow/admin/instances/pi-1/migrate`,
      params: { toProcessDefinitionId: 'def-2' }
    })
  })

  // 路径参数 POST（suspend/resume，既有治理能力回归）
  it('流程实例挂起/恢复端点同形', () => {
    suspendInstance('pi-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/workflow/admin/instances/pi-1/suspend` })
    resumeInstance('pi-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/workflow/admin/instances/pi-1/resume` })
  })

  // body POST/PUT/DELETE
  it('body 写端点同形（任务办理/定义增删保存）', () => {
    const taskBody = { taskId: 't-1', action: 'APPROVE' }
    completeTask(taskBody)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/workflow/tasks/complete', data: taskBody })
    transferTask(taskBody)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/workflow/tasks/transfer', data: taskBody })
    delegateTask(taskBody)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/workflow/tasks/delegate', data: taskBody })
    resolveDelegateTask(taskBody)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/workflow/tasks/resolve-delegate', data: taskBody })
    const startBody = { processKey: 'leave' }
    startProcess(startBody)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/workflow/instances/start', data: startBody })
    const defBody = { name: '请假' }
    createDefinition(defBody)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/workflow/definitions', data: defBody })
    saveDefinition('d-1', defBody)
    expect(request).toHaveBeenCalledWith({ method: 'PUT', url: `/api/workflow/definitions/d-1`, data: defBody })
    deleteDefinition('d-1')
    expect(request).toHaveBeenCalledWith({ method: 'DELETE', url: `/api/workflow/definitions/d-1` })
  })

  it('不使用 blob 通道，全部走统一 request', () => {
    expect(requestRaw).not.toHaveBeenCalled()
  })
})
