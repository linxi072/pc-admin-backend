// 前端内置 Mock 数据层：完整模拟 java-admin-framework 的接口与 DTO 形态。
// request() 在 USE_MOCK=true 时调用本模块；返回值为「已拆包」的数据（与真实拦截器一致）。
// 仅用于离线演示；将 src/api/config.js 的 USE_MOCK 置 false 即对接真实后端（无需改动业务代码）。

function now() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function bizError(code, message) {
  return Object.assign(new Error(message), { code })
}

let seq = 100

const db = {
  users: [
    { id: 1, username: 'admin', displayName: '超级管理员', mobile: '13800000000', email: 'admin@example.com', primaryOrgId: 1, status: 'ACTIVE', roleCodes: ['SUPER_ADMIN'], createdAt: '2026-01-01 10:00:00' },
    { id: 2, username: 'zhangsan', displayName: '张三', mobile: '13800000001', email: 'zhangsan@example.com', primaryOrgId: 1, status: 'ACTIVE', roleCodes: ['OPERATOR'], createdAt: '2026-02-01 09:00:00' },
    { id: 3, username: 'lisi', displayName: '李四', mobile: '13800000002', email: 'lisi@example.com', primaryOrgId: 2, status: 'DISABLED', roleCodes: ['AUDITOR'], createdAt: '2026-03-01 09:00:00' }
  ],
  roles: [
    { id: 1, roleCode: 'SUPER_ADMIN', roleName: '超级管理员', roleType: 'SYSTEM', status: 'ACTIVE', sortNo: 1, menuIds: [1, 2, 3, 4, 5, 6, 7, 8], apiIds: [1, 2, 3, 4, 5] },
    { id: 2, roleCode: 'OPERATOR', roleName: '运营专员', roleType: 'BUSINESS', status: 'ACTIVE', sortNo: 2, menuIds: [1, 4, 5], apiIds: [3, 4] },
    { id: 3, roleCode: 'AUDITOR', roleName: '审计员', roleType: 'BUSINESS', status: 'ACTIVE', sortNo: 3, menuIds: [1, 6], apiIds: [5] }
  ],
  menus: [
    { id: 1, parentId: 0, menuCode: 'dashboard', menuName: '工作台', menuType: 'M', routePath: '/dashboard', permissionCode: '', sortNo: 1, status: 'ENABLED', children: [] },
    { id: 2, parentId: 0, menuCode: 'sys', menuName: '系统管理', menuType: 'C', routePath: '', permissionCode: '', sortNo: 2, status: 'ENABLED', children: [
      { id: 3, parentId: 2, menuCode: 'sys:user', menuName: '用户管理', menuType: 'M', routePath: '/system/user', permissionCode: 'system:user:read', sortNo: 1, status: 'ENABLED', children: [] },
      { id: 4, parentId: 2, menuCode: 'sys:role', menuName: '角色管理', menuType: 'M', routePath: '/system/role', permissionCode: 'system:role:read', sortNo: 2, status: 'ENABLED', children: [] },
      { id: 5, parentId: 2, menuCode: 'sys:menu', menuName: '菜单管理', menuType: 'M', routePath: '/system/menu', permissionCode: 'system:menu:read', sortNo: 3, status: 'ENABLED', children: [] }
    ] },
    { id: 6, parentId: 0, menuCode: 'wf', menuName: '工作流', menuType: 'C', routePath: '', permissionCode: '', sortNo: 3, status: 'ENABLED', children: [
      { id: 7, parentId: 6, menuCode: 'wf:task', menuName: '我的待办', menuType: 'M', routePath: '/workflow/task', permissionCode: 'workflow:task:read', sortNo: 1, status: 'ENABLED', children: [] },
      { id: 8, parentId: 6, menuCode: 'wf:instance', menuName: '我发起的流程', menuType: 'M', routePath: '/workflow/instance', permissionCode: 'workflow:instance:read', sortNo: 2, status: 'ENABLED', children: [] }
    ] }
  ],
  apiResources: [
    { id: 1, resourceName: '用户-分页查询', permissionCode: 'system:user:read', httpMethod: 'GET', pathPattern: '/api/system/users/page', authMode: 'JWT', status: 'ENABLED', riskLevel: 'LOW' },
    { id: 2, resourceName: '用户-创建', permissionCode: 'system:user:create', httpMethod: 'POST', pathPattern: '/api/system/users', authMode: 'JWT', status: 'ENABLED', riskLevel: 'MEDIUM' },
    { id: 3, resourceName: '角色-列表', permissionCode: 'system:role:read', httpMethod: 'GET', pathPattern: '/api/system/roles', authMode: 'JWT', status: 'ENABLED', riskLevel: 'LOW' },
    { id: 4, resourceName: '菜单-树', permissionCode: 'system:menu:read', httpMethod: 'GET', pathPattern: '/api/system/menus/tree', authMode: 'JWT', status: 'ENABLED', riskLevel: 'LOW' },
    { id: 5, resourceName: '任务-待办', permissionCode: 'workflow:task:read', httpMethod: 'GET', pathPattern: '/api/workflow/tasks/mine', authMode: 'JWT', status: 'ENABLED', riskLevel: 'LOW' }
  ],
  tasks: [
    { taskId: 'T1001', processInstanceId: 'P2026001', activityId: 'approve', name: '部门经理审批', status: 'PENDING', assigneeUserId: 1, dueAt: '2026-10-10 18:00:00' },
    { taskId: 'T1002', processInstanceId: 'P2026002', activityId: 'approve', name: '财务复核', status: 'PENDING', assigneeUserId: 1, dueAt: '2026-10-12 18:00:00' }
  ],
  instances: [
    { processInstanceId: 'P2026001', businessType: 'LEAVE', businessId: 'L001', title: '张三的请假申请', status: 'RUNNING', startedAt: '2026-10-01 09:00:00', finishedAt: null },
    { processInstanceId: 'P2026002', businessType: 'EXPENSE', businessId: 'E001', title: '李四的报销申请', status: 'RUNNING', startedAt: '2026-10-02 09:00:00', finishedAt: null }
  ],
  records: {
    P2026001: [
      { operationId: 'OP1', action: 'START', operatorUserId: 2, fromUserId: null, toUserId: 1, opinion: '提交申请', occurredAt: '2026-10-01 09:00:00' },
      { operationId: 'OP2', action: 'APPROVE', operatorUserId: 1, fromUserId: null, toUserId: null, opinion: '同意', occurredAt: '2026-10-01 10:00:00' }
    ],
    P2026002: [
      { operationId: 'OP3', action: 'START', operatorUserId: 3, fromUserId: null, toUserId: 1, opinion: '提交报销', occurredAt: '2026-10-02 09:00:00' }
    ]
  }
}

function roleCodesFor(roleIds) {
  if (!roleIds) return []
  return db.roles.filter((r) => roleIds.includes(r.id)).map((r) => r.roleCode)
}

function idOf(url) {
  const parts = url.split('/')
  return Number(parts[parts.length - 1])
}

// 在嵌套菜单树中按 id 查找节点（深度优先）
function findMenu(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children?.length) {
      const f = findMenu(n.children, id)
      if (f) return f
    }
  }
  return null
}

// 从嵌套菜单树中删除节点：有子节点返回 'HAS_CHILDREN'，不存在返回 'NOT_FOUND'，成功返回 'OK'
function removeMenu(nodes, id) {
  for (let i = 0; i < nodes.length; i++) {
    const n = nodes[i]
    if (n.id === id) {
      if (n.children?.length) return 'HAS_CHILDREN'
      nodes.splice(i, 1)
      return 'OK'
    }
    if (n.children?.length) {
      const r = removeMenu(n.children, id)
      if (r !== 'NOT_FOUND') return r
    }
  }
  return 'NOT_FOUND'
}

// 从嵌套菜单树中摘下并返回指定节点（连同其 children），不删除其子孙
function detachMenu(nodes, id) {
  for (let i = 0; i < nodes.length; i++) {
    const n = nodes[i]
    if (n.id === id) {
      nodes.splice(i, 1)
      return n
    }
    if (n.children?.length) {
      const d = detachMenu(n.children, id)
      if (d) return d
    }
  }
  return null
}

// 收集节点及其全部子孙的 id 集合（用于菜单移动时的环路校验）
function menuSubtreeIds(nodes, id) {
  const set = new Set()
  const walk = (list) => {
    for (const n of list) {
      if (n.id === id) {
        set.add(n.id)
        const collect = (node) => {
          for (const c of node.children || []) {
            set.add(c.id)
            collect(c)
          }
        }
        collect(n)
        return true
      }
      if (n.children?.length && walk(n.children)) return true
    }
    return false
  }
  walk(nodes)
  return set
}

// 简单的路由分发：根据 method + url 返回数据或抛出业务错误
export async function mockRequest({ method, url, params = {}, data = {} }) {
  await new Promise((r) => setTimeout(r, 180)) // 模拟网络延迟
  const m = method.toUpperCase()

  // ---- 认证 ----
  if (m === 'POST' && url === '/api/auth/login') {
    const { username, password } = data
    if (username === 'admin' && password === 'admin123') {
      return { accessToken: 'mock-jwt-' + Date.now(), tokenType: 'Bearer', expiresIn: 3600 }
    }
    throw bizError('AUTH_001', '用户名或密码错误')
  }
  if (m === 'POST' && url === '/api/auth/logout') return null

  // ---- 用户 ----
  if (m === 'GET' && url === '/api/system/users/page') {
    let list = [...db.users]
    if (params.username) list = list.filter((u) => u.username.includes(params.username))
    if (params.status) list = list.filter((u) => u.status === params.status)
    if (params.orgId) list = list.filter((u) => u.primaryOrgId === Number(params.orgId))
    const page = Number(params.page || 1)
    const size = Number(params.size || 20)
    const total = list.length
    const records = list.slice((page - 1) * size, page * size)
    return { page, size, total, records }
  }
  if (m === 'GET' && /^\/api\/system\/users\/\d+$/.test(url)) {
    const u = db.users.find((x) => x.id === idOf(url))
    if (!u) throw bizError('NOT_FOUND', '用户不存在')
    return { ...u }
  }
  if (m === 'POST' && url === '/api/system/users') {
    const id = ++seq
    db.users.push({
      id,
      username: data.username,
      displayName: data.displayName,
      mobile: data.mobile || '',
      email: data.email || '',
      primaryOrgId: data.primaryOrgId || null,
      status: 'ACTIVE',
      roleCodes: roleCodesFor(data.roleIds),
      createdAt: now()
    })
    return id
  }
  if (m === 'PUT' && /^\/api\/system\/users\/\d+$/.test(url)) {
    const u = db.users.find((x) => x.id === idOf(url))
    if (!u) throw bizError('NOT_FOUND', '用户不存在')
    if (data.displayName != null) u.displayName = data.displayName
    if (data.mobile != null) u.mobile = data.mobile
    if (data.email != null) u.email = data.email
    if (data.primaryOrgId != null) u.primaryOrgId = data.primaryOrgId
    if (data.status != null) u.status = data.status
    if (data.roleIds != null) u.roleCodes = roleCodesFor(data.roleIds)
    return null
  }
  // 【用户硬删除】物理移除用户及其角色映射（与后端 JooqWriters.delete(...,false) 语义一致）
  if (m === 'DELETE' && /^\/api\/system\/users\/\d+$/.test(url)) {
    const id = idOf(url)
    const i = db.users.findIndex((x) => x.id === id)
    if (i < 0) throw bizError('NOT_FOUND', '用户不存在')
    db.users.splice(i, 1)
    return null
  }
  if (m === 'POST' && /^\/api\/system\/users\/\d+\/reset-password$/.test(url)) {
    const u = db.users.find((x) => x.id === idOf(url))
    if (!u) throw bizError('NOT_FOUND', '用户不存在')
    return null
  }

  // ---- 角色（含菜单/接口权限持久化）----
  if (m === 'GET' && url === '/api/system/roles') {
    return db.roles.map((r) => ({ ...r }))
  }
  if (m === 'GET' && /^\/api\/system\/roles\/\d+$/.test(url)) {
    const r = db.roles.find((x) => x.id === idOf(url))
    if (!r) throw bizError('NOT_FOUND', '角色不存在')
    return { ...r }
  }
  if (m === 'POST' && url === '/api/system/roles') {
    const id = ++seq
    db.roles.push({
      id,
      roleCode: data.roleCode,
      roleName: data.roleName,
      roleType: data.roleType || 'BUSINESS',
      status: data.status || 'ACTIVE',
      sortNo: data.sortNo || 0,
      menuIds: data.menuIds || [],
      apiIds: data.apiIds || []
    })
    return id
  }
  if (m === 'PUT' && /^\/api\/system\/roles\/\d+$/.test(url)) {
    const r = db.roles.find((x) => x.id === idOf(url))
    if (!r) throw bizError('NOT_FOUND', '角色不存在')
    r.roleName = data.roleName
    if (data.roleType != null) r.roleType = data.roleType
    if (data.status != null) r.status = data.status
    if (data.sortNo != null) r.sortNo = data.sortNo
    r.menuIds = data.menuIds || []
    r.apiIds = data.apiIds || []
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/roles\/\d+$/.test(url)) {
    const i = db.roles.findIndex((x) => x.id === idOf(url))
    if (i < 0) throw bizError('NOT_FOUND', '角色不存在')
    db.roles.splice(i, 1)
    return null
  }

  // ---- 菜单树 / 接口资源 ----
  if (m === 'GET' && url === '/api/system/menus/tree') return db.menus
  if (m === 'GET' && url === '/api/system/api-resources') return db.apiResources

  // 菜单创建（按 parentId 插入嵌套树）+ 删除（有子节点拒绝，与后端一致）
  if (m === 'POST' && url === '/api/system/menus') {
    const id = ++seq
    const node = {
      id,
      parentId: data.parentId || 0,
      menuCode: data.menuCode,
      menuName: data.menuName,
      menuType: data.menuType || 'M',
      routePath: data.routePath || '',
      componentPath: data.componentPath || '',
      permissionCode: data.permissionCode || '',
      icon: data.icon || '',
      visible: data.visible == null ? 1 : data.visible,
      sortNo: data.sortNo || 0,
      status: data.status || 'ENABLED',
      children: []
    }
    if (!node.parentId || node.parentId === 0) {
      db.menus.push(node)
    } else {
      const parent = findMenu(db.menus, node.parentId)
      if (!parent) throw bizError('NOT_FOUND', '父菜单不存在')
      parent.children = parent.children || []
      parent.children.push(node)
    }
    return id
  }
  // 菜单编辑：按 id 选择性更新；parentId 变化时在嵌套树内移动节点（保留 children），并做环路校验
  if (m === 'PUT' && /^\/api\/system\/menus\/\d+$/.test(url)) {
    const node = findMenu(db.menus, idOf(url))
    if (!node) throw bizError('NOT_FOUND', '菜单不存在')
    const newParentId = data.parentId != null ? data.parentId : 0
    if (data.parentId != null && newParentId !== node.parentId) {
      if (newParentId === node.id) throw bizError('CONFLICT', '不能将菜单挂到自身之下')
      if (newParentId !== 0) {
        const sub = menuSubtreeIds(db.menus, node.id)
        if (sub.has(newParentId)) throw bizError('CONFLICT', '不能将菜单移动到其子菜单之下')
        const parent = findMenu(db.menus, newParentId)
        if (!parent) throw bizError('NOT_FOUND', '父菜单不存在')
      }
      const detached = detachMenu(db.menus, node.id)
      if (!detached) throw bizError('NOT_FOUND', '菜单不存在')
      detached.parentId = newParentId
      if (newParentId === 0) {
        db.menus.push(detached)
      } else {
        const parent = findMenu(db.menus, newParentId)
        parent.children = parent.children || []
        parent.children.push(detached)
      }
      // 移动后对象引用不变，继续更新其标量字段
    }
    if (data.menuCode != null) node.menuCode = data.menuCode
    if (data.menuName != null) node.menuName = data.menuName
    if (data.menuType != null) node.menuType = data.menuType
    if (data.routePath != null) node.routePath = data.routePath
    if (data.componentPath != null) node.componentPath = data.componentPath
    if (data.permissionCode != null) node.permissionCode = data.permissionCode
    if (data.icon != null) node.icon = data.icon
    if (data.visible != null) node.visible = data.visible
    if (data.sortNo != null) node.sortNo = data.sortNo
    if (data.status != null) node.status = data.status
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/menus\/\d+$/.test(url)) {
    const res = removeMenu(db.menus, idOf(url))
    if (res === 'HAS_CHILDREN') throw bizError('CONFLICT', '请先删除子菜单')
    if (res === 'NOT_FOUND') throw bizError('NOT_FOUND', '菜单不存在')
    return null
  }

  // 接口资源创建 + 删除
  if (m === 'POST' && url === '/api/system/api-resources') {
    const id = ++seq
    db.apiResources.push({
      id,
      resourceName: data.resourceName,
      permissionCode: data.permissionCode,
      httpMethod: (data.httpMethod || 'GET').toUpperCase(),
      pathPattern: data.pathPattern,
      authMode: data.authMode || 'JWT',
      status: 'ENABLED',
      riskLevel: data.riskLevel || 'NORMAL'
    })
    return id
  }
  // 接口资源编辑：按 id 选择性更新（id 不变）
  if (m === 'PUT' && /^\/api\/system\/api-resources\/\d+$/.test(url)) {
    const r = db.apiResources.find((x) => x.id === idOf(url))
    if (!r) throw bizError('NOT_FOUND', '接口资源不存在')
    if (data.resourceName != null) r.resourceName = data.resourceName
    if (data.permissionCode != null) r.permissionCode = data.permissionCode
    if (data.httpMethod != null) r.httpMethod = data.httpMethod.toUpperCase()
    if (data.pathPattern != null) r.pathPattern = data.pathPattern
    if (data.authMode != null) r.authMode = data.authMode
    if (data.status != null) r.status = data.status
    if (data.riskLevel != null) r.riskLevel = data.riskLevel
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/api-resources\/\d+$/.test(url)) {
    const id = idOf(url)
    const i = db.apiResources.findIndex((x) => x.id === id)
    if (i < 0) throw bizError('NOT_FOUND', '接口资源不存在')
    db.apiResources.splice(i, 1)
    return null
  }

  // ---- 工作流 ----
  if (m === 'GET' && url === '/api/workflow/tasks/mine') return db.tasks
  if (m === 'POST' && url === '/api/workflow/tasks/complete') {
    const i = db.tasks.findIndex((t) => t.taskId === data.taskId)
    if (i >= 0) db.tasks.splice(i, 1) // 审批/驳回后该待办消失
    return null
  }
  if (m === 'POST' && url === '/api/workflow/tasks/transfer') {
    const i = db.tasks.findIndex((t) => t.taskId === data.taskId)
    if (i >= 0) db.tasks.splice(i, 1) // 转办后该待办移出当前处理人
    return null
  }
  if (m === 'GET' && url === '/api/workflow/instances/mine') return db.instances
  if (m === 'GET' && /^\/api\/workflow\/instances\/[^/]+\/records$/.test(url)) {
    const pid = url.split('/')[4]
    return db.records[pid] || []
  }
  if (m === 'POST' && url === '/api/workflow/instances/start') {
    const pid = 'P' + Date.now()
    db.instances.unshift({
      processInstanceId: pid,
      businessType: data.businessType,
      businessId: data.businessId,
      title: data.title,
      status: 'RUNNING',
      startedAt: now(),
      finishedAt: null
    })
    return pid
  }

  throw bizError('NOT_FOUND', `Mock 未实现的接口: ${m} ${url}`)
}
