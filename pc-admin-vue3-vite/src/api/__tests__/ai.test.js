// R3 AI 脚手架前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-ai AiScaffoldController
//   POST /api/ai/scaffold/generate（权限 ai:scaffold:generate）。
//
// 策略：与 cms.test.js 一致地直接读取源码文本做静态断言，
//       校验导出函数集合、HTTP 方法与 URL 前缀，防止前后端契约漂移。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../ai.js'), 'utf8')

test('AI API：导出函数与后端端点一一对应', () => {
  const expected = ['generateScaffold']
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

test('AI API：生成端点使用 /api/ai/scaffold/generate 前缀', () => {
  const urls = source.match(/\/api\/ai\/scaffold\/generate/g) || []
  assert.ok(urls.length >= 1, '生成 URL 前缀至少出现 1 次')
})

test('AI API：统一经 request() 发出', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 1, '应恰好 1 个导出函数各发一次 request')
})
