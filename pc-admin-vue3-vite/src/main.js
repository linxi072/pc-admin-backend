import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)

// 全局注册所有 Element Plus 图标，模板中可直接 <component :is="iconName" /> 或 <User />
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}

app.use(router)
app.use(ElementPlus)
app.mount('#app')
