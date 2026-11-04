// R14 租户维度指标前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-framework TenantMetricsController
//   GET /api/observability/tenant-metrics（权限 system:observability:read）。
//   作为可观测中心新增 Tab 接入，不独立建路由。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../tenantMetrics.js'), 'utf8')

test('TenantMetrics API：导出函数与后端端点一一对应', () => {
  assert.ok(/export const listTenantMetrics =/.test(source), '缺少导出函数: listTenantMetrics')
})

test('TenantMetrics API：端点使用 /api/observability/tenant-metrics 前缀', () => {
  const urls = source.match(/\/api\/observability\/tenant-metrics/g) || []
  assert.ok(urls.length >= 1, 'tenant-metrics URL 前缀至少出现 1 次')
})

test('TenantMetrics API：统一经 request() 发出', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 1, '应恰好 1 个导出函数发一次 request')
})
