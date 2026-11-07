/**
 * 树结构工具：拍平为下拉选项 / 收集子树 id。
 *
 * <p>此前用户页与站内信页各有一份 flattenOrgs()，MenuView 与
 * DepartmentView 各有一份 subtreeIds()，实现分散且缩进规则易漂移，此处收敛。</p>
 */

/**
 * 拍平树为「带层级缩进」的下拉选项数组（前序遍历，父节点在前）。
 *
 * @param {Array} nodes 树节点数组
 * @param {object} opts 配置项
 * @param {string} [opts.idKey='id'] 作为选项 value 的字段
 * @param {string} [opts.nameKey='label'] 读取展示名的字段
 * @param {string} [opts.labelKey='label'] 写入选项展示名的字段
 * @param {string} [opts.childrenKey='children'] 子节点字段
 * @param {string} [opts.indentChar='　'] 每层缩进字符（全角空格，避免被 HTML 折叠）
 * @returns {Array<{ id: any, [labelKey]: string }>}
 */
export function flattenTree(nodes, opts = {}) {
  const {
    idKey = 'id',
    nameKey = 'label',
    labelKey = 'label',
    childrenKey = 'children',
    indentChar = '　'
  } = opts
  const out = []
  // path 记录「当前递归路径」上的节点对象：接口异常或本地缓存被污染时可能返回自引用/
  // 成环的树，无环检测会让递归无限展开直至爆栈（整个下拉/树组件白屏）。合法树无重复节点，
  // 因此该守卫不改变正常输出；path 在回溯时删除，同一对象出现在不同分支仍按原语义各自渲染。
  const walk = (list, depth, path) => {
    for (const n of list || []) {
      if (n == null || path.has(n)) continue
      const prefix = depth > 0 ? indentChar.repeat(depth) : ''
      out.push({ id: n[idKey], [labelKey]: prefix + (n[nameKey] ?? '') })
      if (n[childrenKey]?.length) {
        path.add(n)
        walk(n[childrenKey], depth + 1, path)
        path.delete(n)
      }
    }
  }
  walk(nodes, 0, new Set())
  return out
}

/**
 * 收集指定节点及其全部子孙的 id 集合。
 *
 * <p>用于「上级菜单/部门」下拉中排除自身与子孙，防止选出层级环路。</p>
 *
 * @param {Array} nodes 树节点数组
 * @param {any} id 目标节点 id
 * @param {object} [opts] 同 {@link flattenTree} 的 idKey / childrenKey
 * @returns {Set<any>} 目标节点 + 全部子孙 id（未命中时为空 Set）
 */
export function collectSubtreeIds(nodes, id, opts = {}) {
  const { idKey = 'id', childrenKey = 'children' } = opts
  const set = new Set()
  // 同 flattenTree：以「当前递归路径」集合阻断自引用/成环节点，避免 collect 无限递归。
  const path = new Set()
  const collect = (node) => {
    for (const c of node[childrenKey] || []) {
      if (c == null || path.has(c)) continue
      path.add(c)
      set.add(c[idKey])
      collect(c)
      path.delete(c)
    }
  }
  const walk = (list) => {
    for (const n of list || []) {
      if (n == null || path.has(n)) continue
      if (n[idKey] === id) {
        set.add(n[idKey])
        path.add(n)
        collect(n)
        return true
      }
      if (n[childrenKey]?.length) {
        path.add(n)
        if (walk(n[childrenKey])) return true
        path.delete(n)
      }
    }
    return false
  }
  walk(nodes)
  return set
}
