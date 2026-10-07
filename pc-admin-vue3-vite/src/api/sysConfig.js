import { request } from './http'

// GET /api/system/configs -> List<ConfigVO>
export const listConfigs = () =>
  request({ method: 'GET', url: '/api/system/configs' })

// GET /api/system/configs/key/{key} -> ConfigVO（动态读取，后端带缓存）
export const getConfigByKey = (key) =>
  request({ method: 'GET', url: `/api/system/configs/key/${key}` })

// POST /api/system/configs -> Long (CreateConfigRequest)
export const createConfig = (data) =>
  request({ method: 'POST', url: '/api/system/configs', data })

// PUT /api/system/configs/{id} -> Void (UpdateConfigRequest)
export const updateConfig = (id, data) =>
  request({ method: 'PUT', url: `/api/system/configs/${id}`, data })

// DELETE /api/system/configs/{id}
export const deleteConfig = (id) =>
  request({ method: 'DELETE', url: `/api/system/configs/${id}` })
