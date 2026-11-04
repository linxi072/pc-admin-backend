// R16 配置中心前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-framework ConfigCenterController
//   GET /status、GET /snapshot、POST /refresh（权限 system:config-center:read / :manage）。
//   控制器受 @ConditionalOnProperty(app.config-center.enabled=true) 守卫，未启用时不存在。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../configCenter.js'), 'utf8')

test('ConfigCenter API：导出函数与后端端点一一对应', () => {
  const expected = ['getConfigStatus', 'getConfigSnapshot', 'refreshConfig']
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

test('ConfigCenter API：端点使用 /api/system/config-center 前缀', () => {
  const urls = source.match(/\/api\/system\/config-center\/(status|snapshot|refresh)/g) || []
  assert.ok(urls.length >= 3, 'config-center URL 前缀至少出现 3 次')
})

test('ConfigCenter API：热刷新使用 POST /refresh', () => {
  assert.match(source, /export const refreshConfig = \(\) => request\(\{ method: 'POST', url: '\/api\/system\/config-center\/refresh' \}\)/)
})

test('ConfigCenter API：统一经 request() 发出', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 3, '应恰好 3 个导出函数各发一次 request')
})
