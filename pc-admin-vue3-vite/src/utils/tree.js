/**
 * 树结构工具：拍平为下拉选项 / 收集子树 id。
 *
 * <p>此前 UserView 与 MessageView 各有一份 flattenOrgs()，MenuView 与
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
  const walk = (list, depth) => {
    for (const n of list || []) {
      const prefix = depth > 0 ? indentChar.repeat(depth) : ''
      out.push({ id: n[idKey], [labelKey]: prefix + (n[nameKey] ?? '') })
      if (n[childrenKey]?.length) walk(n[childrenKey], depth + 1)
    }
  }
  walk(nodes, 0)
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
  const collect = (node) => {
    for (const c of node[childrenKey] || []) {
      set.add(c[idKey])
      collect(c)
    }
  }
  const walk = (list) => {
    for (const n of list || []) {
      if (n[idKey] === id) {
        set.add(n[idKey])
        collect(n)
        return true
      }
      if (n[childrenKey]?.length && walk(n[childrenKey])) return true
    }
    return false
  }
  walk(nodes)
  return set
}
