// 视图引用一致性与同名消歧回归测试（零依赖，node:test 运行）。
//
// 背景（去重 D-06 / 补齐 F-13）：src/views 下曾存在 3 组同名 basename 的 .vue 文件
//   DashboardView.vue（工作台 / 报表大屏）、MessageView.vue（IM 会话 / 站内信）、
//   MonitorView.vue（系统监控 / 流程监控）。同名文件靠目录区分，IDE 标签页、
//   日志、错误堆栈、路由 import 都极易指错对象；router 的动态 import 一旦写错路径，
//   Vite 又只在真正懒加载到该路由时才报错（构建期不易发现）。
//
// 本测试把两条不变量固化为门禁：
//   1) router 中所有 @/views/** 动态 import 目标必须真实存在；
//   2) src/views 下不得再有同名 basename 的 .vue 文件（防再次引入命名混淆）。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, resolve, join, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../../..')
const src = resolve(root, 'src')
const views = resolve(src, 'views')

/** 递归收集 views 下全部 .vue 相对路径。 */
function collectViews(dir = views, out = []) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry)
    if (statSync(p).isDirectory()) collectViews(p, out)
    else if (p.endsWith('.vue')) out.push(p)
  }
  return out
}

test('router 中所有 @/views/** 动态 import 目标都真实存在', () => {
  const router = readFileSync(resolve(src, 'router/index.js'), 'utf8')
  const re = /import\(\s*['"](@\/views\/[^'"]+\.vue)['"]\s*\)/g
  const targets = [...router.matchAll(re)].map((m) => m[1])
  assert.ok(targets.length > 50, `应扫描到足量路由组件，实际 ${targets.length}`)
  const missing = targets.filter((t) => !existsSync(resolve(src, t.replace(/^@\//, ''))))
  assert.deepEqual(missing, [], `router 指向了不存在的视图：${missing.join(', ')}`)
})

test('src/views 下不存在同名 basename 的 .vue 文件', () => {
  const files = collectViews()
  const byBase = new Map()
  for (const f of files) {
    const b = basename(f)
    if (!byBase.has(b)) byBase.set(b, [])
    byBase.get(b).push(f.replace(`${root}/`, ''))
  }
  const dups = [...byBase.entries()].filter(([, v]) => v.length > 1)
  const detail = dups.map(([b, v]) => `${b}: ${v.join(' | ')}`).join('\n')
  assert.deepEqual(dups, [], `存在同名视图（易指错对象）：\n${detail}`)
})

test('消歧后的 6 个新文件均存在，旧同名文件均不存在', () => {
  const renamed = [
    'views/WorkbenchView.vue',
    'views/report/OpsDashboardView.vue',
    'views/im/ImConversationView.vue',
    'views/system/StationMessageView.vue',
    'views/system/RuntimeMonitorView.vue',
    'views/workflow/WorkflowMonitorView.vue'
  ]
  const oldNames = [
    'views/DashboardView.vue',
    'views/report/DashboardView.vue',
    'views/im/MessageView.vue',
    'views/system/MessageView.vue',
    'views/system/MonitorView.vue',
    'views/workflow/MonitorView.vue'
  ]
  for (const f of renamed) {
    assert.ok(existsSync(resolve(src, f)), `缺少改名后的文件 ${f}`)
  }
  for (const f of oldNames) {
    assert.equal(existsSync(resolve(src, f)), false, `旧同名文件仍存在 ${f}`)
  }
})

test('router 仍保留 dashboard / reportDashboard 两个路由名（兼容菜单 routePath）', () => {
  const router = readFileSync(resolve(src, 'router/index.js'), 'utf8')
  assert.match(router, /name: 'dashboard'/)
  assert.match(router, /name: 'reportDashboard'/)
  assert.match(router, /import\('@\/views\/WorkbenchView\.vue'\)/)
  assert.match(router, /import\('@\/views\/report\/OpsDashboardView\.vue'\)/)
})

test('NoticeView 内嵌的站内信组件引用已同步到新名', () => {
  const notice = readFileSync(resolve(views, 'system/NoticeView.vue'), 'utf8')
  assert.match(notice, /import StationMessageView from '\.\/StationMessageView\.vue'/)
  assert.match(notice, /<StationMessageView embedded \/>/)
  assert.doesNotMatch(notice, /from '\.\/MessageView\.vue'/)
  assert.doesNotMatch(notice, /<MessageView\b/)
})
