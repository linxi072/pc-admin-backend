import { request } from './http'

// GET /api/system/menus/tree -> List<MenuTreeVO>
export const menuTree = () =>
  request({ method: 'GET', url: '/api/system/menus/tree' })

// POST /api/system/menus -> Long (CreateMenuRequest: parentId, menuCode, menuName, menuType, routePath, componentPath, permissionCode, icon, visible, sortNo, status)
export const createMenu = (data) =>
  request({ method: 'POST', url: '/api/system/menus', data })

// DELETE /api/system/menus/{id}
export const deleteMenu = (id) =>
  request({ method: 'DELETE', url: `/api/system/menus/${id}` })

// PUT /api/system/menus/{id} -> Void (UpdateMenuRequest: 同上编辑字段，parentId 由后端忽略以保持层级)
export const updateMenu = (id, data) =>
  request({ method: 'PUT', url: `/api/system/menus/${id}`, data })
