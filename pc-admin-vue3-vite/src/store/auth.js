import { reactive } from 'vue'

const TOKEN_KEY = 'pc_admin_token'
const USER_KEY = 'pc_admin_user'

export const authState = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  username: localStorage.getItem(USER_KEY) || '',
  // 当前登录用户「按角色动态加载」的菜单树（来自 GET /api/system/menus/mine），
  // 驱动侧边栏渲染与路由守卫鉴权；非前端硬编码。
  menus: [],
  // 菜单加载状态：unloaded（未加载）/ loaded（成功）/ failed（失败）。
  // 路由守卫据此 fail-closed：加载失败时不得把「空菜单」当作「全部放行」。
  menusLoaded: 'unloaded'
})

export function isAuthenticated() {
  return !!authState.token
}

export function setAuth(token, username) {
  authState.token = token
  authState.username = username || ''
  authState.menus = []
  authState.menusLoaded = 'unloaded'
  localStorage.setItem(TOKEN_KEY, token)
  if (username) localStorage.setItem(USER_KEY, username)
}

export function clearAuth() {
  authState.token = ''
  authState.username = ''
  authState.menus = []
  authState.menusLoaded = 'unloaded'
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/**
 * 拉取当前用户的角色菜单树并写入 authState.menus。
 * 采用动态 import 避免与 api/http 形成静态循环依赖（http 内部又引用本 store）。
 *
 * @returns {Promise<Array>} 菜单树（可能为 [])
 */
export async function loadMenus() {
  try {
    const { myMenus } = await import('@/api/menu')
    const tree = await myMenus()
    authState.menus = Array.isArray(tree) ? tree : []
    authState.menusLoaded = 'loaded'
  } catch (e) {
    // 拉取失败必须记录为 failed，供路由守卫 fail-closed；
    // 不能把它当 loaded + 空菜单，否则受限账号可访问任意后台页面。
    authState.menus = []
    authState.menusLoaded = 'failed'
  }
  return authState.menus
}
