// R8 看板模板前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-workbench DashboardTemplateController
//   (/api/dashboard/template，权限 dashboard:template:manage)。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../dashboardTemplate.js'), 'utf8')

// ---------- 1) 导出函数集合与后端端点一一对应 ----------

test('看板模板 API：导出函数与后端端点一一对应', () => {
  const expected = [
    'createDashboardTemplate',
    'updateDashboardTemplate',
    'deleteDashboardTemplate',
    'getDashboardTemplate',
    'publishDashboardTemplate',
    'unpublishDashboardTemplate',
    'viewDashboardTemplate',
    'pageDashboardTemplates'
  ]
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

// ---------- 2) HTTP 方法与 URL 前缀 ----------

test('看板模板 API：URL 前缀统一为 /api/dashboard/template', () => {
  const urls = source.match(/\/api\/dashboard\/template/g) || []
  assert.ok(urls.length >= 8, '前缀至少出现 8 次（8 个端点共享）')
})

test('看板模板 API：创建为 POST /、更新为 PUT /、删除为 POST /{id}/delete（对齐后端非 RESTful 删除）', () => {
  assert.match(source, /export const createDashboardTemplate = \(body\) =>\n  request\(\{ method: 'POST', url: '\/api\/dashboard\/template', data: body \}\)/)
  assert.match(source, /export const updateDashboardTemplate = \(body\) =>\n  request\(\{ method: 'PUT', url: '\/api\/dashboard\/template', data: body \}\)/)
  assert.match(source, /export const deleteDashboardTemplate = \(id\) =>\n  request\(\{ method: 'POST', url: `\/api\/dashboard\/template\/\$\{id\}\/delete` \}\)/)
})

test('看板模板 API：发布态流转为 publish/unpublish/view 动作端点（POST /{id}/...）', () => {
  assert.match(source, /export const publishDashboardTemplate = \(id\) =>\n  request\(\{ method: 'POST', url: `\/api\/dashboard\/template\/\$\{id\}\/publish` \}\)/)
  assert.match(source, /export const unpublishDashboardTemplate = \(id\) =>\n  request\(\{ method: 'POST', url: `\/api\/dashboard\/template\/\$\{id\}\/unpublish` \}\)/)
  assert.match(source, /export const viewDashboardTemplate = \(id\) =>\n  request\(\{ method: 'POST', url: `\/api\/dashboard\/template\/\$\{id\}\/view` \}\)/)
})

test('看板模板 API：分页端点支持编码/名称/状态筛选与排序参数', () => {
  assert.match(source, /export const pageDashboardTemplates = \(query\) =>\n  request\(\{ method: 'GET', url: '\/api\/dashboard\/template\/page', params: query \}\)/)
})

// ---------- 3) 请求统一走 http.request 拆包层 ----------

test('看板模板 API：统一经 request() 发出（复用 Result<T> 拆包与错误处理）', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 8, '应恰好 8 个导出函数各发一次 request')
})
