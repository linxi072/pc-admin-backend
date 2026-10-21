import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated, authState } from '@/store/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'system/user',
        name: 'user',
        component: () => import('@/views/system/UserView.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'system/role',
        name: 'role',
        component: () => import('@/views/system/RoleView.vue'),
        meta: { title: '角色管理' }
      },
      {
        path: 'system/menu',
        name: 'menu',
        component: () => import('@/views/system/MenuView.vue'),
        meta: { title: '菜单管理' }
      },
      {
        path: 'system/api-resource',
        name: 'apiResource',
        component: () => import('@/views/system/ApiResourceView.vue'),
        meta: { title: '接口资源管理' }
      },
      {
        path: 'system/department',
        name: 'department',
        component: () => import('@/views/system/DepartmentView.vue'),
        meta: { title: '部门管理' }
      },
      {
        // 岗位管理（V35）：岗位 CRUD + 用户岗位分配，菜单与权限由后端驱动
        path: 'system/post',
        name: 'post',
        component: () => import('@/views/system/PostView.vue'),
        meta: { title: '岗位管理', permission: 'system:post:read' }
      },
      {
        path: 'system/dict',
        name: 'dict',
        component: () => import('@/views/system/DictView.vue'),
        meta: { title: '字典管理' }
      },
      {
        path: 'system/config',
        name: 'config',
        component: () => import('@/views/system/ConfigView.vue'),
        meta: { title: '系统变量' }
      },
      {
        // 消息中心：合并「系统公告」与「站内信」，页内以 Tab 切换
        path: 'system/notice',
        name: 'notice',
        component: () => import('@/views/system/NoticeView.vue'),
        meta: { title: '消息中心', permission: 'system:announcement:read' }
      },
      {
        // 旧入口保留重定向，避免历史书签/菜单配置失效
        path: 'system/announcement',
        redirect: '/system/notice'
      },
      {
        path: 'system/message',
        redirect: (to) => ({ path: '/system/notice', query: { tab: 'message', ...to.query } })
      },
      {
        path: 'system/monitor',
        name: 'monitor',
        component: () => import('@/views/system/MonitorView.vue'),
        meta: { title: '系统监控', permission: 'system:monitor:read' }
      },
      {
        // 在线用户管理（V27）：在线会话跟踪 / 强制下线 / 临时限制登录，菜单与权限由后端驱动
        path: 'system/online',
        name: 'onlineUser',
        component: () => import('@/views/system/OnlineUserView.vue'),
        meta: { title: '在线用户管理', permission: 'system:online:read' }
      },
      {
        // 缓存监控（V29）：进程内缓存条目/命中率/淘汰统计与清理，菜单与权限由后端驱动
        path: 'system/cache',
        name: 'cacheMonitor',
        component: () => import('@/views/system/CacheMonitorView.vue'),
        meta: { title: '缓存监控', permission: 'system:monitor:read' }
      },
      {
        // 连接池监控（V29）：HikariCP 活跃/空闲/等待线程与池配置，菜单与权限由后端驱动
        path: 'system/datasource',
        name: 'poolMonitor',
        component: () => import('@/views/system/PoolMonitorView.vue'),
        meta: { title: '连接池监控', permission: 'system:monitor:read' }
      },
      {
        // 登录日志（V29）：登录成功/失败逐条明细（安全审计），菜单与权限由后端驱动
        path: 'system/login-log',
        name: 'loginLog',
        component: () => import('@/views/system/LoginLogView.vue'),
        meta: { title: '登录日志', permission: 'system:login-log:read' }
      },
      {
        // 操作日志（V29）：审计日志明细与 traceId 链路，菜单与权限由后端驱动
        path: 'system/operation-log',
        name: 'operationLog',
        component: () => import('@/views/system/OperationLogView.vue'),
        meta: { title: '操作日志', permission: 'system:audit:read' }
      },
      {
        // 短信记录（V37）：短信渠道发送明细（对账排查），入库前已脱敏，菜单与权限由后端驱动
        path: 'system/sms-record',
        name: 'smsRecord',
        component: () => import('@/views/system/SmsLogView.vue'),
        meta: { title: '短信记录', permission: 'system:sms-record:read' }
      },
      {
        path: 'workflow/task',
        name: 'task',
        component: () => import('@/views/workflow/TaskView.vue'),
        meta: { title: '我的待办' }
      },
      {
        path: 'workflow/instance',
        name: 'instance',
        component: () => import('@/views/workflow/InstanceView.vue'),
        meta: { title: '我发起的流程' }
      },
      {
        path: 'workflow/definition',
        name: 'definition',
        component: () => import('@/views/workflow/DesignListView.vue'),
        meta: { title: '工作流设计', permission: 'workflow:definition:read' }
      },
      {
        path: 'workflow/designer/:id',
        name: 'designer',
        component: () => import('@/views/workflow/WorkflowDesigner.vue'),
        meta: { title: '工作流设计器', permission: 'workflow:definition:update' }
      },
      {
        // 报表入口：V30 移除公共统计页（使用/类别/状态）后，旧入口重定向到保留的
        // 首个统计页，避免历史书签/外部链接失效；V31 起 3 个统计页统一归属
        // 「报表管理」一级菜单（后端菜单树驱动，路由 path 不变）
        path: 'report/management',
        redirect: '/report/job-exec'
      },
      {
        // 报表-定时任务执行统计：成功率 / 失败率 / 耗时分布 / 最近执行明细
        path: 'report/job-exec',
        name: 'reportJobExec',
        component: () => import('@/views/report/JobExecReportView.vue'),
        meta: { title: '定时任务执行统计', permission: 'report:read' }
      },
      {
        // 报表-登录统计：登录次数 / 活跃用户趋势 / 异常登录记录
        path: 'report/login',
        name: 'reportLogin',
        component: () => import('@/views/report/LoginReportView.vue'),
        meta: { title: '登录统计', permission: 'report:read' }
      },
      {
        // 报表-接口调用统计：调用量 / 响应时间分布 / 错误率 / Top 接口
        path: 'report/api-call',
        name: 'reportApiCall',
        component: () => import('@/views/report/ApiCallReportView.vue'),
        meta: { title: '接口调用统计', permission: 'report:read' }
      },
      {
        // 定时任务：在线任务管理（列表/启动/暂停/编辑/日志），菜单与权限由后端驱动
        path: 'job/list',
        name: 'jobList',
        component: () => import('@/views/JobListView.vue'),
        meta: { title: '定时任务', permission: 'job:read' }
      },
      {
        // 代码生成：按表结构生成 CRUD 骨架（后端六层 + 前端两文件），菜单与权限由后端驱动
        path: 'dev/codegen',
        name: 'codegen',
        component: () => import('@/views/CodegenView.vue'),
        meta: { title: '代码生成', permission: 'codegen:read' }
      },
      {
        // 文件管理（V33）：上传 / 下载 / 列表 / 删除，记录来源功能模块与上传人，
        // 菜单与权限由后端驱动（menu_code=file-management, permission=system:file:read）
        path: 'system/file',
        name: 'file',
        component: () => import('@/views/system/FileManagementView.vue'),
        meta: { title: '文件管理', permission: 'system:file:read' }
      },
      {
        // 导出中心（V34）：可复用 CSV 导出中心，模板由后端 ExportTask 自动发现，
        // 菜单与权限由后端驱动（menu_code=system:export, permission=system:export:read）
        path: 'system/export',
        name: 'exportCenter',
        component: () => import('@/views/system/ExportCenterView.vue'),
        meta: { title: '导出中心', permission: 'system:export:read' }
      },
      {
        // 导入中心（V36）：模板由后端 ImportTask 自动发现，支持 CSV / XLSX 上传与预检，
        // 菜单与权限由后端驱动（menu_code=system:import, permission=system:import:read）
        path: 'system/import',
        name: 'importCenter',
        component: () => import('@/views/system/ImportCenterView.vue'),
        meta: { title: '导入中心', permission: 'system:import:read' }
      },
      {
        // 可观测中心：链路追踪 / Kafka 监控 / N+1 疑似 / 幂等记录 / 告警收件箱五个运维面统一入口，
        // 页内 Tab 各自对应后端独立权限（observability/kafka/monitor/idempotency:read），
        // 无权限 Tab 展示降级空态；菜单与权限由后端驱动（menu_code=system:observability-center,
        // permission=system:monitor:read——监控域基础权限，与「系统监控」同权）
        path: 'system/observability',
        name: 'observabilityCenter',
        component: () => import('@/views/monitor/ObservabilityCenterView.vue'),
        meta: { title: '可观测中心', permission: 'system:monitor:read' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 收集菜单树中所有可见叶子路由路径（用于在路由守卫中判断「是否在当前用户角色菜单范围内」）
function collectAuthorizedPaths(menus) {
  const set = new Set()
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if (n.routePath) set.add(n.routePath)
      walk(n.children)
    }
  }
  walk(menus)
  return set
}

router.beforeEach((to) => {
  // 登录页对已登录用户不可达（直接重定向工作台）：public 分支需先处理该特例，
  // 否则下方 login 判断因 public 提前放行而成为死代码
  if (to.meta.public) {
    if (to.name === 'login' && isAuthenticated()) {
      return { name: 'dashboard' }
    }
    return true
  }
  if (!isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 动态菜单范围鉴权：菜单尚未加载（首屏/刷新瞬间）时放行，避免误拦截；
  // 菜单加载完成后，凡不在用户菜单树中的受保护路由，视为未授权，重定向到工作台。
  // 带路径参数的详情页（如 /workflow/designer/:id）通常由已授权列表页进入，予以放行。
  const menus = authState.menus
  if (menus && menus.length) {
    const authorized = collectAuthorizedPaths(menus)
    const isDetail = to.matched.some((r) => r.path.includes(':'))
    if (to.path !== '/' && to.path !== '/dashboard' && !authorized.has(to.path) && !isDetail) {
      return { name: 'dashboard' }
    }
  }
  return true
})

export default router
