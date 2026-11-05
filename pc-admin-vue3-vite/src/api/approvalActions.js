import { request } from './http'

// ==================== 审批中心动作（ApprovalActionController，写操作） ====================
// 与 approval.js（只读门户）刻意分离：approval.test.js 断言门户不得含写端点，保持其契约不变。
//
// 幂等：审批/转办/委派/办结委派委托的 WorkflowService 方法标注 @Idempotent(requireKey=true)，
// 必须携带 Idempotency-Key 请求头；此处复用 body.operationId，让传输幂等键与引擎层业务
// 幂等键取同一值——重放时请求指纹一致，且后端双重去重（请求头 + operationId）语义对齐。
function postWithIdempotencyKey(url, body) {
  return request({
    method: 'POST',
    url,
    data: body,
    headers: { 'Idempotency-Key': body.operationId }
  })
}

// POST /start -> String(processInstanceId)
export const approvalStart = (body) =>
  request({ method: 'POST', url: '/api/approval/actions/start', data: body })

// POST /approve -> void（body.action: APPROVE | REJECT）
export const approvalApprove = (body) =>
  postWithIdempotencyKey('/api/approval/actions/approve', body)

// POST /transfer -> void
export const approvalTransfer = (body) =>
  postWithIdempotencyKey('/api/approval/actions/transfer', body)

// POST /delegate -> void（临时委托他人办理）
export const approvalDelegate = (body) =>
  postWithIdempotencyKey('/api/approval/actions/delegate', body)

// POST /resolve-delegate -> void（被委托人办结交还原审批人）
export const approvalResolveDelegate = (body) =>
  postWithIdempotencyKey('/api/approval/actions/resolve-delegate', body)

// POST /{taskId}/claim -> void（认领池化任务，天然幂等，无需 Idempotency-Key）
export const approvalClaim = (taskId) =>
  request({ method: 'POST', url: `/api/approval/actions/${taskId}/claim` })
