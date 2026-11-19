import { describe, it, expect } from 'vitest'
import { flattenTree, collectSubtreeIds } from '../tree'

/** 构造测试树：菜单/部门等通用三字段结构。 */
const TREE = [
  { id: 1, label: '系统管理', children: [
    { id: 11, label: '用户管理' },
    { id: 12, label: '角色管理', children: [{ id: 121, label: '角色详情' }] }
  ] },
  { id: 2, label: '工作流' },
  null // 脏数据：null 节点应被跳过
]

describe('flattenTree', () => {
  it('应按深度缩进拍平并保留顺序', () => {
    const flat = flattenTree(TREE)
    expect(flat.map((n) => n.id)).toEqual([1, 11, 12, 121, 2])
    expect(flat[0].label).toBe('系统管理')
    expect(flat[1].label).toBe('\u3000用户管理') // 一层缩进（全角空格）
    expect(flat[3].label).toBe('\u3000\u3000角色详情') // 两层缩进
  })

  it('应支持自定义 idKey/labelKey/indentChar（输出键固定为 id + labelKey）', () => {
    const src = [{ key: 'a', name: '根', children: [{ key: 'b', name: '子' }] }]
    const flat = flattenTree(src, { idKey: 'key', nameKey: 'name', labelKey: 'text', indentChar: '-' })
    expect(flat).toEqual([{ id: 'a', text: '根' }, { id: 'b', text: '-子' }])
  })

  it('缺失展示名时应输出空串而非 undefined', () => {
    const flat = flattenTree([{ id: 'x' }])
    expect(flat).toEqual([{ id: 'x', label: '' }])
  })

  it('成环树应终止而非爆栈（环上节点跳过）', () => {
    const a = { id: 'a', label: 'A', children: [] }
    const b = { id: 'b', label: 'B', children: [a] }
    a.children.push(b) // 成环
    const flat = flattenTree([a])
    expect(flat.map((n) => n.id)).toEqual(['a', 'b'])
  })

  it('空输入应返回空数组', () => {
    expect(flattenTree([])).toEqual([])
    expect(flattenTree(null)).toEqual([])
  })
})

describe('collectSubtreeIds', () => {
  it('应收集目标节点及其全部子孙 id', () => {
    const ids = collectSubtreeIds(TREE, 12)
    expect([...ids].sort()).toEqual([12, 121])
  })

  it('叶子节点应只含自身', () => {
    expect([...collectSubtreeIds(TREE, 11)]).toEqual([11])
  })

  it('未命中 id 应返回空集合', () => {
    expect(collectSubtreeIds(TREE, 999).size).toBe(0)
  })

  it('成环树应终止（环上已在递归路径的节点不再重复收集）', () => {
    const a = { id: 'a', children: [] }
    const b = { id: 'b', children: [a] }
    a.children.push(b)
    const ids = collectSubtreeIds([a], 'b')
    expect([...ids]).toEqual(['b'])
  })
})
