// R5 CMS 前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-cms
//   CmsCategoryController (/api/cms/category) + CmsArticleController (/api/cms/article)。
//
// 策略：与 result.test.js 一致地直接读取源码文本做静态断言，
//       校验导出函数集合、HTTP 方法与 URL 前缀，防止前后端契约漂移。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../cms.js'), 'utf8')

// ---------- 1) 导出函数集合与后端控制器端点一一对应 ----------

test('CMS API：导出函数与后端端点一一对应', () => {
  const expected = [
    // 栏目 CmsCategoryController
    'createCategory',
    'updateCategory',
    'deleteCategory',
    'getCategory',
    'listCategories',
    'treeCategories',
    // 文章 CmsArticleController
    'createArticle',
    'updateArticle',
    'deleteArticle',
    'getArticle',
    'publishArticle',
    'offlineArticle',
    'viewArticle',
    'pageArticles'
  ]
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

// ---------- 2) HTTP 方法与 URL 前缀 ----------

test('CMS API：栏目端点使用 /api/cms/category 前缀', () => {
  const categoryUrls = source.match(/\/api\/cms\/category/g) || []
  assert.ok(categoryUrls.length >= 5, '栏目 URL 前缀至少出现 5 次（create/update/get/list/tree 共享前缀）')
})

test('CMS API：文章端点使用 /api/cms/article 前缀', () => {
  const articleUrls = source.match(/\/api\/cms\/article/g) || []
  assert.ok(articleUrls.length >= 8, '文章 URL 前缀至少出现 8 次（create/update/get/publish/offline/view/page/delete）')
})

test('CMS API：删除类操作与动作类操作使用 POST（对齐后端非 RESTful 删除/动作端点）', () => {
  assert.match(source, /export const deleteArticle = \(id\) => request\(\{ method: 'POST', url: `\/api\/cms\/article\/\$\{id\}\/delete` \}\)/)
  assert.match(source, /export const publishArticle = \(id\) => request\(\{ method: 'POST', url: `\/api\/cms\/article\/\$\{id\}\/publish` \}\)/)
  assert.match(source, /export const offlineArticle = \(id\) => request\(\{ method: 'POST', url: `\/api\/cms\/article\/\$\{id\}\/offline` \}\)/)
  assert.match(source, /export const viewArticle = \(id\) => request\(\{ method: 'POST', url: `\/api\/cms\/article\/\$\{id\}\/view` \}\)/)
})

test('CMS API：栏目删除使用 DELETE /{id}', () => {
  assert.match(source, /export const deleteCategory = \(id\) => request\(\{ method: 'DELETE', url: `\/api\/cms\/category\/\$\{id\}` \}\)/)
})

// ---------- 3) 请求统一走 http.request 拆包层 ----------

test('CMS API：统一经 request() 发出（复用 Result<T> 拆包与错误处理）', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 14, '应恰好 14 个导出函数各发一次 request')
})
