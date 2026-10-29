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
      }
    }
  }
})
