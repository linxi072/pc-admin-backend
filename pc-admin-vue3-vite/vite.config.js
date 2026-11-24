import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 标准配置：Vue 插件 + @ 别名 + 开发服务器代理（转发 /api 等前缀到后端/网关）。

export default defineConfig(({ mode }) => {
  // 代理目标可配置（方向 B4）：默认直连 scaffold-admin:8080；
  // 接入轻量网关时只需在 .env.development 设 VITE_BACKEND_TARGET=http://127.0.0.1:<网关端口>，
  // 前端请求路径（/api、/ws、/oauth2…）保持不变，实现「直连 ↔ 网关」双挂过渡。
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const backendTarget = (env.VITE_BACKEND_TARGET || 'http://127.0.0.1:8080').replace(/\/+$/, '')

  return {
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
        // 对接真实后端时，将 /api 代理到 backendTarget（默认本地 8080，可切网关）
        '/api': {
          target: backendTarget,
          changeOrigin: true
        },
        // 实时 WebSocket：代理 /ws（ws:true 启用协议升级）
        '/ws': {
          target: backendTarget,
          ws: true,
          changeOrigin: true
        },
        // 健康探针（k8s 风格根路径 /livez、/readyz，permitAll，无 /api 前缀）：
        // 前端健康页直接消费这两个端点，dev 下需与 /api、/ws 一同代理；
        // 生产由同源后端/网关直接处理根路径探针。
        '/livez': {
          target: backendTarget,
          changeOrigin: true
        },
        '/readyz': {
          target: backendTarget,
          changeOrigin: true
        },
        // OAuth2 协议端点（授权码 + PKCE + consent，#900）：/oauth2/authorize、
        // /oauth2/consent、/oauth2/token 等，无 /api 前缀，dev 下须代理以打通授权流程。
        '/oauth2': {
          target: backendTarget,
          changeOrigin: true
        },
        // OpenAPI 文档端点（springdoc 默认 /v3/api-docs，无 /api 前缀）：文档中心页直接消费。
        '/v3': {
          target: backendTarget,
          changeOrigin: true
        }
      }
    },
    // 生产构建：按依赖切分长缓存 chunk，降低首屏体积、提升命中率。
    // element-plus / echarts / vue 生态 / axios 各自独立分包，业务代码变更不使其失效。
    build: {
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        // 降低 Rollup 并行文件操作数：本环境 brokered-fs 在海量模块高并发读取下会
        // IPC slot 超时，限制并发可规避（不影响产物正确性）。
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
  }
})
