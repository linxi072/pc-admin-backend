// 响应式断点 composable（零依赖，供布局与组件按视口自适应）。
//
// 设计要点：
// - 纯函数 resolveBreakpoint(width) 可被 node:test 直接覆盖（无 window 依赖），便于单测；
// - useBreakpoint() 在浏览器环境订阅 matchMedia('(max-width: 768px)')，SSR/无 window 时安全降级为桌面；
// - 仅监听移动断点（<768px）即可驱动「侧边栏→抽屉」切换，其余尺寸由 CSS 媒体查询负责。

import { ref, onMounted, onUnmounted } from 'vue'

/** 移动断点：<= 该宽度视为移动端（与 Topbar 现有 @media max-width:768px 对齐）。 */
export const MOBILE_MAX_WIDTH = 768

/**
 * 按视口宽度解析断点档位（纯函数，零副作用，可单测）。
 * @param {number} width 视口宽度（px）
 * @returns {'mobile'|'tablet'|'desktop'}
 */
export function resolveBreakpoint(width) {
  if (typeof width !== 'number' || Number.isNaN(width)) return 'desktop'
  if (width <= MOBILE_MAX_WIDTH) return 'mobile'
  if (width <= 1024) return 'tablet'
  return 'desktop'
}

/**
 * 判断给定宽度是否处于移动端（纯函数，便于单测与组合使用）。
 * @param {number} width
 * @returns {boolean}
 */
export function isMobileWidth(width) {
  return resolveBreakpoint(width) === 'mobile'
}

/**
 * 响应式断点 composable：返回响应式 isMobile / current 及档位。
 * @returns {{ isMobile: import('vue').Ref<boolean>, current: import('vue').Ref<string>, stop: () => void }}
 */
export function useBreakpoint() {
  const isMobile = ref(false)
  const current = ref('desktop')
  let mql = null
  let handler = null

  function apply(width) {
    const bp = resolveBreakpoint(width)
    current.value = bp
    isMobile.value = bp === 'mobile'
  }

  onMounted(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      // 非浏览器环境：保持默认桌面档，避免报错
      return
    }
    mql = window.matchMedia(`(max-width: ${MOBILE_MAX_WIDTH}px)`)
    handler = (e) => apply(e.matches ? 0 : (typeof window.innerWidth === 'number' ? window.innerWidth : MOBILE_MAX_WIDTH + 1))
    apply(typeof window.innerWidth === 'number' ? window.innerWidth : MOBILE_MAX_WIDTH + 1)
    if (typeof mql.addEventListener === 'function') {
      mql.addEventListener('change', handler)
    } else if (typeof mql.addListener === 'function') {
      // 旧版 Safari 兜底
      mql.addListener(handler)
    }
  })

  function stop() {
    if (mql && handler) {
      if (typeof mql.removeEventListener === 'function') mql.removeEventListener('change', handler)
      else if (typeof mql.removeListener === 'function') mql.removeListener(handler)
    }
    mql = null
    handler = null
  }

  onUnmounted(stop)

  return { isMobile, current, stop }
}
