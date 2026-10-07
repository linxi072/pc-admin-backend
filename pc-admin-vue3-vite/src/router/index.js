import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/store/auth'

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
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (!to.meta.public && !isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && isAuthenticated()) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
