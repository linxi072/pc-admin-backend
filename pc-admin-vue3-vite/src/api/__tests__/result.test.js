// 前后端联调统一契约：前端拆包层测试（零依赖，node:test 运行）。
//
// 唯一事实来源：../../../java-admin-framework/scripts/api-contract.json
//   （与后端 ApiContractConsistencyTest 共用同一份，确保前后端数据格式与业务逻辑一致）。
//
// 覆盖：成功码 "0"/0 兼容、数据交互（data 透传/空 data）、错误码处理（抛 ResultError 且携带 code/data）、
//       契约 successCodes 与 errorCodes 双向一致、边界（null/undefined/非对象/数字 0 成功）。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { isSuccessResult, unwrapResult, ResultError } from '../result.js'

// 从本文件目录向上查找仓库根级 api-contract.json（与后端同一份）
function findContract() {
  let dir = dirname(fileURLToPath(import.meta.url))
  while (true) {
    const cand = resolve(dir, 'java-admin-framework/scripts/api-contract.json')
    if (existsSync(cand)) return cand
    const parent = dirname(dir)
    if (parent === dir) break
    dir = parent
  }
  throw new Error('未找到 api-contract.json（向上查找失败）')
}

const contractPath = findContract()
const contract = JSON.parse(readFileSync(contractPath, 'utf8'))

const successCodes = contract.successCodes // ["0", 0]
const errorCodes = contract.errorCodes // [{code, httpStatus, messageZh, messageEn}]

// ---------- 1) 成功码兼容（正常流程 + 契约一致性） ----------

test('isSuccessResult：契约 successCodes 中每个值都被识别为成功', () => {
  for (const c of successCodes) {
    assert.equal(isSuccessResult({ code: c, message: 'OK', data: null }), true, `成功码应为真: ${c}`)
  }
})

test('isSuccessResult：字符串 "0" 与数字 0 都判成功（修复仅判 === "0" 的 bug）', () => {
  assert.equal(isSuccessResult({ code: '0' }), true)
  assert.equal(isSuccessResult({ code: 0 }), true)
})

test('isSuccessResult：错误码 / null / undefined / 非对象均判失败', () => {
  for (const e of errorCodes) {
    assert.equal(isSuccessResult({ code: e.code }), false, `错误码应判失败: ${e.code}`)
  }
  assert.equal(isSuccessResult(null), false)
  assert.equal(isSuccessResult(undefined), false)
  assert.equal(isSuccessResult(42), false)
  assert.equal(isSuccessResult({}), false) // 缺 code
})

// ---------- 2) 数据交互正确性（请求→响应往返的拆包语义） ----------

test('unwrapResult：成功返回 data，且空 data(null) 也正确透传', () => {
  assert.equal(unwrapResult({ code: '0', message: 'OK', data: { id: '1', name: 'x' } }).name, 'x')
  assert.equal(unwrapResult({ code: 0, message: 'OK', data: null }), null)
  assert.deepEqual(unwrapResult({ code: '0', message: 'OK', data: [1, 2, 3] }), [1, 2, 3])
})

test('unwrapResult：失败抛出 ResultError，且携带 code 与 data', () => {
  const body = { code: 'COMMON_404', message: '资源不存在', data: { id: '9999' } }
  let err
  try {
    unwrapResult(body)
    assert.fail('应当抛出 ResultError')
  } catch (e) {
    err = e
  }
  assert.ok(err instanceof ResultError, '抛出类型为 ResultError')
  assert.equal(err.code, 'COMMON_404')
  assert.equal(err.message, '资源不存在')
  assert.deepEqual(err.data, { id: '9999' })
})

test('unwrapResult：缺 message 时回退默认文案', () => {
  let err
  try {
    unwrapResult({ code: 'COMMON_500' })
  } catch (e) {
    err = e
  }
  assert.equal(err.message, '请求失败')
  assert.equal(err.code, 'COMMON_500')
})

// ---------- 3) 错误码处理：前端对契约全部错误码一致拒绝 ----------

test('错误码处理：每个契约错误码都被判为失败且能抛出对应 ResultError', () => {
  for (const e of errorCodes) {
    const body = { code: e.code, message: e.messageZh, data: null }
    assert.equal(isSuccessResult(body), false, `契约错误码应判失败: ${e.code}`)
    let err
    try {
      unwrapResult(body)
    } catch (ex) {
      err = ex
    }
    assert.ok(err instanceof ResultError, `${e.code} 抛 ResultError`)
    assert.equal(err.code, e.code)
    assert.equal(err.message, e.messageZh)
  }
})

// ---------- 4) 契约结构健全性（防止契约文件本身被破坏） ----------

test('契约结构：envelope 必含 code/message/data/traceId/timestamp', () => {
  const req = contract.envelope.required
  for (const f of ['code', 'message', 'data', 'traceId', 'timestamp']) {
    assert.ok(req.includes(f), `envelope 必须声明字段 ${f}`)
  }
})

test('契约结构：successCodes 含 "0" 与 0，errorCodes 为 9 项', () => {
  assert.ok(successCodes.includes('0'), 'successCodes 含字符串 "0"')
  assert.ok(successCodes.includes(0), 'successCodes 含数字 0')
  assert.equal(errorCodes.length, 9, '通用错误码应为 9 个')
})

test('契约结构：每个错误码具备 code/httpStatus/messageZh/messageEn', () => {
  for (const e of errorCodes) {
    assert.equal(typeof e.code, 'string')
    assert.equal(typeof e.httpStatus, 'number')
    assert.equal(typeof e.messageZh, 'string')
    assert.equal(typeof e.messageEn, 'string')
    assert.ok(e.messageZh.length > 0 && e.messageEn.length > 0)
  }
})
