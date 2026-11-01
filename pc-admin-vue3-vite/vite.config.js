import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 标准配置：Vue 插件 + @ 别名 + 开发服务器代理（USE_MOCK=false 时转发 /api 到后端）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      // 对接真实后端时（USE_MOCK=false），将 /api 代理到本地 8080
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      // 实时 WebSocket：代理 /ws 到本地 8080（ws:true 启用协议升级）
      '/ws': {
        target: 'http://127.0.0.1:8080',
        ws: true,
        changeOrigin: true
      },
      // 健康探针（k8s 风格根路径 /livez、/readyz，permitAll，无 /api 前缀）：
      // 前端健康页直接消费这两个端点，dev 下需与 /api、/ws 一同代理到本地 8080；
      // 生产由同源后端直接处理根路径探针。
      '/livez': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/readyz': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      // OAuth2 协议端点（授权码 + PKCE + consent，#900）：后端路径为 /oauth2/authorize、
      // /oauth2/consent、/oauth2/token 等，同样无 /api 前缀，dev 下须代理到本地 8080
      // 方能打通授权流程（/oauth2/token 由开放平台客户端自测 issueClientToken 使用）。
      '/oauth2': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  // 生产构建：按依赖切分长缓存 chunk，降低首屏体积、提升命中率。
  // element-plus / echarts / vue 生态 / axios 各自独立分包，业务代码变更不使其失效。
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      // 降低 Rollup 并行文件操作数：本环境 brokered-fs 在 element-plus/echarts 海量模块
      // 的高并发读取下会出现 IPC slot 超时，限制并发可规避（不影响产物正确性）。
      maxParallelFileOps: 4,
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('axios')) return 'axios'
          if (
            id.includes('/vue/') ||
            id.includes('/vue-router/') ||
            id.includes('/@vue/') ||
            id.includes('/@vueuse/') ||
            id.includes('/pinia/')
          ) return 'vue-vendor'
          return 'vendor'
        }
      }
    }
  }
})
