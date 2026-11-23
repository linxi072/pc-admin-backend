// 路由鉴权决策纯函数：从 router.beforeEach 抽出，便于离线单测与行为回归。
// 不依赖 vue-router 实例，所有输入显式传入，输出为路由决策对象或 true。

export const DETAIL_PARENT_MENU = {
  designer: '/workflow/definition'
}

/** 收集菜单树中所有可见节点路由路径。 */
export function collectAuthorizedPaths(menus) {
  const set = new Set()
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if (n.routePath) set.add(n.routePath)
      walk(n.children)
    }
  }
  walk(menus)
  return set
}

/**
 * 计算导航决策。
 * @param {object} ctx
 * @param {boolean} ctx.authenticated 是否已认证
 * @param {string} ctx.menusLoaded 菜单加载状态：unloaded/loaded/failed
 * @param {Array} ctx.menus 菜单树
 * @param {object} ctx.to 目标路由（path/name/meta/matched/fullPath）
 * @returns {true|object} true=放行；对象=重定向目标
 */
export function authorizeNavigation({ authenticated, menusLoaded, menus, to }) {
  // 登录页对已登录用户不可达
  if (to.meta?.public) {
    if (to.name === 'login' && authenticated) return { name: 'dashboard' }
    return true
  }
  if (!authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // OAuth2 同意页：已认证用户直达，不依赖后台菜单树
  if (to.path === '/oauth2/consent') return true
  // 工作台是所有已认证用户的落地页
  if (to.path === '/' || to.path === '/dashboard') return true
  // fail-closed：菜单加载失败不放行任意受保护页面
  if (menusLoaded === 'failed' || menusLoaded === 'unloaded') {
    return { name: 'dashboard', reason: 'menus-unavailable' }
  }

  const authorized = collectAuthorizedPaths(menus)
  // 详情页白名单：仅父级列表页已授权者放行
  const parentMenu = DETAIL_PARENT_MENU[to.name]
  if (parentMenu) {
    return authorized.has(parentMenu) ? true : { name: 'dashboard' }
  }
  // 其他带参数路由或非菜单路径一律拦截
  const isDetail = (to.matched || []).some((r) => r.path.includes(':'))
  if (isDetail || !authorized.has(to.path)) {
    return { name: 'dashboard' }
  }
  return true
}
