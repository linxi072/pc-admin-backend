// R7 审批中心前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-approval ApprovalPortalController
//   (/api/approval/portal，只读聚合，权限 approval:portal:view)。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../approval.js'), 'utf8')

// ---------- 1) 导出函数集合与后端端点一一对应 ----------

test('审批门户 API：导出函数与后端端点一一对应', () => {
  const expected = [
    'approvalDashboard',
    'approvalTodo',
    'approvalDone',
    'approvalMyInstances',
    'approvalStartable',
    'approvalDetail'
  ]
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

// ---------- 2) HTTP 方法与 URL（门户全为只读 GET） ----------

test('审批门户 API：全部端点均为 GET（门户只读聚合）', () => {
  const gets = source.match(/method: 'GET'/g) || []
  assert.equal(gets.length, 6, '应恰好 6 个 GET 端点')
  assert.equal((source.match(/method: '(POST|PUT|DELETE)'/g) || []).length, 0, '门户不得含写操作端点')
})

test('审批门户 API：URL 前缀统一为 /api/approval/portal', () => {
  const urls = source.match(/\/api\/approval\/portal/g) || []
  assert.equal(urls.length, 6, '6 个端点共享 /api/approval/portal 前缀')
})

test('审批门户 API：详情端点按 processInstanceId 传路径参数', () => {
  assert.match(source, /export const approvalDetail = \(processInstanceId\) =>\n  request\(\{ method: 'GET', url: `\/api\/approval\/portal\/detail\/\$\{processInstanceId\}` \}\)/)
})

// ---------- 3) 请求统一走 http.request 拆包层 ----------

test('审批门户 API：统一经 request() 发出（复用 Result<T> 拆包与错误处理）', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
})
