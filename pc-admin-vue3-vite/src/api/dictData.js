import { request } from './http'

// GET /api/system/dict-data?dictType=xxx -> List<DictDataVO>
export const listDictData = (dictType) =>
  request({ method: 'GET', url: '/api/system/dict-data', params: { dictType } })

// POST /api/system/dict-data -> Long (CreateDictDataRequest)
export const createDictData = (data) =>
  request({ method: 'POST', url: '/api/system/dict-data', data })

// PUT /api/system/dict-data/{id} -> Void (UpdateDictDataRequest)
export const updateDictData = (id, data) =>
  request({ method: 'PUT', url: `/api/system/dict-data/${id}`, data })

// DELETE /api/system/dict-data/{id}
export const deleteDictData = (id) =>
  request({ method: 'DELETE', url: `/api/system/dict-data/${id}` })
