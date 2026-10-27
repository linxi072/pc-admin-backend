import { request } from './http'

// 低代码表单（V47 spike + V53 正式立项 / M22）：表单定义以 JSON Schema 描述，前端零依赖渲染；
// 提交值经后端按 schema 校验后落库到独立 form_submission 表（FORM_SUBMISSION 绑定目标）。
// 后端 base-path：/api/system/forms
// 权限：读面 system:form-definition:read；提交 system:form-submission:create；提交记录查询 system:form-submission:read

/** 表单定义列表（含停用，供前端选择器）。 */
export const listForms = () =>
  request({ method: 'GET', url: '/api/system/forms/list' })

/** 按编码取表单 Schema（渲染面入口，仅返回启用中定义）。 */
export const getFormSchema = (code) =>
  request({ method: 'GET', url: `/api/system/forms/schema/${code}` })

/** 提交表单（M22 正式立项：落库 FORM_SUBMISSION 目标）。values 为字段键 → 值的对象。 */
export const submitForm = (code, values) =>
  request({ method: 'POST', url: `/api/system/forms/${code}/submit`, data: values })

/** 表单提交记录分页查询。params: { page, size }；返回 PageResult { page, size, total, records }。 */
export const listSubmissions = (code, params = {}) =>
  request({ method: 'GET', url: `/api/system/forms/${code}/submissions`, params })

// ---- 表单定义管理（M22 深化）：后端 base-path /api/system/form-definitions ----
// 权限：列表/详情 system:form-definition:read；创建 system:form-definition:create；
// 更新 system:form-definition:update；删除 system:form-definition:delete（均自动登记 SUPER_ADMIN）。

/** 表单定义列表（含停用）。 */
export const listFormDefinitions = () =>
  request({ method: 'GET', url: '/api/system/form-definitions' })

/** 表单定义详情。 */
export const getFormDefinition = (id) =>
  request({ method: 'GET', url: `/api/system/form-definitions/${id}` })

/** 创建表单定义。payload: { formCode, formName, schemaJson, targetType, enabled, remark }。 */
export const createFormDefinition = (payload) =>
  request({ method: 'POST', url: '/api/system/form-definitions', data: payload })

/** 更新表单定义。payload 同创建。 */
export const updateFormDefinition = (id, payload) =>
  request({ method: 'PUT', url: `/api/system/form-definitions/${id}`, data: payload })

/** 删除表单定义（逻辑删除）。 */
export const deleteFormDefinition = (id) =>
  request({ method: 'DELETE', url: `/api/system/form-definitions/${id}` })
