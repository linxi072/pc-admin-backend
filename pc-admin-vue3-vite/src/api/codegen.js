import { request, requestRaw } from './http'

// GET /api/codegen/tables -> string[]（库内业务表清单）
export const codegenTables = () =>
  request({ method: 'GET', url: '/api/codegen/tables' })

// GET /api/codegen/tables/{tableName} -> TableMetaVO
// { tableName, className, comment, columns: [{ columnName, dataType, javaType, javaField, comment, primaryKey, nullable, autoIncrement }] }
export const codegenTableMeta = (tableName) =>
  request({ method: 'GET', url: '/api/codegen/tables/' + encodeURIComponent(tableName) })

// POST /api/codegen/preview -> GeneratedFileVO[]
// { path, language: 'JAVA'|'VUE'|'JS'|'SQL', content }
// option: { tableName, moduleName, functionName, className, author, withFrontend }
export const codegenPreview = (option) =>
  request({ method: 'POST', url: '/api/codegen/preview', data: option })

// POST /api/codegen/download -> zip（原始响应，走 requestRaw  blob 通道）
export const codegenDownload = (option) =>
  requestRaw({
    method: 'POST',
    url: '/api/codegen/download',
    data: option,
    responseType: 'blob'
  })
