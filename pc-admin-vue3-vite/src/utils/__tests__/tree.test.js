// 树工具边界回归测试（零依赖，node:test 运行）。
//
// 背景：flattenTree / collectSubtreeIds 用于「上级菜单/部门」下拉与子树排除。
// 接口异常或本地缓存被污染时可能返回自引用/成环的节点，原递归实现会无限展开直至爆栈，
// 表现为整个下拉组件白屏。本测试锁定「成环数据能安全终止 + 合法树输出完全不变」。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { flattenTree, collectSubtreeIds } from '../tree.js'

const flatOpts = { nameKey: 'label', indentChar: '-' }

test('flattenTree：正常树前序拍平并带层级缩进', () => {
  const tree = [{
    id: 1,
    label: 'root',
    children: [
      { id: 2, label: 'a', children: [{ id: 4, label: 'a1', children: [] }] },
      { id: 3, label: 'b', children: [] }
    ]
  }]
  const out = flattenTree(tree, flatOpts)
  assert.deepEqual(out.map((o) => o.id), [1, 2, 4, 3])
  assert.deepEqual(out.map((o) => o.label), ['root', '-a', '--a1', '-b'])
})

test('flattenTree：同一对象出现在不同分支仍各自渲染（合法输入零回归）', () => {
  const shared = { id: 3, label: 'shared', children: [] }
  const tree = [
    { id: 1, label: 'a', children: [shared] },
    { id: 2, label: 'b', children: [shared] }
  ]
  assert.deepEqual(flattenTree(tree, flatOpts).map((o) => o.id), [1, 3, 2, 3])
})

test('flattenTree：自引用 / 成环节点安全终止，不爆栈', () => {
  const root = { id: 1, label: 'root', children: [] }
  const child = { id: 2, label: 'child', children: [] }
  root.children.push(child)
  child.children.push(root) // 子指回父，形成环
  child.children.push(child) // 自引用

  const out = flattenTree([root], flatOpts)
  assert.deepEqual(out.map((o) => o.id), [1, 2])
})

test('collectSubtreeIds：正常树收集目标节点与全部子孙', () => {
  const tree = [{
    id: 1,
    children: [
      { id: 2, children: [{ id: 4, children: [] }] },
      { id: 3, children: [] }
    ]
  }]
  const ids = collectSubtreeIds(tree, 2)
  assert.deepEqual([...ids].sort(), [2, 4])
  assert.equal(collectSubtreeIds(tree, 999).size, 0)
})

test('collectSubtreeIds：成环数据安全终止且 id 集合正确', () => {
  const root = { id: 1, children: [] }
  const child = { id: 2, children: [] }
  root.children.push(child)
  child.children.push(root, child)

  assert.deepEqual([...collectSubtreeIds([root], 1)].sort(), [1, 2])
  // 目标是 child 时，root 是它的祖先（已在递归路径上），环回边应被跳过
  assert.deepEqual([...collectSubtreeIds([root], 2)].sort(), [2])
})
