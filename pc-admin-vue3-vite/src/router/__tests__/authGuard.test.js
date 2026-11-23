import { describe, expect, it } from 'vitest'
import { authorizeNavigation, collectAuthorizedPaths, DETAIL_PARENT_MENU } from '../authGuard.js'

// 路由鉴权纯函数回归：防止 menus 为空放行（fail-open）与详情页越权回潮。

const to = (overrides = {}) => ({
  path: '/system/users',
  name: 'users',
  meta: {},
  matched: [],
  fullPath: '/system/users',
  ...overrides
})

const menus = [
  { routePath: '/system/users', children: [] },
  { routePath: '/workflow/definition', children: [{ routePath: '/workflow/definition/sub' }] }
]

describe('authorizeNavigation - 认证与公开路由', () => {
  it('未认证访问受保护页 → 重定向登录并带 redirect', () => {
    expect(authorizeNavigation({ authenticated: false, menusLoaded: 'unloaded', menus: [], to: to() }))
      .toEqual({ name: 'login', query: { redirect: '/system/users' } })
  })

  it('已认证访问登录页 → 重定向工作台', () => {
    expect(authorizeNavigation({ authenticated: true, menusLoaded: 'loaded', menus,
      to: to({ path: '/login', name: 'login', meta: { public: true } }) }))
      .toEqual({ name: 'dashboard' })
  })

  it('未认证访问公开页 → 放行', () => {
    expect(authorizeNavigation({ authenticated: false, menusLoaded: 'unloaded', menus: [],
      to: to({ meta: { public: true } }) })).toBe(true)
  })

  it('工作台与 OAuth2 同意页对已认证用户放行', () => {
    const ctx = { authenticated: true, menusLoaded: 'failed', menus: [] }
    expect(authorizeNavigation({ ...ctx, to: to({ path: '/dashboard' }) })).toBe(true)
    expect(authorizeNavigation({ ...ctx, to: to({ path: '/oauth2/consent' }) })).toBe(true)
  })
})

describe('authorizeNavigation - fail-closed 防回归', () => {
  it('菜单加载失败 → 拦截所有受保护页（不再全放行）', () => {
    const decision = authorizeNavigation({ authenticated: true, menusLoaded: 'failed', menus: [], to: to() })
    expect(decision).toEqual({ name: 'dashboard', reason: 'menus-unavailable' })
  })

  it('菜单未加载 → 同样拦截（守卫会 await 加载后重试）', () => {
    const decision = authorizeNavigation({ authenticated: true, menusLoaded: 'unloaded', menus: [], to: to() })
    expect(decision.reason).toBe('menus-unavailable')
  })

  it('菜单已加载但为空 → 同样不放行任意后台页', () => {
    const decision = authorizeNavigation({ authenticated: true, menusLoaded: 'loaded', menus: [], to: to() })
    expect(decision).toEqual({ name: 'dashboard' })
  })
})

describe('authorizeNavigation - 菜单范围与详情页白名单', () => {
  it('菜单中的普通页放行', () => {
    expect(authorizeNavigation({ authenticated: true, menusLoaded: 'loaded', menus, to: to() })).toBe(true)
  })

  it('不在菜单中的普通页拦截', () => {
    expect(authorizeNavigation({ authenticated: true, menusLoaded: 'loaded', menus,
      to: to({ path: '/system/secret', name: 'secret' }) })).toEqual({ name: 'dashboard' })
  })

  it('详情页白名单内且父级菜单已授权 → 放行', () => {
    expect(DETAIL_PARENT_MENU.designer).toBe('/workflow/definition')
    expect(authorizeNavigation({ authenticated: true, menusLoaded: 'loaded', menus,
      to: to({ path: '/workflow/designer/1', name: 'designer' }) })).toBe(true)
  })

  it('详情页白名单内但父级菜单未授权 → 拦截', () => {
    expect(authorizeNavigation({ authenticated: true, menusLoaded: 'loaded', menus: [],
      to: to({ path: '/workflow/designer/1', name: 'designer' }) })).toEqual({ name: 'dashboard' })
  })

  it('非白名单的带参数路由一律拦截（杜绝越权通道）', () => {
    expect(authorizeNavigation({ authenticated: true, menusLoaded: 'loaded', menus,
      to: to({ path: '/system/audit/1', matched: [{ path: '/system/audit/:id' }] }) }))
      .toEqual({ name: 'dashboard' })
  })
})

describe('collectAuthorizedPaths', () => {
  it('递归收集父子菜单路径', () => {
    const set = collectAuthorizedPaths(menus)
    expect([...set]).toEqual(['/system/users', '/workflow/definition', '/workflow/definition/sub'])
  })
})
