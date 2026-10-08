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
    { id: 1, username: 'admin', displayName: '超级管理员', mobile: '13800000000', email: 'admin@example.com', avatarUrl: null, notifySiteMessage: 1, notifyEmail: 1, notifyMobile: 0, showLoginLog: 1, maskMobile: 1, discoverable: 1, lastLoginAt: '2026-10-07 09:12:33', orgId: 1, roleId: 1, roleName: '超级管理员', roleCode: 'SUPER_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01 10:00:00' },
    { id: 2, username: 'zhangsan', displayName: '张三', mobile: '13800000001', email: 'zhangsan@example.com', orgId: 1, roleId: 2, roleName: '运营专员', roleCode: 'OPERATOR', status: 'ACTIVE', createdAt: '2026-02-01 09:00:00' },
    { id: 3, username: 'lisi', displayName: '李四', mobile: '13800000002', email: 'lisi@example.com', orgId: 2, roleId: 3, roleName: '审计员', roleCode: 'AUDITOR', status: 'DISABLED', createdAt: '2026-03-01 09:00:00' }
  ],
  roles: [
    { id: 1, roleCode: 'SUPER_ADMIN', roleName: '超级管理员', roleType: 'SYSTEM', status: 'ACTIVE', sortNo: 1, menuIds: [1, 2, 3, 4, 5, 6, 7, 8], apiIds: [1, 2, 3, 4, 5] },
    { id: 2, roleCode: 'OPERATOR', roleName: '运营专员', roleType: 'BUSINESS', status: 'ACTIVE', sortNo: 2, menuIds: [1, 4, 5], apiIds: [3, 4] },
    { id: 3, roleCode: 'AUDITOR', roleName: '审计员', roleType: 'BUSINESS', status: 'ACTIVE', sortNo: 3, menuIds: [1, 6], apiIds: [5] }
  ],
  // 角色数据权限规则，结构对齐 sys_role_data_scope / sys_role_data_scope_org
  // resourceCode 当前仅 system:user 接入过滤（与后端 DefaultDataScopeProvider 一致）
  roleDataScopes: [
    { id: 1, roleId: 2, resourceCode: 'system:user', scopeType: 'DEPT_AND_CHILD', orgIds: [] }
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
      { id: 8, parentId: 6, menuCode: 'wf:instance', menuName: '我发起的流程', menuType: 'M', routePath: '/workflow/instance', permissionCode: 'workflow:instance:read', sortNo: 2, status: 'ENABLED', children: [] },
      { id: 9, parentId: 6, menuCode: 'wf:definition', menuName: '工作流设计', menuType: 'M', routePath: '/workflow/definition', permissionCode: 'workflow:definition:read', sortNo: 3, status: 'ENABLED', children: [] }
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
  definitions: [
    {
      id: 1, processKey: 'leaveApproval', processName: '请假审批', description: '示例：发起 -> 直属主管审批 -> 结束',
      status: 'PUBLISHED', version: 2,
      nodes: [
        { id: 'start', type: 'START', name: '发起' },
        { id: 'a1', type: 'APPROVAL', name: '直属主管审批', approvalMode: 'ANY', assigneeType: 'ROLE', assigneeExpression: 'MANAGER', rejectPolicy: 'PREVIOUS' },
        { id: 'end', type: 'END', name: '结束' }
      ],
      edges: [
        { id: 'e1', sourceNodeId: 'start', targetNodeId: 'a1' },
        { id: 'e2', sourceNodeId: 'a1', targetNodeId: 'end' }
      ],
      bpmnXml: '', deploymentId: 'dep-mock-1', processDefinitionId: 'leave:2:mock',
      publishedAt: '2026-10-01 10:00:00', createdAt: '2026-09-30 10:00:00', updatedAt: '2026-10-01 10:00:00'
    }
  ],
  defSeq: 1,
  records: {
    P2026001: [
      { operationId: 'OP1', action: 'START', operatorUserId: 2, fromUserId: null, toUserId: 1, opinion: '提交申请', occurredAt: '2026-10-01 09:00:00' },
      { operationId: 'OP2', action: 'APPROVE', operatorUserId: 1, fromUserId: null, toUserId: null, opinion: '同意', occurredAt: '2026-10-01 10:00:00' }
    ],
    P2026002: [
      { operationId: 'OP3', action: 'START', operatorUserId: 3, fromUserId: null, toUserId: 1, opinion: '提交报销', occurredAt: '2026-10-02 09:00:00' }
    ]
  },
  orgs: [
    { id: 1, parentId: 0, orgCode: 'HQ', orgName: '总公司', orgType: 'COMPANY', sortNo: 1, leaderUserId: 1, status: 'ACTIVE', children: [
      { id: 2, parentId: 1, orgCode: 'TECH', orgName: '技术部', orgType: 'DEPARTMENT', sortNo: 1, leaderUserId: 2, status: 'ACTIVE', children: [
        { id: 4, parentId: 2, orgCode: 'FE', orgName: '前端组', orgType: 'TEAM', sortNo: 1, leaderUserId: 3, status: 'ACTIVE', children: [] }
      ]},
      { id: 3, parentId: 1, orgCode: 'HR', orgName: '人事部', orgType: 'DEPARTMENT', sortNo: 2, leaderUserId: 2, status: 'ACTIVE', children: [] }
    ]}
  ],
  dictTypes: [
    { id: 1, dictCode: 'sys_normal_disable', dictName: '系统开关', status: 'ACTIVE', sortNo: 1, remark: '正常/停用' },
    { id: 2, dictCode: 'sys_user_sex', dictName: '用户性别', status: 'ACTIVE', sortNo: 2, remark: '性别字典' }
  ],
  dictData: [
    { id: 1, dictTypeCode: 'sys_normal_disable', dictLabel: '正常', dictValue: '0', dictSort: 1, status: 'ACTIVE', remark: null },
    { id: 2, dictTypeCode: 'sys_normal_disable', dictLabel: '停用', dictValue: '1', dictSort: 2, status: 'ACTIVE', remark: null },
    { id: 3, dictTypeCode: 'sys_user_sex', dictLabel: '男', dictValue: '0', dictSort: 1, status: 'ACTIVE', remark: null },
    { id: 4, dictTypeCode: 'sys_user_sex', dictLabel: '女', dictValue: '1', dictSort: 2, status: 'ACTIVE', remark: null }
  ],
  configs: [
    { id: 1, configKey: 'sys.title', configName: '系统标题', configValue: '管理框架', configType: 'STRING', remark: '前端展示标题', status: 'ACTIVE' },
    { id: 2, configKey: 'sys.max.login.fail', configName: '最大登录失败次数', configValue: '5', configType: 'INT', remark: '超过则锁定账户', status: 'ACTIVE' },
    { id: 3, configKey: 'sys.captcha.enabled', configName: '是否启用验证码', configValue: 'true', configType: 'BOOLEAN', remark: '登录验证码开关', status: 'ACTIVE' }
  ],

  // ---- 系统公告 ----
  announcements: [
    { id: 1, title: '关于系统升级维护的通知', content: '本系统将于本周六 22:00 - 次日 02:00 进行升级维护，期间可能短暂不可用，请提前保存工作内容。', status: 'PUBLISHED', isTop: 1, publishAt: '2026-10-01T09:00:00', expireAt: null, publishedAt: '2026-10-01 09:00:00', offlineAt: null, publisherId: 1, viewCount: 128, createdAt: '2026-10-01 08:50:00' },
    { id: 2, title: '新版用户管理上线说明', content: '用户管理已收敛为「单角色 + 单部门」绑定模型，权限判定与数据范围按单值直读，详见变更说明。', status: 'PUBLISHED', isTop: 0, publishAt: '2026-10-05T10:00:00', expireAt: '2026-11-05T10:00:00', publishedAt: '2026-10-05 10:00:00', offlineAt: null, publisherId: 1, viewCount: 46, createdAt: '2026-10-05 09:40:00' },
    { id: 3, title: '【草稿】季度表彰名单', content: '拟表彰三季度优秀员工，请补充名单后发布。', status: 'DRAFT', isTop: 0, publishAt: null, expireAt: null, publishedAt: null, offlineAt: null, publisherId: 1, viewCount: 0, createdAt: '2026-10-06 15:20:00' },
    { id: 4, title: '【已下线】旧版操作手册', content: '本手册已由新版文档替代。', status: 'OFFLINE', isTop: 0, publishAt: '2026-09-01T09:00:00', expireAt: null, publishedAt: '2026-09-01 09:00:00', offlineAt: '2026-09-20 10:00:00', publisherId: 1, viewCount: 210, createdAt: '2026-08-31 18:00:00' }
  ],

  // ---- 登录设备（账号安全）：对应真实表 sys_refresh_token 的活跃会话 ----
  devices: [
    { sessionId: 'sess-current', deviceId: 'sess-current', deviceName: 'Chrome 浏览器', userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/120', ipAddress: '127.0.0.1', issuedAt: '2026-10-07 09:00:00', lastUsedAt: '2026-10-07 18:20:15', expiresAt: '2026-10-14 09:00:00', current: true },
    { sessionId: 'sess-2', deviceId: 'sess-2', deviceName: 'iOS 设备', userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Safari/605', ipAddress: '10.0.1.23', issuedAt: '2026-10-05 20:11:02', lastUsedAt: '2026-10-06 21:02:44', expiresAt: '2026-10-12 20:11:02', current: false },
    { sessionId: 'sess-3', deviceId: 'sess-3', deviceName: 'Windows 设备', userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/120', ipAddress: '192.168.1.108', issuedAt: '2026-10-02 09:30:00', lastUsedAt: '2026-10-04 08:12:09', expiresAt: '2026-10-09 09:30:00', current: false }
  ],

  // ---- 站内信 ----
  messages: [
    { id: 1, title: '欢迎使用运营管理后台', content: '系统公告与站内信功能已上线，可在「系统管理」中查看。', msgType: 'SYSTEM', senderId: 1, totalCount: 3, readCount: 2, sentAt: '2026-10-06 09:00:00', createdAt: '2026-10-06 09:00:00' },
    { id: 2, title: '请及时完善个人资料', content: '请于本月内完善手机号与邮箱信息，便于接收通知。', msgType: 'NOTICE', senderId: 1, totalCount: 2, readCount: 1, sentAt: '2026-10-06 10:00:00', createdAt: '2026-10-06 10:00:00' }
  ],
  // 收件明细：isRead 0/1，readAt 为已读回执时间
  messageReceipts: [
    { id: 1, messageId: 1, userId: 1, isRead: 1, readAt: '2026-10-06 09:05:00' },
    { id: 2, messageId: 1, userId: 2, isRead: 0, readAt: null },
    { id: 3, messageId: 1, userId: 3, isRead: 1, readAt: '2026-10-06 11:20:00' },
    { id: 4, messageId: 2, userId: 1, isRead: 0, readAt: null },
    { id: 5, messageId: 2, userId: 3, isRead: 1, readAt: '2026-10-06 12:00:00' }
  ],

  // ---- 系统监控采样 ----
  monitorSamples: [],
  // 在线会话（模拟刷新令牌口径：未吊销未过期）
  onlineSessions: [
    { userId: 1, username: 'admin', displayName: '超级管理员', sessionId: 'sess-001', clientId: 'web', ipAddress: '127.0.0.1', userAgent: 'Mozilla/5.0 (Macintosh)', issuedAt: '2026-10-07 08:00:00', lastUsedAt: '2026-10-07 17:20:00', expiresAt: '2026-10-14 08:00:00', remainingMinutes: 9600 },
    { userId: 2, username: 'zhangsan', displayName: '张三', sessionId: 'sess-002', clientId: 'web', ipAddress: '127.0.0.1', userAgent: 'Mozilla/5.0 (Windows NT)', issuedAt: '2026-10-07 09:30:00', lastUsedAt: '2026-10-07 16:40:00', expiresAt: '2026-10-14 09:30:00', remainingMinutes: 9420 }
  ],
  // 异常日志（对应后端 sys_operation_log success=0）
  errorLogs: [
    { id: 1, moduleCode: 'system', operationType: 'CREATE', operationName: '创建用户', requestMethod: 'POST', requestPath: '/api/system/users', resultCode: 'COMMON_409', durationMs: 35, operatorName: 'admin', traceId: 'a1b2c3d4e5', success: 0, occurredAt: '2026-10-07 10:12:33' },
    { id: 2, moduleCode: 'workflow', operationType: 'APPROVE', operationName: '审批任务', requestMethod: 'POST', requestPath: '/api/workflow/tasks/complete', resultCode: 'COMMON_422', durationMs: 128, operatorName: 'zhangsan', traceId: 'f6e7d8c9b0', success: 0, occurredAt: '2026-10-07 11:05:02' },
    { id: 3, moduleCode: 'system', operationType: 'DELETE', operationName: '删除部门', requestMethod: 'DELETE', requestPath: '/api/system/orgs/4', resultCode: 'COMMON_409', durationMs: 22, operatorName: 'admin', traceId: '1122334455', success: 0, occurredAt: '2026-10-07 14:20:18' }
  ]
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

// ---- 机构（部门）树辅助 ----
function findOrg(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children?.length) {
      const f = findOrg(n.children, id)
      if (f) return f
    }
  }
  return null
}
/** 拍平部门树的所有节点，用于校验自定义范围里的部门是否真实存在。 */
function flattenOrgIds(nodes, acc = []) {
  for (const n of nodes) {
    acc.push(n.id)
    if (n.children?.length) flattenOrgIds(n.children, acc)
  }
  return acc
}
/** 按部门 ID 取名称；已删除的部门返回占位文案，与后端 orgNames 降级行为一致。 */
function orgNameOf(nodes, id) {
  const o = findOrg(nodes, id)
  return o ? o.orgName : `已删除部门(${id})`
}
function removeOrg(nodes, id) {
  for (let i = 0; i < nodes.length; i++) {
    const n = nodes[i]
    if (n.id === id) {
      if (n.children?.length) return 'HAS_CHILDREN'
      nodes.splice(i, 1)
      return 'OK'
    }
    if (n.children?.length) {
      const r = removeOrg(n.children, id)
      if (r !== 'NOT_FOUND') return r
    }
  }
  return 'NOT_FOUND'
}
function detachOrg(nodes, id) {
  for (let i = 0; i < nodes.length; i++) {
    const n = nodes[i]
    if (n.id === id) {
      nodes.splice(i, 1)
      return n
    }
    if (n.children?.length) {
      const d = detachOrg(n.children, id)
      if (d) return d
    }
  }
  return null
}
function orgSubtreeIds(nodes, id) {
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

// ---- 服务端排序白名单（与后端 JooqSorts 的字段名 → 列名映射一一对应）----
// 白名单外的字段、非法方向一律忽略，mock 行为与真实后端保持一致：不会静默按任意字段排序。
const SORT_FIELDS = {
  '/api/system/users/page': {
    id: 'id', username: 'username', displayName: 'displayName', status: 'status', createdAt: 'createdAt'
  },
  '/api/system/announcements/page': {
    id: 'id', title: 'title', status: 'status', isTop: 'isTop',
    publishAt: 'publishAt', expireAt: 'expireAt', createdAt: 'createdAt'
  },
  '/api/system/monitor/error-logs': {
    id: 'id', moduleCode: 'moduleCode', operationName: 'operationName',
    operatorName: 'operatorName', durationMs: 'durationMs', occurredAt: 'occurredAt'
  }
}

/**
 * 按 sortField / sortDirection 排序，返回新数组。
 * 字段不在白名单或方向非 ASC/DESC 时原样返回，由调用方沿用默认排序。
 */
function applyServerSort(list, url, sortField, sortDirection) {
  const whitelist = SORT_FIELDS[url]
  const dir = String(sortDirection || '').toUpperCase()
  if (!whitelist || !sortField || !whitelist[sortField]) return list
  if (dir !== 'ASC' && dir !== 'DESC') return list
  const key = whitelist[sortField]
  const sign = dir === 'DESC' ? -1 : 1
  return [...list].sort((a, b) => {
    const va = a?.[key]
    const vb = b?.[key]
    if (va === vb) return 0
    if (va == null) return 1 // 空值恒排末尾，避免前端看到空行顶在前面
    if (vb == null) return -1
    return va > vb ? sign : -sign
  })
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

  // ---- 用户（V6 收敛：单角色 roleId + 单部门 orgId）----
  if (m === 'GET' && url === '/api/system/users/page') {
    let list = [...db.users]
    if (params.username) {
      list = list.filter(
        (u) => u.username.includes(params.username) || (u.displayName || '').includes(params.username)
      )
    }
    if (params.status) list = list.filter((u) => u.status === params.status)
    if (params.orgId) list = list.filter((u) => u.orgId === Number(params.orgId))
    if (params.roleId) list = list.filter((u) => u.roleId === Number(params.roleId))
    // 服务端排序：白名单内字段生效，未指定时保持默认（后端为 id 倒序）
    list = applyServerSort(list, url, params.sortField, params.sortDirection)
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
    if (data.roleId == null) throw bizError('VALIDATION_ERROR', '角色不能为空，用户仅可绑定单个角色')
    const role = db.roles.find((r) => r.id === Number(data.roleId))
    if (!role) throw bizError('NOT_FOUND', '角色不存在')
    const id = ++seq
    db.users.push({
      id,
      username: data.username,
      displayName: data.displayName,
      mobile: data.mobile || '',
      email: data.email || '',
      orgId: data.orgId != null ? Number(data.orgId) : null,
      roleId: Number(data.roleId),
      roleName: role.roleName,
      roleCode: role.roleCode,
      status: 'ACTIVE',
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
    if (data.orgId != null) u.orgId = data.orgId
    if (data.status != null) u.status = data.status
    if (data.roleId != null) {
      const role = db.roles.find((r) => r.id === Number(data.roleId))
      if (!role) throw bizError('NOT_FOUND', '角色不存在')
      u.roleId = role.id
      u.roleName = role.roleName
      u.roleCode = role.roleCode
    }
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
    // 级联清理数据权限，避免角色重建后读到旧规则
    db.roleDataScopes = db.roleDataScopes.filter((x) => x.roleId !== idOf(url))
    return null
  }

  // ---- 角色数据权限（覆盖式保存，语义与后端 RoleDataScopeService 一致）----
  if (m === 'GET' && /^\/api\/system\/roles\/\d+\/data-scopes$/.test(url)) {
    const roleId = Number(url.match(/\/roles\/(\d+)\/data-scopes/)[1])
    if (!db.roles.some((x) => x.id === roleId)) throw bizError('NOT_FOUND', '角色不存在')
    return db.roleDataScopes
      .filter((x) => x.roleId === roleId)
      .map((r) => ({
        ...r,
        orgIds: r.scopeType === 'CUSTOM' ? [...(r.orgIds || [])] : [],
        orgNames: r.scopeType === 'CUSTOM'
          ? (r.orgIds || []).map((oid) => orgNameOf(db.orgs, oid))
          : []
      }))
  }
  if (m === 'PUT' && /^\/api\/system\/roles\/\d+\/data-scopes$/.test(url)) {
    const roleId = Number(url.match(/\/roles\/(\d+)\/data-scopes/)[1])
    if (!db.roles.some((x) => x.id === roleId)) throw bizError('NOT_FOUND', '角色不存在')
    if (!data.resourceCode) throw bizError('VALIDATION_ERROR', '资源编码不能为空')
    if (!data.scopeType) throw bizError('VALIDATION_ERROR', '数据范围类型不能为空')
    const orgIds = data.orgIds || []
    // 与后端 validateScopeInput 相同的组合校验
    if (data.scopeType === 'CUSTOM') {
      if (!orgIds.length) throw bizError('VALIDATION_ERROR', '自定义范围必须至少选择一个部门')
      const existing = new Set(flattenOrgIds(db.orgs))
      const missing = orgIds.filter((id) => !existing.has(id))
      if (missing.length) throw bizError('VALIDATION_ERROR', `部门不存在或已删除: ${missing}`)
    } else if (orgIds.length) {
      throw bizError('VALIDATION_ERROR', `${data.scopeType} 范围不适用自定义部门列表，请清空后重试`)
    }
    // 覆盖式：先清该角色该资源的旧规则
    db.roleDataScopes = db.roleDataScopes.filter(
      (x) => !(x.roleId === roleId && x.resourceCode === data.resourceCode)
    )
    // ALL 表示不限制，直接不落规则（与后端一致：删除后即回到不限）
    if (data.scopeType !== 'ALL') {
      db.roleDataScopes.push({
        id: ++seq,
        roleId,
        resourceCode: data.resourceCode,
        scopeType: data.scopeType,
        orgIds: data.scopeType === 'CUSTOM' ? [...orgIds] : []
      })
    }
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
  // 接口资源扫描：模拟「后端注解扫描结果」，与 db.apiResources 比对后返回差异（不写库）
  if (m === 'GET' && url === '/api/system/api-resources/scan') {
    return scanApiResourceDiff()
  }
  if (m === 'POST' && url === '/api/system/api-resources/scan/sync') {
    const diff = scanApiResourceDiff()
    diff.newResources.forEach((r) => {
      db.apiResources.push({
        id: ++seq,
        resourceName: r.resourceName,
        permissionCode: r.permissionCode,
        httpMethod: r.httpMethod,
        pathPattern: r.pathPattern,
        authMode: r.authMode,
        status: 'ENABLED',
        riskLevel: 'NORMAL'
      })
    })
    diff.changedResources.forEach((r) => {
      const cur = db.apiResources.find(
        (x) => x.httpMethod === r.httpMethod && x.pathPattern === r.pathPattern
      )
      // 与真实后端一致：只覆盖权限标识/资源名/鉴权模式，status 与 riskLevel 保留人工维护值
      if (cur) {
        cur.resourceName = r.resourceName
        cur.permissionCode = r.permissionCode
        cur.authMode = r.authMode
      }
    })
    return {
      insertedCount: diff.newResources.length,
      updatedCount: diff.changedResources.length,
      unchangedCount: diff.unchangedCount,
      skippedCount: diff.skippedCount,
      orphanCount: diff.orphanedResources.length
    }
  }

  // ---- 系统公告 ----
  if (m === 'GET' && url === '/api/system/announcements/page') {
    const t = Date.now()
    const eff = (a) =>
      a.status === 'PUBLISHED' &&
      (!a.publishAt || new Date(a.publishAt).getTime() <= t) &&
      (!a.expireAt || new Date(a.expireAt).getTime() > t)
    let list = db.announcements.map((a) => ({ ...a, effective: eff(a) }))
    if (params.keyword) {
      list = list.filter(
        (a) => a.title.includes(params.keyword) || (a.content || '').includes(params.keyword)
      )
    }
    if (params.status) list = list.filter((a) => a.status === params.status)
    if (params.onlyValid === true || params.onlyValid === 'true') {
      list = list.filter((a) => a.effective)
    }
    // 未指定排序时保持默认：置顶优先 + 发布时间倒序
    const sorted = applyServerSort(list, url, params.sortField, params.sortDirection)
    if (sorted === list) {
      list.sort((x, y) => {
        if (x.isTop !== y.isTop) return y.isTop - x.isTop // 置顶优先
        return String(y.publishAt || '').localeCompare(String(x.publishAt || ''))
      })
    } else {
      list = sorted
    }
    const page = Number(params.page || 1)
    const size = Number(params.size || 20)
    return { page, size, total: list.length, records: list.slice((page - 1) * size, page * size) }
  }
  if (m === 'GET' && /^\/api\/system\/announcements\/\d+$/.test(url)) {
    const a = db.announcements.find((x) => x.id === idOf(url))
    if (!a) throw bizError('NOT_FOUND', '公告不存在')
    a.viewCount = (a.viewCount || 0) + 1
    const t = Date.now()
    const effective =
      a.status === 'PUBLISHED' &&
      (!a.publishAt || new Date(a.publishAt).getTime() <= t) &&
      (!a.expireAt || new Date(a.expireAt).getTime() > t)
    return { ...a, effective }
  }
  if (m === 'POST' && url === '/api/system/announcements') {
    const id = ++seq
    db.announcements.push({
      id,
      title: data.title,
      content: data.content,
      status: 'DRAFT',
      isTop: data.isTop || 0,
      publishAt: data.publishAt || null,
      expireAt: data.expireAt || null,
      publishedAt: null,
      offlineAt: null,
      publisherId: 1,
      viewCount: 0,
      createdAt: now()
    })
    return id
  }
  if (m === 'PUT' && /^\/api\/system\/announcements\/\d+$/.test(url)) {
    const a = db.announcements.find((x) => x.id === idOf(url))
    if (!a) throw bizError('NOT_FOUND', '公告不存在')
    if (a.status === 'PUBLISHED') throw bizError('CONFLICT', '公告已发布，请先下线后再编辑')
    if (data.title != null) a.title = data.title
    if (data.content != null) a.content = data.content
    if (data.isTop != null) a.isTop = data.isTop
    if (data.publishAt !== undefined) a.publishAt = data.publishAt
    if (data.expireAt !== undefined) a.expireAt = data.expireAt
    return null
  }
  if (m === 'POST' && /^\/api\/system\/announcements\/\d+\/publish$/.test(url)) {
    const a = db.announcements.find((x) => x.id === idOf(url))
    if (!a) throw bizError('NOT_FOUND', '公告不存在')
    if (a.status === 'PUBLISHED') throw bizError('CONFLICT', '公告已是已发布状态')
    if (params.publishAt) a.publishAt = params.publishAt
    if (params.expireAt) a.expireAt = params.expireAt
    a.status = 'PUBLISHED'
    a.publishedAt = now()
    a.offlineAt = null
    return null
  }
  if (m === 'POST' && /^\/api\/system\/announcements\/\d+\/offline$/.test(url)) {
    const a = db.announcements.find((x) => x.id === idOf(url))
    if (!a) throw bizError('NOT_FOUND', '公告不存在')
    if (a.status !== 'PUBLISHED') throw bizError('CONFLICT', '仅已发布的公告可以下线')
    a.status = 'OFFLINE'
    a.offlineAt = now()
    return null
  }
  if (m === 'POST' && /^\/api\/system\/announcements\/\d+\/toggle-top$/.test(url)) {
    const a = db.announcements.find((x) => x.id === idOf(url))
    if (!a) throw bizError('NOT_FOUND', '公告不存在')
    a.isTop = a.isTop === 1 ? 0 : 1
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/announcements\/\d+$/.test(url)) {
    const id = idOf(url)
    const i = db.announcements.findIndex((x) => x.id === id)
    if (i < 0) throw bizError('NOT_FOUND', '公告不存在')
    db.announcements.splice(i, 1)
    return null
  }

  // ---- 站内信 ----
  if (m === 'POST' && url === '/api/system/messages/send') {
    const hasExplicit = Array.isArray(data.receiverIds) && data.receiverIds.length > 0
    const hasRole = Array.isArray(data.roleIds) && data.roleIds.length > 0
    const hasOrg = Array.isArray(data.orgIds) && data.orgIds.length > 0
    if (!hasExplicit && !hasRole && !hasOrg) {
      throw bizError('VALIDATION_ERROR', '请指定接收人，或按角色/部门筛选接收人')
    }
    // 角色与部门为「或」关系；V6 收敛后用户为单角色单部门
    let targets = hasExplicit
      ? [...data.receiverIds]
      : db.users
          .filter((u) => u.status === 'ACTIVE')
          .filter((u) => (hasRole && data.roleIds.includes(u.roleId)) || (hasOrg && data.orgIds.includes(u.orgId)))
          .map((u) => u.id)
    targets = [...new Set(targets)]
    if (targets.length === 0) throw bizError('CONFLICT', '按当前筛选条件未匹配到任何接收人')
    if (targets.length > 5000) throw bizError('VALIDATION_ERROR', '单次发送接收人不得超过 5000 人')

    const id = ++seq
    db.messages.push({
      id,
      title: data.title,
      content: data.content,
      msgType: data.msgType || 'NOTICE',
      senderId: 1,
      filterRoleIds: (data.roleIds || []).join(',') || null,
      filterOrgIds: (data.orgIds || []).join(',') || null,
      receiverIds: targets.join(','),
      totalCount: targets.length,
      readCount: 0,
      sentAt: now(),
      createdAt: now()
    })
    targets.forEach((uid) => {
      db.messageReceipts.push({
        id: ++seq,
        messageId: id,
        userId: uid,
        isRead: 0,
        readAt: null
      })
    })
    return { receiverCount: targets.length }
  }
  // 演示：以 admin(id=1) 作为当前登录用户
  if (m === 'GET' && url === '/api/system/messages/mine') {
    const me = 1
    let rows = db.messageReceipts.filter((r) => r.userId === me)
    if (params.onlyUnread === true || params.onlyUnread === 'true') {
      rows = rows.filter((r) => r.isRead === 0)
    }
    rows.sort((x, y) => x.isRead - y.isRead || y.id - x.id) // 未读优先
    const records = rows.map((r) => {
      const msg = db.messages.find((x) => x.id === r.messageId)
      const sender = msg ? db.users.find((u) => u.id === msg.senderId) : null
      return {
        messageId: r.messageId,
        title: msg?.title,
        content: msg?.content,
        msgType: msg?.msgType,
        senderId: msg?.senderId,
        senderName: sender?.displayName,
        sentAt: msg?.sentAt,
        isRead: r.isRead === 1,
        readAt: r.readAt
      }
    })
    const page = Number(params.page || 1)
    const size = Number(params.size || 20)
    return { page, size, total: records.length, records: records.slice((page - 1) * size, page * size) }
  }
  if (m === 'GET' && url === '/api/system/messages/unread-count') {
    return { unread: db.messageReceipts.filter((r) => r.userId === 1 && r.isRead === 0).length }
  }
  if (m === 'POST' && url === '/api/system/messages/read-all') {
    const me = 1
    const unread = db.messageReceipts.filter((r) => r.userId === me && r.isRead === 0)
    unread.forEach((r) => {
      r.isRead = 1
      r.readAt = now()
      const msg = db.messages.find((x) => x.id === r.messageId)
      if (msg) msg.readCount = (msg.readCount || 0) + 1
    })
    return { updated: unread.length }
  }
  if (m === 'POST' && /^\/api\/system\/messages\/\d+\/read$/.test(url)) {
    const mid = idOf(url)
    const r = db.messageReceipts.find((x) => x.messageId === mid && x.userId === 1)
    if (!r) throw bizError('NOT_FOUND', '消息不存在或非本人接收')
    if (r.isRead === 1) return null // 幂等
    r.isRead = 1
    r.readAt = now()
    const msg = db.messages.find((x) => x.id === mid)
    if (msg) msg.readCount = (msg.readCount || 0) + 1
    return null
  }
  if (m === 'GET' && url === '/api/system/messages/sent') {
    const sent = db.messages.filter((x) => x.senderId === 1)
    const page = Number(params.page || 1)
    const size = Number(params.size || 20)
    return { page, size, total: sent.length, records: sent.slice((page - 1) * size, page * size) }
  }

  // ---- 系统监控 ----
  if (m === 'GET' && url === '/api/system/monitor/metrics') {
    const mt = Math.round((process.uptime() / 3600) * 100) / 100
    const heapTotal = 512 * 1024 * 1024
    return {
      cpuUsage: 23.45,
      cpuCores: 8,
      loadAverage: mt,
      memoryUsage: 46.8,
      systemMemoryUsage: 58.2,
      diskUsage: 41.7,
      diskPath: '/',
      usedHeapBytes: 239 * 1024 * 1024,
      maxHeapBytes: heapTotal,
      usedMemoryBytes: 9.7 * 1024 * 1024 * 1024,
      totalMemoryBytes: 16 * 1024 * 1024 * 1024,
      jvmName: 'OpenJDK 64-Bit Server VM',
      javaVersion: '17.0.13',
      osName: 'mac os x / aarch64',
      uptimeMillis: Math.round(process.uptime() * 1000),
      threadCount: 68,
      peakThreadCount: 75,
      loadedClassCount: 18234,
      processId: 1001,
      sampledAt: now()
    }
  }
  if (m === 'GET' && url === '/api/system/monitor/online-summary') {
    return {
      activeTokens: db.onlineSessions.length,
      activeSessions: db.onlineSessions.length,
      onlineUsers: new Set(db.onlineSessions.map((s) => s.userId)).size
    }
  }
  if (m === 'GET' && url === '/api/system/monitor/online-sessions') {
    let list = db.onlineSessions
    if (params.keyword) {
      list = list.filter(
        (s) => (s.username || '').includes(params.keyword) || (s.displayName || '').includes(params.keyword)
      )
    }
    return list
  }
  if (m === 'GET' && url === '/api/system/monitor/error-logs') {
    let list = [...db.errorLogs]
    if (params.keyword) {
      list = list.filter(
        (e) =>
          (e.requestPath || '').includes(params.keyword) ||
          (e.operationName || '').includes(params.keyword) ||
          (e.operatorName || '').includes(params.keyword) ||
          (e.traceId || '').includes(params.keyword)
      )
    }
    // 未指定排序时保持默认（时间倒序），指定则按白名单字段排
    const sorted = applyServerSort(list, url, params.sortField, params.sortDirection)
    list = sorted === list
      ? [...list].sort((x, y) => String(y.occurredAt).localeCompare(String(x.occurredAt)))
      : sorted
    const page = Number(params.page || 1)
    const size = Number(params.size || 20)
    return { page, size, total: list.length, records: list.slice((page - 1) * size, page * size) }
  }
  if (m === 'GET' && url === '/api/system/monitor/error-summary') {
    const groups = {}
    db.errorLogs.forEach((e) => {
      const key = `${e.moduleCode}|${e.resultCode}`
      groups[key] = groups[key] || { moduleCode: e.moduleCode, resultCode: e.resultCode, count: 0 }
      groups[key].count++
    })
    return Object.values(groups)
  }
  if (m === 'GET' && url === '/api/system/monitor/samples') {
    return db.monitorSamples.slice(-Number(params.limit || 200))
  }
  if (m === 'POST' && url === '/api/system/monitor/sample') {
    db.monitorSamples.push({
      id: ++seq,
      cpuUsage: 20 + Math.random() * 30,
      memoryUsage: 40 + Math.random() * 20,
      systemMemoryUsage: 50 + Math.random() * 20,
      diskUsage: 41.7,
      usedHeapBytes: 239 * 1024 * 1024,
      maxHeapBytes: 512 * 1024 * 1024,
      usedMemoryBytes: 9.7 * 1024 * 1024 * 1024,
      totalMemoryBytes: 16 * 1024 * 1024 * 1024,
      onlineUsers: new Set(db.onlineSessions.map((s) => s.userId)).size,
      activeSessions: db.onlineSessions.length,
      threadCount: 68,
      sampledAt: now()
    })
    return { inserted: 1 }
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

  // ---- 自定义工作流定义 ----
  if (m === 'GET' && url === '/api/workflow/definitions') return db.definitions
  if (m === 'POST' && url === '/api/workflow/definitions') {
    const id = ++db.defSeq
    const def = {
      id, processKey: data.processKey, processName: data.processName, description: data.description || '',
      status: 'DRAFT', version: 0, nodes: [], edges: [], bpmnXml: '',
      deploymentId: null, processDefinitionId: null, publishedAt: null,
      createdAt: now(), updatedAt: now()
    }
    db.definitions.push(def)
    return def
  }
  if (m === 'GET' && /^\/api\/workflow\/definitions\/\d+$/.test(url)) {
    const id = Number(url.split('/').pop())
    return db.definitions.find((d) => d.id === id) || bizError('NOT_FOUND', '工作流定义不存在')
  }
  if (m === 'PUT' && /^\/api\/workflow\/definitions\/\d+$/.test(url)) {
    const id = Number(url.split('/').pop())
    const def = db.definitions.find((d) => d.id === id)
    if (!def) return bizError('NOT_FOUND', '工作流定义不存在')
    if (def.status !== 'DRAFT') return bizError('VALIDATION_ERROR', '仅草稿态可编辑（已发布请先取消发布）')
    def.processName = data.processName
    def.description = data.description || ''
    def.nodes = data.nodes || []
    def.edges = data.edges || []
    def.updatedAt = now()
    return def
  }
  if (m === 'DELETE' && /^\/api\/workflow\/definitions\/\d+$/.test(url)) {
    const id = Number(url.split('/').pop())
    const i = db.definitions.findIndex((d) => d.id === id)
    if (i >= 0) db.definitions.splice(i, 1)
    return null
  }
  if (m === 'POST' && /^\/api\/workflow\/definitions\/\d+\/publish$/.test(url)) {
    const id = Number(url.split('/')[4])
    const def = db.definitions.find((d) => d.id === id)
    if (!def) return bizError('NOT_FOUND', '工作流定义不存在')
    def.version = (def.version || 0) + 1
    def.status = 'PUBLISHED'
    def.deploymentId = 'dep-mock-' + def.version
    def.processDefinitionId = def.processKey + ':' + def.version + ':mock'
    def.publishedAt = now()
    def.updatedAt = now()
    return def
  }
  if (m === 'POST' && /^\/api\/workflow\/definitions\/\d+\/unpublish$/.test(url)) {
    const id = Number(url.split('/')[4])
    const def = db.definitions.find((d) => d.id === id)
    if (!def) return bizError('NOT_FOUND', '工作流定义不存在')
    def.status = 'DRAFT'
    def.updatedAt = now()
    return def
  }
  if (m === 'GET' && /^\/api\/workflow\/definitions\/\d+\/bpmn$/.test(url)) {
    const id = Number(url.split('/')[4])
    const def = db.definitions.find((d) => d.id === id)
    if (!def) return bizError('NOT_FOUND', '工作流定义不存在')
    return { processKey: def.processKey, bpmnXml: def.bpmnXml || '<!-- 草稿尚未生成 BPMN -->' }
  }

  // ---- 部门（机构）树 / 层级移动 / 删除 ----
  if (m === 'GET' && url === '/api/system/orgs/tree') return db.orgs
  if (m === 'POST' && url === '/api/system/orgs') {
    const id = ++seq
    const node = {
      id,
      parentId: data.parentId || 0,
      orgCode: data.orgCode,
      orgName: data.orgName,
      orgType: data.orgType || 'DEPARTMENT',
      sortNo: data.sortNo || 0,
      leaderUserId: data.leaderUserId || 0,
      status: data.status || 'ACTIVE',
      children: []
    }
    if (!node.parentId || node.parentId === 0) {
      db.orgs.push(node)
    } else {
      const p = findOrg(db.orgs, node.parentId)
      if (!p) throw bizError('NOT_FOUND', '父部门不存在')
      p.children = p.children || []
      p.children.push(node)
    }
    return id
  }
  // 部门编辑：parentId 变化时在嵌套树内移动节点（保留 children），并做环路校验
  if (m === 'PUT' && /^\/api\/system\/orgs\/\d+$/.test(url)) {
    const node = findOrg(db.orgs, idOf(url))
    if (!node) throw bizError('NOT_FOUND', '部门不存在')
    const newParentId = data.parentId != null ? data.parentId : 0
    if (data.parentId != null && newParentId !== node.parentId) {
      if (newParentId === node.id) throw bizError('CONFLICT', '不能将部门挂到自身之下')
      if (newParentId !== 0) {
        const sub = orgSubtreeIds(db.orgs, node.id)
        if (sub.has(newParentId)) throw bizError('CONFLICT', '不能将部门移动到其子部门之下')
        const p = findOrg(db.orgs, newParentId)
        if (!p) throw bizError('NOT_FOUND', '父部门不存在')
      }
      const detached = detachOrg(db.orgs, node.id)
      if (!detached) throw bizError('NOT_FOUND', '部门不存在')
      detached.parentId = newParentId
      if (newParentId === 0) {
        db.orgs.push(detached)
      } else {
        const p = findOrg(db.orgs, newParentId)
        p.children = p.children || []
        p.children.push(detached)
      }
    }
    if (data.orgCode != null) node.orgCode = data.orgCode
    if (data.orgName != null) node.orgName = data.orgName
    if (data.orgType != null) node.orgType = data.orgType
    if (data.sortNo != null) node.sortNo = data.sortNo
    if (data.leaderUserId != null) node.leaderUserId = data.leaderUserId
    if (data.status != null) node.status = data.status
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/orgs\/\d+$/.test(url)) {
    const res = removeOrg(db.orgs, idOf(url))
    if (res === 'HAS_CHILDREN') throw bizError('CONFLICT', '请先删除子部门')
    if (res === 'NOT_FOUND') throw bizError('NOT_FOUND', '部门不存在')
    return null
  }

  // ---- 字典类型 ----
  if (m === 'GET' && url === '/api/system/dict-types') return db.dictTypes.map((x) => ({ ...x }))
  if (m === 'POST' && url === '/api/system/dict-types') {
    if (db.dictTypes.some((t) => t.dictCode === data.dictCode)) throw bizError('CONFLICT', '字典编码已存在')
    const id = ++seq
    db.dictTypes.push({
      id,
      dictCode: data.dictCode,
      dictName: data.dictName,
      status: data.status || 'ACTIVE',
      sortNo: data.sortNo || 0,
      remark: data.remark || null
    })
    return id
  }
  if (m === 'PUT' && /^\/api\/system\/dict-types\/\d+$/.test(url)) {
    const t = db.dictTypes.find((x) => x.id === idOf(url))
    if (!t) throw bizError('NOT_FOUND', '字典类型不存在')
    if (data.dictCode != null && data.dictCode !== t.dictCode &&
        db.dictTypes.some((x) => x.dictCode === data.dictCode)) throw bizError('CONFLICT', '字典编码已存在')
    if (data.dictCode != null) t.dictCode = data.dictCode
    if (data.dictName != null) t.dictName = data.dictName
    if (data.sortNo != null) t.sortNo = data.sortNo
    if (data.remark != null) t.remark = data.remark
    if (data.status != null) t.status = data.status
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/dict-types\/\d+$/.test(url)) {
    const t = db.dictTypes.find((x) => x.id === idOf(url))
    if (!t) throw bizError('NOT_FOUND', '字典类型不存在')
    if (db.dictData.some((d) => d.dictTypeCode === t.dictCode)) throw bizError('CONFLICT', '请先删除该字典类型下的字典数据')
    db.dictTypes = db.dictTypes.filter((x) => x.id !== idOf(url))
    return null
  }

  // ---- 字典数据 ----
  if (m === 'GET' && url === '/api/system/dict-data') {
    let list = db.dictData.map((x) => ({ ...x }))
    if (params.dictType) list = list.filter((d) => d.dictTypeCode === params.dictType)
    return list
  }
  if (m === 'POST' && url === '/api/system/dict-data') {
    if (db.dictData.some((d) => d.dictTypeCode === data.dictTypeCode && d.dictValue === data.dictValue))
      throw bizError('CONFLICT', '该字典类型下字典值已存在')
    const id = ++seq
    db.dictData.push({
      id,
      dictTypeCode: data.dictTypeCode,
      dictLabel: data.dictLabel,
      dictValue: data.dictValue,
      dictSort: data.dictSort || 0,
      status: data.status || 'ACTIVE',
      remark: data.remark || null
    })
    return id
  }
  if (m === 'PUT' && /^\/api\/system\/dict-data\/\d+$/.test(url)) {
    const d = db.dictData.find((x) => x.id === idOf(url))
    if (!d) throw bizError('NOT_FOUND', '字典数据不存在')
    const typeCode = data.dictTypeCode != null ? data.dictTypeCode : d.dictTypeCode
    if (data.dictValue != null && data.dictValue !== d.dictValue &&
        db.dictData.some((x) => x.dictTypeCode === typeCode && x.dictValue === data.dictValue && x.id !== d.id))
      throw bizError('CONFLICT', '该字典类型下字典值已存在')
    if (data.dictTypeCode != null) d.dictTypeCode = data.dictTypeCode
    if (data.dictLabel != null) d.dictLabel = data.dictLabel
    if (data.dictValue != null) d.dictValue = data.dictValue
    if (data.dictSort != null) d.dictSort = data.dictSort
    if (data.remark != null) d.remark = data.remark
    if (data.status != null) d.status = data.status
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/dict-data\/\d+$/.test(url)) {
    const i = db.dictData.findIndex((x) => x.id === idOf(url))
    if (i < 0) throw bizError('NOT_FOUND', '字典数据不存在')
    db.dictData.splice(i, 1)
    return null
  }

  // ---- 系统变量（参数配置）----
  if (m === 'GET' && url === '/api/system/configs') return db.configs.map((x) => ({ ...x }))
  if (m === 'GET' && /^\/api\/system\/configs\/key\/(.+)$/.test(url)) {
    const key = url.match(/^\/api\/system\/configs\/key\/(.+)$/)[1]
    const c = db.configs.find((x) => x.configKey === key)
    if (!c) throw bizError('NOT_FOUND', '配置不存在')
    return { ...c }
  }
  if (m === 'POST' && url === '/api/system/configs') {
    if (db.configs.some((c) => c.configKey === data.configKey)) throw bizError('CONFLICT', '配置键已存在')
    const id = ++seq
    db.configs.push({
      id,
      configKey: data.configKey,
      configName: data.configName,
      configValue: data.configValue || '',
      configType: data.configType || 'STRING',
      remark: data.remark || null,
      status: data.status || 'ACTIVE'
    })
    return id
  }
  if (m === 'PUT' && /^\/api\/system\/configs\/\d+$/.test(url)) {
    const c = db.configs.find((x) => x.id === idOf(url))
    if (!c) throw bizError('NOT_FOUND', '配置不存在')
    if (data.configKey != null && data.configKey !== c.configKey &&
        db.configs.some((x) => x.configKey === data.configKey)) throw bizError('CONFLICT', '配置键已存在')
    if (data.configKey != null) c.configKey = data.configKey
    if (data.configName != null) c.configName = data.configName
    if (data.configValue != null) c.configValue = data.configValue
    if (data.configType != null) c.configType = data.configType
    if (data.remark != null) c.remark = data.remark
    if (data.status != null) c.status = data.status
    return null
  }
  if (m === 'DELETE' && /^\/api\/system\/configs\/\d+$/.test(url)) {
    const i = db.configs.findIndex((x) => x.id === idOf(url))
    if (i < 0) throw bizError('NOT_FOUND', '配置不存在')
    db.configs.splice(i, 1)
    return null
  }

// ============ 接口资源扫描（模拟后端 @PreAuthorize 扫描结果） ============
// 场景设计：多数已同步、少数新增、个别变更、一条库中失效，用于演示差异对比。
const scannedApiResources = [
  { httpMethod: 'GET', pathPattern: '/api/system/users/page', permissionCode: 'system:user:read', resourceName: '用户分页查询', controllerMethod: 'UserController#page', authMode: 'REQUIRED' },
  { httpMethod: 'POST', pathPattern: '/api/system/users', permissionCode: 'system:user:create', resourceName: '新增用户', controllerMethod: 'UserController#create', authMode: 'REQUIRED' },
  { httpMethod: 'PUT', pathPattern: '/api/system/users/{id}', permissionCode: 'system:user:update', resourceName: '更新用户', controllerMethod: 'UserController#update', authMode: 'REQUIRED' },
  { httpMethod: 'DELETE', pathPattern: '/api/system/users/{id}', permissionCode: 'system:user:delete', resourceName: '删除用户', controllerMethod: 'UserController#delete', authMode: 'REQUIRED' },
  { httpMethod: 'GET', pathPattern: '/api/system/roles', permissionCode: 'system:role:read', resourceName: '角色列表', controllerMethod: 'RoleController#list', authMode: 'REQUIRED' },
  { httpMethod: 'POST', pathPattern: '/api/system/roles', permissionCode: 'system:role:create', resourceName: '新增角色', controllerMethod: 'RoleController#create', authMode: 'REQUIRED' },
  { httpMethod: 'GET', pathPattern: '/api/system/roles/{id}/data-scopes', permissionCode: 'system:role:read', resourceName: '角色数据权限列表', controllerMethod: 'RoleController#listDataScopes', authMode: 'REQUIRED' },
  { httpMethod: 'PUT', pathPattern: '/api/system/roles/{id}/data-scopes', permissionCode: 'system:role:update', resourceName: '保存角色数据权限（覆盖式）', controllerMethod: 'RoleController#saveDataScope', authMode: 'REQUIRED' },
  { httpMethod: 'GET', pathPattern: '/api/system/menus/tree', permissionCode: 'system:menu:read', resourceName: '菜单树', controllerMethod: 'MenuController#tree', authMode: 'REQUIRED' },
  { httpMethod: 'GET', pathPattern: '/api/system/orgs/tree', permissionCode: 'system:org:read', resourceName: '部门树', controllerMethod: 'OrgController#tree', authMode: 'REQUIRED' },
  { httpMethod: 'GET', pathPattern: '/api/system/api-resources', permissionCode: 'system:api:read', resourceName: '接口资源列表', controllerMethod: 'ApiResourceController#list', authMode: 'REQUIRED' },
  { httpMethod: 'POST', pathPattern: '/api/system/api-resources', permissionCode: 'system:api:create', resourceName: '注册接口资源', controllerMethod: 'ApiResourceController#create', authMode: 'REQUIRED' },
  { httpMethod: 'PUT', pathPattern: '/api/system/api-resources/{id}', permissionCode: 'system:api:update', resourceName: '更新接口资源', controllerMethod: 'ApiResourceController#update', authMode: 'REQUIRED' },
  { httpMethod: 'DELETE', pathPattern: '/api/system/api-resources/{id}', permissionCode: 'system:api:delete', resourceName: '删除接口资源', controllerMethod: 'ApiResourceController#delete', authMode: 'REQUIRED' },
  // 以下为尚未登记的新接口 → 扫描后应出现在「待新增」
  { httpMethod: 'GET', pathPattern: '/api/system/api-resources/scan', permissionCode: 'system:api:read', resourceName: '扫描接口资源并预览差异', controllerMethod: 'ApiResourceController#scanPreview', authMode: 'REQUIRED' },
  { httpMethod: 'POST', pathPattern: '/api/system/api-resources/scan/sync', permissionCode: 'system:api:sync', resourceName: '执行接口资源扫描同步', controllerMethod: 'ApiResourceController#sync', authMode: 'REQUIRED' },
  { httpMethod: 'GET', pathPattern: '/api/system/monitor/metrics', permissionCode: 'system:monitor:read', resourceName: '服务器指标', controllerMethod: 'MonitorController#metrics', authMode: 'REQUIRED' },
  { httpMethod: 'GET', pathPattern: '/api/profile', permissionCode: '-', resourceName: '个人资料查询', controllerMethod: 'ProfileController#profile', authMode: 'REQUIRED' }
]

/**
 * 比对「扫描结果」与「库中记录」，产出与真实后端同构的差异结构。
 * 与后端 shouldPersist 保持一致：免鉴权接口、或无权限码（permissionCode='-'）的接口均不落库。
 */
function scanApiResourceDiff() {
  const persist = scannedApiResources.filter(
    (r) => r.authMode !== 'ANONYMOUS' && r.permissionCode && r.permissionCode !== '-'
  )
  const newResources = []
  const changedResources = []
  const liveKeys = new Set()
  let unchangedCount = 0

  persist.forEach((r) => {
    const key = `${r.httpMethod} ${r.pathPattern}`
    liveKeys.add(key)
    const cur = db.apiResources.find((x) => x.httpMethod === r.httpMethod && x.pathPattern === r.pathPattern)
    if (!cur) {
      newResources.push({ ...r })
    } else if (
      cur.permissionCode !== r.permissionCode ||
      cur.resourceName !== r.resourceName ||
      cur.authMode !== r.authMode
    ) {
      changedResources.push({ ...r })
    } else {
      unchangedCount++
    }
  })

  const orphanedResources = db.apiResources
    .filter((x) => !liveKeys.has(`${x.httpMethod} ${x.pathPattern}`))
    .map((x) => ({ ...x }))

  return {
    newResources,
    changedResources,
    orphanedResources,
    unchangedCount,
    total: scannedApiResources.length,
    skippedCount: scannedApiResources.length - persist.length
  }
}

// ============ 个人中心 / 账号设置（/api/profile） ============
  // 演示环境固定以 admin(id=1) 作为「当前登录用户」，与登录态保持一致。
  const me = () => db.users.find((u) => u.id === 1)

  if (m === 'GET' && url === '/api/profile') {
    const u = me()
    return {
      id: u.id,
      username: u.username,
      displayName: u.displayName,
      avatarUrl: u.avatarUrl || null,
      mobile: u.mobile || null,
      mobileBound: !!u.mobile,
      email: u.email || null,
      emailBound: !!u.email,
      roleName: u.roleName,
      orgName: '技术部',
      status: u.status,
      lastLoginAt: u.lastLoginAt || null,
      passwordChangedAt: '2026-09-01 10:00:00',
      createdAt: u.createdAt
    }
  }
  if (m === 'PUT' && url === '/api/profile') {
    const u = me()
    if (data.displayName) u.displayName = data.displayName
    if (data.mobile !== undefined) u.mobile = data.mobile || null
    if (data.email !== undefined) u.email = data.email || null
    if (data.avatarUrl !== undefined) u.avatarUrl = data.avatarUrl || null
    return this.mockRequest({ method: 'GET', url: '/api/profile' })
  }
  if (m === 'PUT' && url === '/api/profile/contact') {
    const u = me()
    const v = (data.contact || '').trim()
    if (data.channel === 'MOBILE') {
      if (v && !/^1[3-9]\d{9}$/.test(v)) throw bizError('VALIDATION_ERROR', '手机号格式不正确')
      u.mobile = v || null
      if (!v) u.notifyMobile = 0
    } else {
      if (v && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v)) throw bizError('VALIDATION_ERROR', '邮箱格式不正确')
      u.email = v || null
      if (!v) u.notifyEmail = 0
    }
    return this.mockRequest({ method: 'GET', url: '/api/profile' })
  }
  if (m === 'POST' && url === '/api/profile/avatar') {
    // 演示环境不落盘，用 data URL 直接回显，所见即所得
    const u = me()
    u.avatarUrl = 'data:image/svg+xml;charset=utf-8,' +
      encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72"><rect width="72" height="72" fill="#409eff"/><text x="36" y="46" font-size="32" fill="#fff" text-anchor="middle">${(u.displayName || 'U').charAt(0)}</text></svg>`)
    return this.mockRequest({ method: 'GET', url: '/api/profile' })
  }
  if (m === 'POST' && url === '/api/profile/password') {
    if (!data.oldPassword) throw bizError('VALIDATION_ERROR', '原密码不能为空')
    if (!data.newPassword || data.newPassword.length < 8) {
      throw bizError('VALIDATION_ERROR', '密码长度需为 8~64 位')
    }
    return null
  }
  if (m === 'GET' && url === '/api/profile/preference') {
    const u = me()
    return {
      notifySiteMessage: !!u.notifySiteMessage,
      notifyEmail: !!u.notifyEmail,
      notifyMobile: !!u.notifyMobile,
      showLoginLog: !!u.showLoginLog,
      maskMobile: !!u.maskMobile,
      discoverable: !!u.discoverable
    }
  }
  if (m === 'PUT' && url === '/api/profile/preference') {
    const u = me()
    const keys = ['notifySiteMessage', 'notifyEmail', 'notifyMobile', 'showLoginLog', 'maskMobile', 'discoverable']
    keys.forEach((k) => {
      if (data[k] != null) u[k] = data[k]
    })
    return this.mockRequest({ method: 'GET', url: '/api/profile/preference' })
  }
  if (m === 'GET' && url === '/api/profile/devices') {
    return db.devices
  }
  if (m === 'DELETE' && /^\/api\/profile\/devices\/.+$/.test(url)) {
    const sessionId = decodeURIComponent(url.split('/').pop())
    const i = db.devices.findIndex((x) => x.sessionId === sessionId)
    if (i < 0) throw bizError('NOT_FOUND', '该设备不存在或已下线')
    if (db.devices[i].current) throw bizError('VALIDATION_ERROR', '不能下线当前设备')
    db.devices.splice(i, 1)
    return null
  }
  if (m === 'POST' && url === '/api/profile/devices/logout-others') {
    const others = db.devices.filter((x) => !x.current)
    db.devices = db.devices.filter((x) => x.current)
    return others.length
  }

  throw bizError('NOT_FOUND', `Mock 未实现的接口: ${m} ${url}`)
}
