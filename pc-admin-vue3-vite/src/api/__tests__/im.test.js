// R6 IM 前端 API 契约测试（零依赖，node:test 运行）。
//
// 对齐后端：java-admin-framework scaffold-im
//   ImConversationController (/api/im/conversation) + ImMessageController (/api/im/message)。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const source = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../im.js'), 'utf8')

// ---------- 1) 导出函数集合与后端控制器端点一一对应 ----------

test('IM API：导出函数与后端端点一一对应', () => {
  const expected = [
    // 会话 ImConversationController
    'createConversation',
    'listMyConversations',
    'getConversation',
    'addMember',
    'removeMember',
    'quitConversation',
    'deleteConversation',
    // 消息 ImMessageController
    'sendMessage',
    'listConversationMessages',
    'getMessage',
    'recallMessage',
    'markConversationRead',
    'unreadCount'
  ]
  for (const fn of expected) {
    assert.ok(new RegExp(`export const ${fn} =`).test(source), `缺少导出函数: ${fn}`)
  }
})

// ---------- 2) HTTP 方法与 URL 前缀 ----------

test('IM API：会话端点使用 /api/im/conversation 前缀', () => {
  const urls = source.match(/\/api\/im\/conversation/g) || []
  assert.ok(urls.length >= 7, '会话 URL 前缀至少出现 7 次')
})

test('IM API：消息端点使用 /api/im/message 前缀', () => {
  const urls = source.match(/\/api\/im\/message/g) || []
  assert.ok(urls.length >= 6, '消息 URL 前缀至少出现 6 次')
})

test('IM API：动作类端点使用 POST（create/members/remove/quit/delete/send/recall/read）', () => {
  assert.match(source, /export const createConversation = \(body\) => request\(\{ method: 'POST', url: '\/api\/im\/conversation\/create', data: body \}\)/)
  assert.match(source, /export const removeMember = \(id, userId\) =>\n  request\(\{ method: 'POST', url: `\/api\/im\/conversation\/\$\{id\}\/members\/\$\{userId\}\/remove` \}\)/)
  assert.match(source, /export const quitConversation = \(id\) => request\(\{ method: 'POST', url: `\/api\/im\/conversation\/\$\{id\}\/quit` \}\)/)
  assert.match(source, /export const deleteConversation = \(id\) => request\(\{ method: 'POST', url: `\/api\/im\/conversation\/\$\{id\}\/delete` \}\)/)
  assert.match(source, /export const sendMessage = \(body\) => request\(\{ method: 'POST', url: '\/api\/im\/message\/send', data: body \}\)/)
  assert.match(source, /export const recallMessage = \(id\) => request\(\{ method: 'POST', url: `\/api\/im\/message\/\$\{id\}\/recall` \}\)/)
  assert.match(source, /export const markConversationRead = \(conversationId\) =>\n  request\(\{ method: 'POST', url: `\/api\/im\/message\/conversation\/\$\{conversationId\}\/read` \}\)/)
})

test('IM API：addMember 经 query 传 userId（对齐后端 @RequestParam Long userId）', () => {
  assert.match(source, /export const addMember = \(id, userId\) =>\n  request\(\{ method: 'POST', url: `\/api\/im\/conversation\/\$\{id\}\/members`, params: \{ userId \} \}\)/)
})

// ---------- 3) 请求统一走 http.request 拆包层 ----------

test('IM API：统一经 request() 发出（复用 Result<T> 拆包与错误处理）', () => {
  assert.match(source, /import \{ request \} from '\.\/http'/)
  const requestCalls = source.match(/request\(/g) || []
  assert.equal(requestCalls.length, 13, '应恰好 13 个导出函数各发一次 request')
})
