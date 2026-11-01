import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 暗色变量表：仅在 html.dark 下生效，配合主题系统深色调使用
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles.css'
import './styles/theme-tokens.css'
import './styles/element-theme.css'
import { t } from './i18n'
import { initTheme } from './utils/theme'

const app = createApp(App)

// 全局注册所有 Element Plus 图标，模板中可直接 <component :is="iconName" /> 或 <User />
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

// 注册全局 $t，便于任意模板在不显式 import 的情况下使用翻译；
// 组件内也可直接从 '@/i18n' 导入 t / backendLocale / elLocale。
app.config.globalProperties.$t = t

app.use(router)
app.use(ElementPlus)
// 与持久化主题对齐（index.html 内联脚本已先行同步，此处幂等，避免首屏闪烁）
initTheme()
app.mount('#app')
