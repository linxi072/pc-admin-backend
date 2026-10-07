import { request } from './http'

// GET /api/system/api-resources -> List<ApiResourceView>
export const listApiResources = () =>
  request({ method: 'GET', url: '/api/system/api-resources' })

// POST /api/system/api-resources -> Long (CreateApiResourceRequest: resourceName, permissionCode, httpMethod, pathPattern, authMode, riskLevel)
export const createApiResource = (data) =>
  request({ method: 'POST', url: '/api/system/api-resources', data })

// DELETE /api/system/api-resources/{id}
export const deleteApiResource = (id) =>
  request({ method: 'DELETE', url: `/api/system/api-resources/${id}` })

// PUT /api/system/api-resources/{id} -> Void (UpdateApiResourceRequest: resourceName, permissionCode, httpMethod, pathPattern, authMode, status, riskLevel)
export const updateApiResource = (id, data) =>
  request({ method: 'PUT', url: `/api/system/api-resources/${id}`, data })

// GET /api/system/api-resources/scan -> ApiResourceScanResult（扫描预览，不写库）
// 结构: { newResources, changedResources, orphanedResources, unchangedCount, total }
export const scanApiResources = () =>
  request({ method: 'GET', url: '/api/system/api-resources/scan' })

// POST /api/system/api-resources/scan/sync -> ApiResourceSyncResult（写库，幂等）
// 结构: { insertedCount, updatedCount, unchangedCount, skippedCount, orphanCount }
export const syncApiResources = () =>
  request({ method: 'POST', url: '/api/system/api-resources/scan/sync' })
