/* eslint-env node */
// pc-admin-vue3-vite ESLint 配置（Vue3 + Vite 前端规范基线）。
// 与 Prettier 分工：ESLint 负责代码质量/潜在错误，Prettier 负责格式化。
// 依赖（devDependencies）需 npm install 后生效；CI/本地可用 `npm run lint` / `npm run format`。
module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    ecmaFeatures: { jsx: false }
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended'
  ],
  plugins: ['vue'],
  rules: {
    // 风格类交由 Prettier 处理，ESLint 仅保留质量/正确性规则
    'vue/multi-word-component-names': 'off',
    'vue/no-v-html': 'warn',
    'no-console': 'warn',
    'no-debugger': 'warn',
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }]
  },
  ignorePatterns: ['dist', 'node_modules', '*.d.ts']
}
