import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 暗色变量表：仅在 html.dark 下生效，配合主题系统深色调使用
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'

import App from './App.vue'
import router from './router'
import './styles.css'
import './styles/theme-tokens.css'
import './styles/element-theme.css'
import { t } from './i18n'
import { initTheme } from './utils/theme'

const app = createApp(App)

/**
 * 全局错误边界（关键修复）：登录成功后「首页静默空白、控制台无任何报错」的根因在此。
 *
 * <p>Vue 3 默认【没有】全局错误边界：当任意组件（含 AppLayout 顶栏/侧栏、DashboardView 等）
 * 在渲染 / setup / 侦听器 / 生命周期中抛出未处理异常时，错误会沿组件树向上冒泡，
 * 触发其所有祖先组件卸载 → 整个后台外壳（el-container 整棵子树）被销毁 → 页面静默空白，
 * 且默认仅 console.error 一行堆栈、对用户完全不可见，极难定位。
 *
 * <p>设置 app.config.errorHandler 后，Vue 会【就地捕获并停止冒泡】该错误：
 * 仅出错的子树渲染为空，而外壳（侧栏 / 顶栏 / 主内容容器）始终保留，
 * 同时我们显式弹出可见通知 + 完整堆栈，使「白屏」转化为「可诊断、可恢复的错误」。
 *
 * <p>限频（同一错误 800ms 内只弹一次）避免渲染循环中被同一异常刷屏。
 */
let lastErrorAt = 0
function reportError(err, info) {
  console.error('[全局错误捕获]', info || '', err)
  const now = Date.now()
  if (now - lastErrorAt > 800) {
    lastErrorAt = now
    const message = (err && err.message) || String(err)
    ElNotification({
      title: t('app.errorTitle') || '页面渲染出错',
      message: message + (info ? `（${info}）` : ''),
      type: 'error',
      duration: 6000,
      showClose: true
    })
  }
}
app.config.errorHandler = (err, instance, info) => reportError(err, info)

// Promise 未捕获拒绝兜底：async setup / 未 await 的异常链若未被组件树捕获，
// 同样不应导致静默白屏，统一汇聚到同一上报通道。
window.addEventListener('unhandledrejection', (e) => {
  console.error('[未处理的 Promise 拒绝]', e.reason)
  reportError(e.reason instanceof Error ? e.reason : new Error(String(e.reason)), 'unhandledrejection')
})

// 全局注册所有 Element Plus 图标，模板中可直接 <component :is="iconName" /> 或 <User />
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

// 注册全局 $t，便于任意模板在不显式 import 的情况下使用翻译；
// 组件内也可直接从 '@/i18n' 导入 t / backendLocale / elLocale。
app.config.globalProperties.$t = t
// 兜底别名：模板中若直接写 t(...) 而未在 <script setup> 导入，也能解析到 i18n 函数，
// 避免“t is not a function”这类因漏导入导致的整页渲染报错（见 DashboardView 历史回归）。
app.config.globalProperties.t = t

app.use(router)
app.use(ElementPlus)
// 与持久化主题对齐（index.html 内联脚本已先行同步，此处幂等，避免首屏闪烁）
initTheme()
app.mount('#app')
