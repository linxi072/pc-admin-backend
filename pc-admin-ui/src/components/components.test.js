import { describe, expect, it } from 'vitest'
import { createSSRApp, h } from 'vue'
import { renderToString } from '@vue/server-renderer'
import StatCard from './StatCard.vue'
import CopyText from './CopyText.vue'

// 组件库 smoke 测试：验证 SFC 可被 Vite/Vue 正常编译、SSR 渲染无异常、关键 props 生效。
// BaseChart 依赖浏览器 ECharts/ResizeObserver，SSR 场景跳过，交由主应用构建验证。

async function render(component, props, slots = {}) {
  const app = createSSRApp({ render: () => h(component, props, slots) })
  // SSR 环境未加载 Element Plus：为 el-button / el-icon 注册透传 stub，
  // 让按钮与图标节点可渲染，避免 Vue 解析告警影响断言（仅测试基建，不改组件）。
  app.component('el-button', { inheritAttrs: true, render() { return h('button', this.$attrs, this.$slots.default?.()) } })
  app.component('el-icon', { inheritAttrs: true, render() { return h('span', this.$attrs, this.$slots.default?.()) } })
  return renderToString(app)
}

describe('StatCard（库包组件）', () => {
  it('渲染 label / 数值 / 单位', async () => {
    const html = await render(StatCard, { label: '今日订单', value: 1234, unit: '单' })
    expect(html).toContain('今日订单')
    expect(html).toContain('1,234')
    expect(html).toContain('单')
  })

  it('loading 时渲染骨架且不渲染数值', async () => {
    const html = await render(StatCard, { label: '耗时', value: 100, loading: true })
    expect(html).toContain('stat-card__skeleton')
    expect(html).not.toContain('100')
  })

  it('空值渲染占位符', async () => {
    const html = await render(StatCard, { label: '空', value: null })
    expect(html).toContain('-')
  })
})

describe('CopyText（库包组件）', () => {
  it('默认展示文本与复制按钮', async () => {
    const html = await render(CopyText, { text: 'abc123' })
    expect(html).toContain('abc123')
    expect(html).toContain('copy-text__btn')
  })

  it('showText=false 时仅渲染按钮', async () => {
    const html = await render(CopyText, { text: 'secret', showText: false })
    expect(html).not.toContain('secret')
    expect(html).toContain('copy-text__btn')
  })

  it('display 覆盖脱敏展示', async () => {
    const html = await render(CopyText, { text: '13800138000', display: '138****8000' })
    expect(html).toContain('138****8000')
    expect(html).not.toContain('13800138000')
  })
})
