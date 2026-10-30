import { request } from './http'

// GET /api/system/column-permissions/resources -> List<ColumnPermissionResourceView>
// ColumnPermissionResourceView: { resourceCode, resourceName, fields: [{field, label, strategy, sensitiveType}] }
// 资源清单由后端 ColumnPermissionResourceRegistry 启动时自动扫描 @ColumnPermission 得到，
// 新增受控资源无需改前端常量即出现在本页。
export const listColumnPermissionResources = () =>
  request({ method: 'GET', url: '/api/system/column-permissions/resources' })

// GET /api/system/roles/{id}/column-permissions -> Set<String>
// 返回该角色拥有明文可见权的字段权限码集合（形如 system:user:column:mobile）
export const listRoleColumnPermissions = (id) =>
  request({ method: 'GET', url: `/api/system/roles/${id}/column-permissions` })

// PUT /api/system/roles/{id}/column-permissions -> void（覆盖式保存）
// body: { codes: string[] }；codes 为空数组表示「全部字段脱敏/隐藏」
export const saveRoleColumnPermissions = (id, codes) =>
  request({ method: 'PUT', url: `/api/system/roles/${id}/column-permissions`, data: { codes } })
