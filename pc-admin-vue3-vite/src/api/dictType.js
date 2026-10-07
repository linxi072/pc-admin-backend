import { request } from './http'

// GET /api/system/dict-types -> List<DictTypeVO>
export const listDictTypes = () =>
  request({ method: 'GET', url: '/api/system/dict-types' })

// POST /api/system/dict-types -> Long (CreateDictTypeRequest)
export const createDictType = (data) =>
  request({ method: 'POST', url: '/api/system/dict-types', data })

// PUT /api/system/dict-types/{id} -> Void (UpdateDictTypeRequest)
export const updateDictType = (id, data) =>
  request({ method: 'PUT', url: `/api/system/dict-types/${id}`, data })

// DELETE /api/system/dict-types/{id}
export const deleteDictType = (id) =>
  request({ method: 'DELETE', url: `/api/system/dict-types/${id}` })
