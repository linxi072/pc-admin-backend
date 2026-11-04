// 租户治理前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-system TenantGovernanceController
//   GET /api/system/tenants/data-overview（权限 system:tenant:manage）
//   POST /api/system/tenants/backfill
//   POST /api/system/tenants/reassign

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../tenantGovernance.js'), 'utf8')

test('TenantGovernance API：导出函数与后端端点一一对应', () => {
  const expected = ['fetchTenantDataOverview', 'backfillTenantData', 'reassignTenantData']
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

test('TenantGovernance API：端点使用 /api/system/tenants 前缀', () => {
  const urls = source.match(/\/api\/system\/tenants/g) || []
  assert.ok(urls.length >= 3, 'tenants URL 前缀至少出现 3 次（overview/backfill/reassign）')
})

test('TenantGovernance API：统一经 request() 发出', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 3, '应恰好 3 个导出函数各发一次 request')
})
