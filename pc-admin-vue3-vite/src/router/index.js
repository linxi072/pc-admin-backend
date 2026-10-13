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
        // 报表管理：原单页多 Tab 已拆分为 6 个独立菜单页（V24，父菜单「报表管理」目录化），
        // 旧入口保留重定向，避免历史书签/外部链接失效
        path: 'report/management',
        redirect: '/report/usage'
      },
      {
        // 报表-使用统计：访问量 / 活跃用户数 / 成功率 / 类别分布 / 状态分布
        path: 'report/usage',
        name: 'reportUsage',
        component: () => import('@/views/report/UsageReportView.vue'),
        meta: { title: '使用统计', permission: 'report:read' }
      },
      {
        // 报表-类别统计：按功能模块汇总操作频次与占比
        path: 'report/category',
        name: 'reportCategory',
        component: () => import('@/views/report/CategoryReportView.vue'),
        meta: { title: '类别统计', permission: 'report:read' }
      },
      {
        // 报表-状态统计：按操作成功 / 失败状态汇总分布
        path: 'report/status',
        name: 'reportStatus',
        component: () => import('@/views/report/StatusReportView.vue'),
        meta: { title: '状态统计', permission: 'report:read' }
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
  if (to.meta.public) return true
  if (!isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && isAuthenticated()) {
    return { name: 'dashboard' }
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
