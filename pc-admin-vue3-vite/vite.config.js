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
      }
    }
  }
})
