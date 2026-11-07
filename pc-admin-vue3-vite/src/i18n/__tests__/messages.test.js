// i18n 消息目录静态校验（零依赖，node:test 运行）。
//
// 背景（E2E F4）：system 段内曾同时存在
//     user: { ... }        // 用户管理页的文案对象（system.user.xxx）
//     user: '用户#'        // 消息/公告页展示「用户#123」的标量前缀
// JS 对象字面量的重复键会静默覆盖——后者覆盖前者，于是所有 t('system.user.xxx')
// 都取不到值，页面直接显示原始键名。这类问题在运行时无法察觉（对象已合并），
// 因此改为在源码层做静态扫描：同一对象内出现重复键即失败。
//
// 覆盖：
//   1) 真实 messages.js 不得存在任何同层重复键；
//   2) zh-CN / en-US 两套语言键集合完全一致（防止只补一边）；
//   3) 扫描器自身有效性（合成样例必须被抓到，避免测试恒绿）。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import messages from '../messages.js'

const messagesPath = resolve(dirname(fileURLToPath(import.meta.url)), '../messages.js')

/**
 * 词法切分：只关心「字符串 / 标识符 / 大括号 / 中括号 / 冒号 / 逗号」，
 * 注释与其它标点直接跳过。字符串内容（可能含 { } 与引号转义）整体吞掉，
 * 避免把 '共 {n} 条' 里的花括号误当成对象边界。
 */
function tokenize(src) {
  const tokens = []
  const n = src.length
  let i = 0
  let line = 1
  while (i < n) {
    const c = src[i]
    if (c === '\n') {
      line++
      i++
      continue
    }
    if (c === '/' && src[i + 1] === '/') {
      while (i < n && src[i] !== '\n') i++
      continue
    }
    if (c === '/' && src[i + 1] === '*') {
      i += 2
      while (i < n && !(src[i] === '*' && src[i + 1] === '/')) {
        if (src[i] === '\n') line++
        i++
      }
      i += 2
      continue
    }
    if (c === "'" || c === '"' || c === '`') {
      const startLine = line
      let j = i + 1
      let value = ''
      while (j < n) {
        if (src[j] === '\\') {
          value += src[j + 1] ?? ''
          j += 2
          continue
        }
        if (src[j] === c) break
        if (src[j] === '\n') line++
        value += src[j]
        j++
      }
      tokens.push({ type: 'string', value, line: startLine })
      i = j + 1
      continue
    }
    if (c === '{' || c === '}' || c === '[' || c === ']' || c === ':' || c === ',') {
      tokens.push({ type: c, line })
      i++
      continue
    }
    if (/[A-Za-z0-9_$]/.test(c)) {
      let j = i
      while (j < n && /[A-Za-z0-9_$.]/.test(src[j])) j++
      tokens.push({ type: 'word', value: src.slice(i, j), line })
      i = j
      continue
    }
    i++
  }
  return tokens
}

/** 逐个对象帧收集键名，同层重复即记录（含首次出现行号，便于定位）。 */
function findDuplicateKeys(src) {
  const tokens = tokenize(src)
  const root = { kind: 'object', path: '(root)', keys: new Map() }
  const frames = [root]
  const dupes = []
  let pendingKey = null

  for (let k = 0; k < tokens.length; k++) {
    const tk = tokens[k]
    const frame = frames[frames.length - 1]

    if (tk.type === ':') {
      const prev = tokens[k - 1]
      if (frame.kind === 'object' && prev && (prev.type === 'string' || prev.type === 'word')) {
        pendingKey = prev.value
        if (frame.keys.has(pendingKey)) {
          dupes.push({
            path: `${frame.path}.${pendingKey}`,
            firstLine: frame.keys.get(pendingKey),
            againLine: prev.line
          })
        } else {
          frame.keys.set(pendingKey, prev.line)
        }
      }
      continue
    }
    if (tk.type === '{' || tk.type === '[') {
      // 顶层 `export default {` 没有键名，路径仍记为 (root)，不要拼成 (root)[]
      const path = pendingKey !== null
        ? `${frame.path}.${pendingKey}`
        : (frames.length === 1 ? frame.path : `${frame.path}[]`)
      frames.push({ kind: tk.type === '{' ? 'object' : 'array', path, keys: new Map() })
      pendingKey = null
      continue
    }
    if (tk.type === '}' || tk.type === ']') {
      if (frames.length > 1) frames.pop()
      pendingKey = null
      continue
    }
    if (tk.type === ',') {
      pendingKey = null
    }
  }
  return dupes
}

/** 收集指定语言的全部键路径（用于两端键集合比对）。 */
function collectPaths(node, prefix = '', out = new Set()) {
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      collectPaths(value, path, out)
    } else {
      out.add(path)
    }
  }
  return out
}

test('messages.js 不存在同一对象内的重复键（重复键会静默覆盖，如 system.user 被标量覆盖）', () => {
  const dupes = findDuplicateKeys(readFileSync(messagesPath, 'utf8'))
  const detail = dupes.map((d) => `${d.path} 首现 L${d.firstLine}，重复于 L${d.againLine}`).join('\n')
  assert.deepEqual(dupes, [], `发现重复键（后者会覆盖前者）：\n${detail}`)
})

test('扫描器自校验：合成样例中的重复键必须被抓出', () => {
  const sample = `
    export default {
      'zh-CN': {
        system: {
          user: { title: 'A', name: 'B' },
          user: '用户#',
          datascope: { all: { label: 'C' } }
        }
      }
    }`
  const dupes = findDuplicateKeys(sample)
  assert.equal(dupes.length, 1, '应且仅应命中一例重复键')
  assert.equal(dupes[0].path, '(root).zh-CN.system.user')
})

test('扫描器不得把字符串内的花括号当成对象边界', () => {
  const sample = `
    export default {
      'zh-CN': {
        a: '共 {n} 条',
        b: { c: 'x}' }
      }
    }`
  assert.deepEqual(findDuplicateKeys(sample), [])
})

test('zh-CN 与 en-US 的键集合完全一致（防止只补一端）', () => {
  const zh = collectPaths(messages['zh-CN'])
  const en = collectPaths(messages['en-US'])
  const missingInEn = [...zh].filter((k) => !en.has(k))
  const missingInZh = [...en].filter((k) => !zh.has(k))
  assert.deepEqual(missingInEn, [], `en-US 缺失键：${missingInEn.join(', ')}`)
  assert.deepEqual(missingInZh, [], `zh-CN 缺失键：${missingInZh.join(', ')}`)
})

test('system.user 仍为对象（用户管理页文案集合），用户前缀改用 system.userPrefix', () => {
  for (const locale of ['zh-CN', 'en-US']) {
    assert.equal(typeof messages[locale].system.user, 'object', `${locale}: system.user 应为对象`)
    assert.equal(typeof messages[locale].system.userPrefix, 'string', `${locale}: system.userPrefix 应为字符串`)
  }
})
