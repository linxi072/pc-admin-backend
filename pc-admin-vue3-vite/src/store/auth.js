import { reactive } from 'vue'

const TOKEN_KEY = 'pc_admin_token'
const USER_KEY = 'pc_admin_user'

export const authState = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  username: localStorage.getItem(USER_KEY) || '',
  // 当前登录用户「按角色动态加载」的菜单树（来自 GET /api/system/menus/mine），
  // 驱动侧边栏渲染与路由守卫鉴权；非前端硬编码。
  menus: []
})

export function isAuthenticated() {
  return !!authState.token
}

export function setAuth(token, username) {
  authState.token = token
  authState.username = username || ''
  authState.menus = []
  localStorage.setItem(TOKEN_KEY, token)
  if (username) localStorage.setItem(USER_KEY, username)
}

export function clearAuth() {
  authState.token = ''
  authState.username = ''
  authState.menus = []
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
  } catch (e) {
    // 拉取失败（如未登录 / 无菜单权限）降级为空菜单，不阻塞页面
    authState.menus = []
  }
  return authState.menus
}
