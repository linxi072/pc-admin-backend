// R15 混沌工程前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-framework ChaosController
//   GET/POST/DELETE /api/system/chaos/faults（权限 system:chaos:read / system:chaos:manage）。
//   控制器受 @ConditionalOnProperty(app.chaos.enabled=true) 守卫，未启用时不存在。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../chaos.js'), 'utf8')

test('Chaos API：导出函数与后端端点一一对应', () => {
  const expected = ['listFaults', 'injectFault', 'releaseFault', 'clearFaults']
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

test('Chaos API：故障端点使用 /api/system/chaos/faults 前缀', () => {
  const urls = source.match(/\/api\/system\/chaos\/faults/g) || []
  assert.ok(urls.length >= 4, 'faults URL 前缀至少出现 4 次（list/inject/release/clear）')
})

test('Chaos API：解除使用 DELETE /{id}', () => {
  assert.match(source, /export const releaseFault = \(id\) => request\(\{ method: 'DELETE', url: `\/api\/system\/chaos\/faults\/\$\{id\}` \}\)/)
})

test('Chaos API：统一经 request() 发出', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 4, '应恰好 4 个导出函数各发一次 request')
})
