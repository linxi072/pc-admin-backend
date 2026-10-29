import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated, authState } from '@/store/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: 'route.loginTitle' }
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
        meta: { title: 'route.dashboard' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: 'route.profile' }
      },
      {
        path: 'system/user',
        name: 'user',
        component: () => import('@/views/system/UserView.vue'),
        meta: { title: 'route.user' }
      },
      {
        path: 'system/role',
        name: 'role',
        component: () => import('@/views/system/RoleView.vue'),
        meta: { title: 'route.role' }
      },
      {
        path: 'system/menu',
        name: 'menu',
        component: () => import('@/views/system/MenuView.vue'),
        meta: { title: 'route.menu' }
      },
      {
        path: 'system/api-resource',
        name: 'apiResource',
        component: () => import('@/views/system/ApiResourceView.vue'),
        meta: { title: 'route.apiResource' }
      },
      {
        path: 'system/department',
        name: 'department',
        component: () => import('@/views/system/DepartmentView.vue'),
        meta: { title: 'route.department' }
      },
      {
        // 岗位管理（V35）：岗位 CRUD + 用户岗位分配，菜单与权限由后端驱动
        path: 'system/post',
        name: 'post',
        component: () => import('@/views/system/PostView.vue'),
        meta: { title: 'route.post', permission: 'system:post:read' }
      },
      {
        path: 'system/dict',
        name: 'dict',
        component: () => import('@/views/system/DictView.vue'),
        meta: { title: 'route.dict' }
      },
      {
        path: 'system/config',
        name: 'config',
        component: () => import('@/views/system/ConfigView.vue'),
        meta: { title: 'route.config' }
      },
      {
        // 消息中心：合并「系统公告」与「站内信」，页内以 Tab 切换
        path: 'system/notice',
        name: 'notice',
        component: () => import('@/views/system/NoticeView.vue'),
        meta: { title: 'route.notice', permission: 'system:announcement:read' }
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
        meta: { title: 'route.monitor', permission: 'system:monitor:read' }
      },
      {
        // 在线用户管理（V27）：在线会话跟踪 / 强制下线 / 临时限制登录，菜单与权限由后端驱动
        path: 'system/online',
        name: 'onlineUser',
        component: () => import('@/views/system/OnlineUserView.vue'),
        meta: { title: 'route.onlineUser', permission: 'system:online:read' }
      },
      {
        // 缓存监控（V29）：进程内缓存条目/命中率/淘汰统计与清理，菜单与权限由后端驱动
        path: 'system/cache',
        name: 'cacheMonitor',
        component: () => import('@/views/system/CacheMonitorView.vue'),
        meta: { title: 'route.cacheMonitor', permission: 'system:monitor:read' }
      },
      {
        // 连接池监控（V29）：HikariCP 活跃/空闲/等待线程与池配置，菜单与权限由后端驱动
        path: 'system/datasource',
        name: 'poolMonitor',
        component: () => import('@/views/system/PoolMonitorView.vue'),
        meta: { title: 'route.poolMonitor', permission: 'system:monitor:read' }
      },
      {
        // 登录日志（V29）：登录成功/失败逐条明细（安全审计），菜单与权限由后端驱动
        path: 'system/login-log',
        name: 'loginLog',
        component: () => import('@/views/system/LoginLogView.vue'),
        meta: { title: 'route.loginLog', permission: 'system:login-log:read' }
      },
      {
        // 操作日志（V29）：审计日志明细与 traceId 链路，菜单与权限由后端驱动
        path: 'system/operation-log',
        name: 'operationLog',
        component: () => import('@/views/system/OperationLogView.vue'),
        meta: { title: 'route.operationLog', permission: 'system:audit:read' }
      },
      {
        // 短信记录（V37）：短信渠道发送明细（对账排查），入库前已脱敏，菜单与权限由后端驱动
        path: 'system/sms-record',
        name: 'smsRecord',
        component: () => import('@/views/system/SmsLogView.vue'),
        meta: { title: 'route.smsRecord', permission: 'system:sms-record:read' }
      },
      {
        // 通知模板中心（V41）：为 M6 多渠道通知提供可复用 ${var} 模板；权限码与菜单由后端驱动
        path: 'system/notification-templates',
        name: 'notificationTemplate',
        component: () => import('@/views/system/NotificationTemplate.vue'),
        meta: { title: 'route.notificationTemplate', permission: 'system:notification-template:read' }
      },
      {
        // 通知记录（V42）：全五渠道统一发送审计面（含被拒与失败），入库前已脱敏摘要，菜单与权限由后端驱动
        path: 'system/notification-records',
        name: 'notificationRecord',
        component: () => import('@/views/system/NotificationRecordView.vue'),
        meta: { title: 'route.notificationRecord', permission: 'system:notification-record:read' }
      },
      {
        path: 'workflow/task',
        name: 'task',
        component: () => import('@/views/workflow/TaskView.vue'),
        meta: { title: 'route.task' }
      },
      {
        path: 'workflow/instance',
        name: 'instance',
        component: () => import('@/views/workflow/InstanceView.vue'),
        meta: { title: 'route.instance' }
      },
      {
        path: 'workflow/definition',
        name: 'definition',
        component: () => import('@/views/workflow/DesignListView.vue'),
        meta: { title: 'route.definition', permission: 'workflow:definition:read' }
      },
      {
        // 流程监控（M23）：运行实例 / 超时任务 / 定义版本链 / 统计摘要 + 催办，
        // 与「我发起的/我的待办」个人面互补的管理员全局视角。
        path: 'workflow/monitor',
        name: 'workflowMonitor',
        component: () => import('@/views/workflow/MonitorView.vue'),
        meta: { title: 'route.workflowMonitor', permission: 'workflow:monitor:read' }
      },
      {
        path: 'workflow/designer/:id',
        name: 'designer',
        component: () => import('@/views/workflow/WorkflowDesigner.vue'),
        meta: { title: 'route.designer', permission: 'workflow:definition:update' }
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
        meta: { title: 'route.reportJobExec', permission: 'report:read' }
      },
      {
        // 报表-登录统计：登录次数 / 活跃用户趋势 / 异常登录记录
        path: 'report/login',
        name: 'reportLogin',
        component: () => import('@/views/report/LoginReportView.vue'),
        meta: { title: 'route.reportLogin', permission: 'report:read' }
      },
      {
        // 报表-接口调用统计：调用量 / 响应时间分布 / 错误率 / Top 接口
        path: 'report/api-call',
        name: 'reportApiCall',
        component: () => import('@/views/report/ApiCallReportView.vue'),
        meta: { title: 'route.reportApiCall', permission: 'report:read' }
      },
      {
        // 报表大屏（M27）：运营总览 + 时间序列趋势，菜单与权限由后端驱动
        // （menu_code=system:dashboard, permission=system:dashboard:read）
        path: 'report/dashboard',
        name: 'reportDashboard',
        component: () => import('@/views/report/DashboardView.vue'),
        meta: { title: 'route.reportDashboard', permission: 'system:dashboard:read' }
      },
      {
        // 定时任务：在线任务管理（列表/启动/暂停/编辑/日志），菜单与权限由后端驱动
        path: 'job/list',
        name: 'jobList',
        component: () => import('@/views/JobListView.vue'),
        meta: { title: 'route.jobList', permission: 'job:read' }
      },
      {
        // 代码生成：按表结构生成 CRUD 骨架（后端六层 + 前端两文件），菜单与权限由后端驱动
        path: 'dev/codegen',
        name: 'codegen',
        component: () => import('@/views/CodegenView.vue'),
        meta: { title: 'route.codegen', permission: 'codegen:read' }
      },
      {
        // 文件管理（V33）：上传 / 下载 / 列表 / 删除，记录来源功能模块与上传人，
        // 菜单与权限由后端驱动（menu_code=file-management, permission=system:file:read）
        path: 'system/file',
        name: 'file',
        component: () => import('@/views/system/FileManagementView.vue'),
        meta: { title: 'route.file', permission: 'system:file:read' }
      },
      {
        // 导出中心（V34）：可复用 CSV 导出中心，模板由后端 ExportTask 自动发现，
        // 菜单与权限由后端驱动（menu_code=system:export, permission=system:export:read）
        path: 'system/export',
        name: 'exportCenter',
        component: () => import('@/views/system/ExportCenterView.vue'),
        meta: { title: 'route.exportCenter', permission: 'system:export:read' }
      },
      {
        // 导入中心（V36）：模板由后端 ImportTask 自动发现，支持 CSV / XLSX 上传与预检，
        // 菜单与权限由后端驱动（menu_code=system:import, permission=system:import:read）
        path: 'system/import',
        name: 'importCenter',
        component: () => import('@/views/system/ImportCenterView.vue'),
        meta: { title: 'route.importCenter', permission: 'system:import:read' }
      },
      {
        // 租户管理（V51 / M19 P2）：租户注册表 CRUD + 启停，菜单与权限由后端驱动
        // （menu_code=system:tenant, permission=system:tenant:read）
        path: 'system/tenants',
        name: 'tenant',
        component: () => import('@/views/system/TenantView.vue'),
        meta: { title: 'route.tenant', permission: 'system:tenant:read' }
      },
      {
        // 租户隔离态势（M28 只读运维诊断）：聚合隔离「配置态 + 运行态 + 当前执行作用域 + 配置校验结果」，
        // 供运维只读诊断；后端 GET /api/system/tenant-isolation/status，权限 system:tenant-isolation:read
        path: 'system/tenant-isolation',
        name: 'tenantIsolation',
        component: () => import('@/views/system/TenantIsolationView.vue'),
        meta: { title: 'route.tenantIsolation', permission: 'system:tenant-isolation:read' }
      },
      {
        // 脱敏策略中心（V46）：按字段键 + 角色豁免的统一脱敏策略配置面，
        // 供 M1 审计/M20 增强等既有脱敏点经 MaskStrategyResolver 分派；菜单与权限由后端驱动
        path: 'system/mask-strategies',
        name: 'maskStrategy',
        component: () => import('@/views/system/MaskStrategyView.vue'),
        meta: { title: 'route.maskStrategy', permission: 'system:mask-strategy:read' }
      },
      {
        // 实时推送管理（M29）：在线会话只读视图 + 运维侧手动广播/定向推送，
        // 后端 RealtimeController（/api/realtime），权限由后端驱动
        // （menu_code=system:realtime, permission=system:realtime:read；推送另需 system:realtime:push）
        path: 'system/realtime',
        name: 'realtime',
        component: () => import('@/views/system/RealtimeView.vue'),
        meta: { title: 'route.realtime', permission: 'system:realtime:read' }
      },
      {
        // 数据权限配置（M31）：角色 × 资源的数据范围可视化配置面，资源清单由后端
        // DataScopeResourceRegistry 自动扫描 @DataScope 得到；菜单与权限由后端驱动
        // （menu_code=system:data-scope, permission=system:data-scope:read；保存另需 system:role:update）
        path: 'system/data-scope',
        name: 'dataScope',
        component: () => import('@/views/system/DataScopeView.vue'),
        meta: { title: 'route.dataScope', permission: 'system:data-scope:read' }
      },
      {
        // 字段/数据权限统一配置（M41）：同一页面内以 Tab 同时配置「行级数据范围」与「列级字段明文可见权」，
        // 资源清单分别由后端 DataScopeResourceRegistry / ColumnPermissionResourceRegistry 自动扫描得到；
        // 菜单与权限由后端驱动（menu_code=system:permission-policy, permission=system:column-permission:read；保存另需 system:role:update）
        path: 'system/permission-policy',
        name: 'permissionPolicy',
        component: () => import('@/views/system/PermissionPolicyView.vue'),
        meta: { title: 'route.permissionPolicy', permission: 'system:column-permission:read' }
      },
      {
        // 低代码表单试点（V47 / M22 spike）：Schema 契约 + 零依赖渲染验证，提交落库与绑定目标
        // 为正式立项决策点；菜单与权限由后端驱动（menu_code=system:form-pilot,
        // permission=system:form-definition:read）
        path: 'system/form-pilot',
        name: 'formPilot',
        component: () => import('@/views/system/FormPilotView.vue'),
        meta: { title: 'route.formPilot', permission: 'system:form-definition:read' }
      },
      {
        // 表单定义管理（M22 深化）：表单定义 CRUD + JSON-Schema 可视化编辑 + 实时预览；
        // 后端接口前缀 /api/system/form-definitions，读权限复用 system:form-definition:read，
        // 写权限 system:form-definition:create/update/delete（自动登记 SUPER_ADMIN）
        path: 'system/form-definition',
        name: 'formDefinition',
        component: () => import('@/views/system/FormDefinitionView.vue'),
        meta: { title: 'route.formDefinition', permission: 'system:form-definition:read' }
      },
      {
        // 表单提交管理（M47 跨表单统一提交管理）：跨表单检索/统计/详情/逻辑删除 + 导出中心内置模板；
        // 后端 FormSubmissionAdminController（/api/system/form-submissions），权限由后端驱动
        // （menu_code=system:form-submission, permission=system:form-submission:read；删除另需 system:form-submission:delete）
        path: 'system/form-submissions',
        name: 'formSubmissionManage',
        component: () => import('@/views/system/FormSubmissionManageView.vue'),
        meta: { title: 'route.formSubmission', permission: 'system:form-submission:read' }
      },
      {
        // 开放平台客户端（V48 / M24）：客户端注册 + 密钥治理（明文一次性展示/哈希落库/轮换/停用），
        // 授权服务器依赖不在离线仓库暂无 token 端点；菜单与权限由后端驱动
        // （menu_code=system:openapi-client, permission=system:openapi-client:read）
        path: 'system/openapi-clients',
        name: 'openApiClient',
        component: () => import('@/views/system/OpenApiClientView.vue'),
        meta: { title: 'route.openApiClient', permission: 'system:openapi-client:read' }
      },
      {
        // 可观测中心：链路追踪 / Kafka 监控 / N+1 疑似 / 幂等记录 / 告警收件箱五个运维面统一入口，
        // 页内 Tab 各自对应后端独立权限（observability/kafka/monitor/idempotency:read），
        // 无权限 Tab 展示降级空态；菜单与权限由后端驱动（menu_code=system:observability-center,
        // permission=system:monitor:read——监控域基础权限，与「系统监控」同权）
        path: 'system/observability',
        name: 'observabilityCenter',
        component: () => import('@/views/monitor/ObservabilityCenterView.vue'),
        meta: { title: 'route.observabilityCenter', permission: 'system:monitor:read' }
      },
      {
        // 健康探针（M42）：可视化 /livez、/readyz 两个 k8s 风格根路径探针的存活/就绪状态与明细，
        // 后端 ProbeController（scaffold-monitor）已提供，permitAll；读权限复用监控域基础权限
        // system:monitor:read（与「系统监控」「缓存监控」同权），菜单与权限由后端驱动
        // （menu_code=system:health, permission=system:monitor:read）
        path: 'system/health',
        name: 'health',
        component: () => import('@/views/system/HealthView.vue'),
        meta: { title: 'route.health', permission: 'system:monitor:read' }
      },
      {
        // OAuth2 令牌管理（M43）：活动令牌分页查询 + 即时吊销治理面，
        // 后端 OAuth2TokenController（/api/system/oauth2-tokens），权限由后端驱动
        // （menu_code=system:oauth2-token, permission=system:oauth2-token:read；吊销另需 system:oauth2-token:revoke）
        path: 'system/oauth2-tokens',
        name: 'oauth2Token',
        component: () => import('@/views/system/TokenManagerView.vue'),
        meta: { title: 'route.oauth2Token', permission: 'system:oauth2-token:read' }
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
