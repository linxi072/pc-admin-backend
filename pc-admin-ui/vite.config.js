import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 组件库 lib mode 构建：外部化所有 peer 依赖，产出 ESM + CJS 双格式 + 独立 CSS。
// 与 pc-admin-vue3-vite 同 Vite 5 / plugin-vue 5，零新增依赖。
export default defineConfig({
  plugins: [vue()],
  build: {
    lib: {
      entry: fileURLToPath(new URL('./src/index.js', import.meta.url)),
      name: 'AcmeAdminUI',
      formats: ['es', 'cjs'],
      fileName: (format) => (format === 'es' ? 'admin-ui.js' : 'admin-ui.cjs')
    },
    rollupOptions: {
      external: ['vue', 'element-plus', '@element-plus/icons-vue', 'echarts', 'echarts/core',
        'echarts/charts', 'echarts/components', 'echarts/renderers'],
      output: {
        globals: {
          vue: 'Vue',
          'element-plus': 'ElementPlus',
          '@element-plus/icons-vue': 'ElementPlusIconsVue',
          echarts: 'echarts'
        }
      }
    },
    cssCodeSplit: false
  },
  test: {
    include: ['src/**/*.test.js'],
    environment: 'node'
  }
})
