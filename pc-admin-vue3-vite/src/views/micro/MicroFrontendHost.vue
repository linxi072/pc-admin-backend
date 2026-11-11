<template>
  <div>
    <h2 class="page-title">{{ '微前端宿主' }}</h2>

    <el-alert :title="'离线安全微前端主壳：扫描 src/micro-apps/*/index.js，按 mount/unmount 契约懒加载子应用（零外部依赖，任意技术栈可挂载）。'" type="info" :closable="false" class="host-alert" />

    <div class="host-layout">
      <!-- 子应用清单：由 src/micro-apps/*/index.js 目录结构驱动 -->
      <el-card shadow="never" class="app-list-card">
        <template #header>
          <span>{{ '可用子应用' }}（{{ apps.length }}）</span>
        </template>
        <div
          v-for="app in apps"
          :key="app.name"
          class="app-item"
          :class="{ active: app.name === activeAppName }"
          @click="selectApp(app.name)"
        >
          <el-icon><Box /></el-icon>
          <span class="app-name">{{ app.title }}</span>
          <span class="app-code">{{ app.name }}</span>
        </div>
        <el-empty v-if="apps.length === 0" :description="'未发现子应用（micro-apps 目录为空）'" :image-size="70" />
      </el-card>

      <!-- 挂载容器：mount/unmount 生命周期由主壳调度 -->
      <el-card shadow="never" class="mount-card">
        <template #header>
          <span>{{ '已挂载子应用' }}：{{ activeTitle || '无' }}</span>
        </template>
        <div ref="mountContainer" class="mount-container">
          <el-empty v-if="!activeAppName" :description="'请选择左侧子应用进行挂载'" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>

import { ref, computed, watch, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Box } from '@element-plus/icons-vue'

// 离线安全的微前端主壳：扫描 src/micro-apps/*/index.js，
// 每个子应用导出 { name, title?, mount(container, ctx), unmount(ctx) } 契约，
// 主壳负责挂载/卸载调度。零外部依赖（不引入 Module Federation），构建即含。
const modules = import.meta.glob('../../micro-apps/*/index.js', { eager: false })

const route = useRoute()
const router = useRouter()
const mountContainer = ref(null)
const activeAppName = ref('')
let mountedApp = null // 当前已挂载的子应用模块（用于调用 unmount）

const apps = Object.entries(modules).map(([path, loader]) => {
  // 路径形如 ../../micro-apps/hello/index.js → 提取目录名 hello
  const name = path.split('/').at(-2)
  return { name, loader, title: name }
})

const activeTitle = computed(() => apps.find((app) => app.name === activeAppName.value)?.title || '')

async function selectApp(name) {
  if (name === activeAppName.value) return
  await mountApp(name)
}

async function mountApp(name) {
  const app = apps.find((a) => a.name === name)
  if (!app) {
    ElMessage.warning(`子应用不存在：${name}`)
    return
  }
  // 先卸载当前子应用，保证容器独占与生命周期对称
  await unmountCurrent()
  try {
    const mod = await app.loader()
    const definition = mod.default || mod
    if (typeof definition?.mount !== 'function') {
      ElMessage.error(`子应用「${name}」缺少 mount 契约，无法挂载`)
      return
    }
    mountedApp = definition
    activeAppName.value = name
    // 同步 URL query，刷新/分享可恢复挂载状态
    router.replace({ path: route.path, query: { app: name } })
    // 先让 v-if 的空态占位刷新移除，再挂载子应用 DOM，避免同容器冲突
    await nextTick()
    await definition.mount(mountContainer.value, { locale: navigator.language || 'zh-CN' })
  } catch (e) {
    ElMessage.error(`子应用挂载失败：${e?.message || e}`)
    mountedApp = null
    activeAppName.value = ''
  }
}

async function unmountCurrent() {
  if (!mountedApp) return
  const app = mountedApp
  mountedApp = null
  activeAppName.value = ''
  try {
    if (typeof app.unmount === 'function') {
      await app.unmount()
    }
  } catch {
    // 卸载失败不阻断下一个子应用挂载
  } finally {
    // 主壳兜底：无论子应用是否自行清理，都清空容器避免遗留 DOM
    if (mountContainer.value) mountContainer.value.innerHTML = ''
  }
}

// 深层监听挂载容器变化无必要；query ?app= 恢复在 onMounted 处理
onMounted(async () => {
  const fromQuery = typeof route.query.app === 'string' ? route.query.app : ''
  if (fromQuery && apps.some((a) => a.name === fromQuery)) {
    await mountApp(fromQuery)
  }
})

onBeforeUnmount(unmountCurrent)
</script>

<style scoped>
.host-alert {
  margin-bottom: 14px;
}
.host-layout {
  display: flex;
  gap: 14px;
  align-items: stretch;
}
.app-list-card {
  width: 280px;
  flex-shrink: 0;
}
.app-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.app-item:hover {
  background: var(--el-fill-color-light);
}
.app-item.active {
  background: var(--el-color-primary-light-9);
}
.app-name {
  font-weight: 500;
}
.app-code {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.mount-card {
  flex: 1;
  min-width: 0;
}
.mount-container {
  min-height: 420px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
}
</style>
