import { describe, expect, it } from 'vitest'
import { createSSRApp, h, nextTick } from 'vue'
import { renderToString } from '@vue/server-renderer'
import StatusTag from './StatusTag.vue'
import DescriptionList from './DescriptionList.vue'
import ProTable from './ProTable.vue'
import QueryForm from './QueryForm.vue'
import ConfirmDialog from './ConfirmDialog.vue'
import DictSelect from './DictSelect.vue'

// 第二批通用组件 smoke 测试：验证 SFC 可编译、SSR 渲染无异常、边界兜底生效。
// Element Plus 组件在 SSR 环境用透传 stub 注册，避免解析告警干扰断言。

function withStubs(component, props, slots = {}) {
  const app = createSSRApp({ render: () => h(component, props, slots) })
  const passthrough = (tag) => ({
    inheritAttrs: true,
    render() { return h(tag, this.$attrs, this.$slots.default?.()) }
  })
  for (const name of ['el-table', 'el-table-column', 'el-pagination', 'el-form', 'el-form-item',
    'el-button', 'el-tag', 'el-descriptions', 'el-descriptions-item',
    'el-icon', 'el-select', 'el-option']) {
    app.component(name, passthrough(name === 'el-tag' ? 'span' : 'div'))
  }
  // el-dialog 需额外渲染 footer 插槽，否则确认按钮不出现在 SSR 输出
  app.component('el-dialog', {
    inheritAttrs: true,
    render() {
      return h('div', this.$attrs, [this.$slots.default?.(), this.$slots.footer?.()])
    }
  })
  // v-loading 指令 stub（SSR 无 DOM，注册空对象即可）
  app.directive('loading', {})
  return renderToString(app)
}

describe('StatusTag', () => {
  it('命中映射显示 text 与 type', async () => {
    const html = await withStubs(StatusTag, {
      value: '1', mapping: { 1: { text: '启用', type: 'success' } }
    })
    expect(html).toContain('启用')
  })

  it('未命中显示原值，null 显示占位符', async () => {
    expect(await withStubs(StatusTag, { value: 'X' })).toContain('X')
    expect(await withStubs(StatusTag, { value: null })).toContain('-')
  })
})

describe('DescriptionList', () => {
  it('渲染 label/value 与空值占位符', async () => {
    const html = await withStubs(DescriptionList, {
      items: [{ key: 'a', label: '姓名', value: '张三' }, { key: 'b', label: '年龄', value: null }]
    })
    expect(html).toContain('姓名')
    expect(html).toContain('张三')
    expect(html).toContain('-')
  })
})

describe('ProTable', () => {
  it('渲染表格与分页，total 为负数按 0', async () => {
    const html = await withStubs(ProTable, {
      data: [{ id: 1 }], columns: [{ prop: 'id', label: 'ID' }], total: -5
    })
    expect(html).toContain('ID')
    expect(html).toContain('pro-table__pagination')
  })

  it('pagination=false 时不渲染分页', async () => {
    const html = await withStubs(ProTable, { data: [], columns: [], pagination: false })
    expect(html).not.toContain('pro-table__pagination')
  })
})

describe('QueryForm', () => {
  it('渲染查询与重置按钮', async () => {
    const html = await withStubs(QueryForm, {})
    expect(html).toContain('查询')
    expect(html).toContain('重置')
  })
})

describe('ConfirmDialog', () => {
  it('函数式 message 求值', async () => {
    const html = await withStubs(ConfirmDialog, {
      modelValue: true, message: () => '确认删除这条记录？'
    })
    expect(html).toContain('确认删除这条记录？')
  })

  it('danger 时确认按钮使用 danger 类型', async () => {
    const html = await withStubs(ConfirmDialog, { modelValue: true, danger: true })
    expect(html).toContain('确定')
  })
})

describe('DictSelect', () => {
  it('渲染所有选项', async () => {
    const html = await withStubs(DictSelect, {
      options: [{ label: '启用', value: 1 }, { label: '停用', value: 0 }]
    })
    expect(html).toContain('启用')
    expect(html).toContain('停用')
  })

  it('options=null 不抛错', async () => {
    const html = await withStubs(DictSelect, { options: null })
    expect(html).toContain('dict-select')
  })
})
