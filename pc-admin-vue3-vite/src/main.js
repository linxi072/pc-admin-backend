import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles.css'
import { t } from './i18n'

const app = createApp(App)

// 全局注册所有 Element Plus 图标，模板中可直接 <component :is="iconName" /> 或 <User />
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

// 注册全局 $t，便于任意模板在不显式 import 的情况下使用翻译；
// 组件内也可直接从 '@/i18n' 导入 t / setLocale / availableLocales。
app.config.globalProperties.$t = t

app.use(router)
app.use(ElementPlus)
app.mount('#app')
