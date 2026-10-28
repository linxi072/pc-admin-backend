// 轻量国际化引擎（零依赖，离线可构建）。
//
// 设计要点：
// 1. 不引入 vue-i18n（避免离线环境 npm install 失败），仅用 Vue 响应式 + 消息目录。
// 2. 语言自动匹配：启动时依据浏览器 / 系统语言（navigator.languages）自动选择，
//    不提供手动切换。与后端对齐：后端 I18nConfig 用 SessionLocaleResolver + LocaleChangeInterceptor(?lang=)，
//    前端把自动选定的语言经 http 层 ?lang= 上报（见 src/api/http.js），
//    后端异常 / 业务错误消息即按该语言返回（GlobalExceptionHandler 已本地化）。
// 3. Element Plus 自带语言包（element-plus/es/locale/lang/*）已随依赖安装，
//    通过 <el-config-provider :locale="elLocale"> 绑定，使日期选择器 / 分页等组件跟随语言。

import { reactive, computed } from 'vue'
import messages from './messages'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const DEFAULT = 'zh-CN'

// 前端内部用 BCP-47（zh-CN / en-US）；上报后端时转 Spring Locale 形式（zh_CN / en_US）。
const BACKEND_LOCALE_MAP = { 'zh-CN': 'zh_CN', 'en-US': 'en_US' }
const EL_LOCALE_MAP = { 'zh-CN': zhCn, 'en-US': en }

/**
 * 自动匹配语言：优先遍历 navigator.languages（系统/浏览器偏好列表，从高到低），
 * 命中受支持语言即采用；均未命中回退默认 zh-CN。不读取 localStorage，不提供手动切换。
 */
function detectInitial() {
  const candidates =
    (typeof navigator !== 'undefined' && navigator.languages && navigator.languages.length)
      ? navigator.languages
      : (typeof navigator !== 'undefined' && navigator.language ? [navigator.language] : [])
  for (const raw of candidates) {
    const primary = String(raw || '').toLowerCase().split(/[-_]/)[0]
    if (primary === 'zh') return 'zh-CN'
    if (primary === 'en') return 'en-US'
  }
  return DEFAULT
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

/**
 * 按点分嵌套键从消息目录取值：'login.title' -> catalog.login.title。
 * 扁平键（catalog[key] 直接命中）亦兼容。
 */
function resolveMessage(catalog, key) {
  if (!catalog || key == null) return undefined
  if (catalog[key] !== undefined) return catalog[key]
  return key.split('.').reduce(
    (acc, part) => (acc != null && typeof acc === 'object' ? acc[part] : undefined),
    catalog
  )
}

/**
 * 翻译。键支持点分嵌套（'login.title' 对应 messages[locale].login.title）。
 * 解析顺序：当前语言目录 → 默认语言目录 → 原键（兜底）。
 * 仅当两种语言目录均无该键时才回退为键名本身（避免 UI 崩溃，也便于定位遗漏的翻译）。
 * 支持 {name} 占位符插值（params 为对象）。
 */
export function t(key, params) {
  let str = resolveMessage(messages[i18n.locale], key)
  if (str == null) str = resolveMessage(messages[DEFAULT], key)
  if (str == null) str = key
  if (params && typeof str === 'string') {
    str = str.replace(/\{(\w+)\}/g, (_, name) =>
      params[name] != null ? String(params[name]) : `{${name}}`
    )
  }
  return str
}

/** Element Plus 语言包（随前端自动选定的 i18n.locale 绑定）。 */
export const elLocale = computed(() => EL_LOCALE_MAP[i18n.locale] || zhCn)

export default { i18n, t, backendLocale, elLocale }
