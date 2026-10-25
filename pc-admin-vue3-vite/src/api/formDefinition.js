import { request } from './http'

// 低代码表单试点（V47 / M22 spike）：表单定义以 JSON Schema 描述，前端零依赖渲染。
// 后端 base-path：/api/system/forms
// 权限：system:form-definition:read
// 范围收口：spike 只下发「Schema 契约 + 试点表单」，提交落库与绑定目标（BUSINESS_TABLE /
// FORM_SUBMISSION）为正式立项决策点，故无创建/提交接口；试点表单 contact-feedback 由 V47 迁移播种。

/** 表单定义列表（含停用，供前端选择器）。 */
export const listForms = () =>
  request({ method: 'GET', url: '/api/system/forms/list' })

/** 按编码取表单 Schema（渲染面入口，仅返回启用中定义）。 */
export const getFormSchema = (code) =>
  request({ method: 'GET', url: `/api/system/forms/schema/${code}` })
