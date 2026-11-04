// R12 微前端契约测试（零依赖，node:test 运行）。
//
// 覆盖：
//   1) 子应用 mount/unmount 契约静态断言（hello/notes）；
//   2) 主壳 glob 扫描与生命周期调度断言；
//   3) V79 菜单种子幂等与权限码复用断言（与后端契约同源校验）。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))

function read(rel) {
  return readFileSync(resolve(here, rel), 'utf8')
}

// ---------- 1) 子应用契约 ----------

for (const appName of ['hello', 'notes']) {
  test(`微前端子应用 ${appName}：导出 mount/unmount 契约`, () => {
    const source = read(`../../../micro-apps/${appName}/index.js`)
    assert.match(source, /export default \{/, '应导出默认对象')
    assert.match(source, /name: '[a-z][a-z0-9-]*'/, '应声明 name 标识')
    assert.match(source, /mount\(container,\s*ctx\)/, '应实现 mount(container, ctx)')
    assert.match(source, /unmount\(/, '应实现 unmount')
    assert.match(source, /data-micro-app/, '根节点应带 data-micro-app 标记（便于调试与隔离断言）')
  })

  test(`微前端子应用 ${appName}：unmount 自清理根节点`, () => {
    const source = read(`../../../micro-apps/${appName}/index.js`)
    assert.match(source, /\.remove\(\)/, 'unmount 应 remove 根节点')
  })
}

test('微前端子应用：不依赖任何第三方包（零 import，保证离线可构建）', () => {
  for (const appName of ['hello', 'notes']) {
    const source = read(`../../../micro-apps/${appName}/index.js`)
    assert.equal((source.match(/^import\s/m) || []).length, 0, `${appName} 不得含 import 语句`)
  }
})

// ---------- 2) 主壳调度 ----------

test('微前端主壳：glob 扫描 micro-apps 目录并按契约调度', () => {
  const source = read('../../micro/MicroFrontendHost.vue')
  assert.match(source, /import\.meta\.glob\('\.\.\/\.\.\/micro-apps\/\*\/index\.js', \{ eager: false \}\)/)
  assert.match(source, /definition\.mount\(/, '主壳调用子应用 mount')
  assert.match(source, /unmountCurrent/, '主壳持有卸载调度（生命周期对称）')
  assert.match(source, /innerHTML = ''/, '主壳兜底清空容器，防遗留 DOM')
  assert.match(source, /router\.replace\(\{ path: route\.path, query: \{ app: name \} \}\)/, '挂载状态同步 URL query')
  assert.match(source, /onBeforeUnmount\(unmountCurrent\)/, '组件卸载时必须卸载子应用')
})

// ---------- 3) V79 菜单种子（后端契约同源校验） ----------

test('V79 迁移：微前端菜单种子复用 system:monitor:read 且幂等登记', () => {
  const source = read(
    '../../../../../java-admin-framework/scaffold-admin/src/main/resources/db/migration/V79__micro_frontend.sql'
  )
  assert.match(source, /'\/micro\/host'/, '路由路径 /micro/host 与前端一致')
  assert.match(source, /'micro\/MicroFrontendHost'/, '组件路径与前端一致')
  assert.match(source, /'system:monitor:read'/, '权限码复用已登记的 system:monitor:read（health 先例）')
  assert.match(source, /INSERT IGNORE INTO sys_menu/, '菜单幂等（INSERT IGNORE）')
  assert.match(source, /SELECT 0, 1, 9802/, 'SUPER_ADMIN 绑定幂等')
})
