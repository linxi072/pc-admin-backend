// 审批中心动作 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-approval ApprovalActionController
//   (/api/approval/actions，权限 approval:action:*)，门户只读契约见 approval.test.js。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../approvalActions.js'), 'utf8')

// ---------- 1) 导出函数与后端 6 个写端点一一对应 ----------

test('审批动作 API：6 个导出函数覆盖后端全部写端点', () => {
  const expected = [
    'approvalStart',
    'approvalApprove',
    'approvalTransfer',
    'approvalDelegate',
    'approvalResolveDelegate',
    'approvalClaim'
  ]
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

test('审批动作 API：URL 前缀统一为 /api/approval/actions，且每个端点各一处', () => {
  const urls = source.match(/\/api\/approval\/actions/g) || []
  assert.equal(urls.length, 6, '6 个端点共享 /api/approval/actions 前缀')
})

test('审批动作 API：全部为 POST 写操作，无 GET/PUT/DELETE', () => {
  assert.ok(source.includes("method: 'POST'"))
  assert.equal((source.match(/method: '(GET|PUT|DELETE)'/g) || []).length, 0)
})

// ---------- 2) 幂等键契约（@Idempotent requireKey=true） ----------

test('审批动作 API：4 个幂等写动作携带 Idempotency-Key 请求头', () => {
  assert.match(source, /headers: \{ 'Idempotency-Key': body\.operationId \}/)
  for (const fn of ['approvalApprove', 'approvalTransfer', 'approvalDelegate', 'approvalResolveDelegate']) {
    assert.ok(
      new RegExp(`export const ${fn} = \\(body\\) =>\\n  postWithIdempotencyKey\\(`).test(source),
      `${fn} 必须走幂等 POST 通道`
    )
  }
})

test('审批动作 API：发起与认领不强制幂等键', () => {
  assert.match(source, /export const approvalStart = \(body\) =>\n  request\(\{ method: 'POST'/)
  assert.match(source, /export const approvalClaim = \(taskId\) =>\n  request\(\{ method: 'POST'/)
})

// ---------- 3) 请求统一走 http.request 拆包层 ----------

test('审批动作 API：统一经 request() 发出（复用 Result<T> 拆包与错误处理）', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
})
