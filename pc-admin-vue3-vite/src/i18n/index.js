// 轻量国际化引擎（零依赖，离线可构建）。
//
// 设计要点：
// 1. 不引入 vue-i18n（避免离线环境 npm install 失败），仅用 Vue 响应式 + 消息目录。
// 2. 与后端对齐：后端 I18nConfig 用 SessionLocaleResolver + LocaleChangeInterceptor(?lang=)，
//    默认 locale=zh_CN。前端把当前语言经 http 层 ?lang= 上报（见 src/api/http.js），
//    后端异常 / 业务错误消息即按该语言返回（GlobalExceptionHandler 已本地化）。
// 3. Element Plus 自带语言包（element-plus/es/locale/lang/*）已随依赖安装，
//    通过 <el-config-provider :locale="elLocale"> 绑定，使日期选择器 / 分页等组件跟随切换。

import { reactive, computed } from 'vue'
import messages from './messages'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const STORAGE_KEY = 'pc_admin_locale'
const SUPPORTED = ['zh-CN', 'en-US']
const DEFAULT = 'zh-CN'

// 前端内部用 BCP-47（zh-CN / en-US）；上报后端时转 Spring Locale 形式（zh_CN / en_US）。
const BACKEND_LOCALE_MAP = { 'zh-CN': 'zh_CN', 'en-US': 'en_US' }
const EL_LOCALE_MAP = { 'zh-CN': zhCn, 'en-US': en }

function detectInitial() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved && SUPPORTED.includes(saved)) return saved
  } catch (e) {
    // localStorage 不可用时回退到浏览器语言
  }
  const nav = (typeof navigator !== 'undefined' && navigator.language) || DEFAULT
  return nav.toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN'
}

export const i18n = reactive({ locale: detectInitial() })

// 初始化 <html lang>，便于无障碍 / SEO
if (typeof document !== 'undefined') {
  document.documentElement.lang = i18n.locale
}

/** 当前语言对应的后端 Locale 字符串（zh_CN / en_US）。 */
export function backendLocale() {
  return BACKEND_LOCALE_MAP[i18n.locale] || 'zh_CN'
}

/** 切换语言：更新响应式状态、持久化、同步 <html lang>。 */
export function setLocale(loc) {
  if (!SUPPORTED.includes(loc)) return
  i18n.locale = loc
  try {
    localStorage.setItem(STORAGE_KEY, loc)
  } catch (e) {
    // 忽略持久化失败（隐私模式等），内存态仍生效
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = loc
  }
}

/**
 * 翻译。缺失键回退到默认语言，再缺失则返回原键（绝不抛错，保证 UI 不崩）。
 * 支持 {name} 占位符插值（params 为对象）。
 * 函数体内读取 i18n.locale（响应式），因此在模板中调用会随语言切换自动重渲染。
 */
export function t(key, params) {
  const catalog = messages[i18n.locale] || messages[DEFAULT]
  let str = catalog ? catalog[key] : undefined
  if (str == null) {
    str = messages[DEFAULT] ? messages[DEFAULT][key] : undefined
  }
  if (str == null) str = key
  if (params && typeof str === 'string') {
    str = str.replace(/\{(\w+)\}/g, (_, name) =>
      params[name] != null ? String(params[name]) : `{${name}}`
    )
  }
  return str
}

/** 语言选项（供下拉框渲染）。 */
export const availableLocales = SUPPORTED.map((code) => ({
  code,
  label: code === 'zh-CN' ? '简体中文' : 'English'
}))

/** Element Plus 语言包（响应式，随 i18n.locale 变化）。 */
export const elLocale = computed(() => EL_LOCALE_MAP[i18n.locale] || zhCn)

export default { i18n, t, setLocale, backendLocale, availableLocales, elLocale }
