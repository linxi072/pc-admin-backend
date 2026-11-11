// 低代码表单 Schema 纯函数工具（零依赖，可被 node:test 直接单测）。
//
// 与后端 FormSchemaRules（白名单字段类型与校验语义）保持单一事实来源：
// - 校验规则同时供 DynamicForm 渲染与 validateForm 离线校验；
// - 条件可见性 evalVisibility 同时决定字段是否渲染与是否参与校验；
// - 全部逻辑不依赖 Vue / DOM，便于前后端契约一致性测试。

/** 受支持的字段类型白名单（与后端 FormSchemaRules 对齐）。 */
export const FIELD_TYPES = ['INPUT', 'TEXTAREA', 'NUMBER', 'SELECT', 'RADIO', 'SWITCH', 'DATE']

/** 条件可见性支持的运算符。 */
export const VISIBILITY_OPS = ['eq', 'ne', 'in', 'gt', 'lt', 'gte', 'lte', 'truthy', 'falsy']

/**
 * 字段默认初始值（与 DynamicForm 既有规则一致）。
 * @param {string} type
 * @returns {*}
 */
export function defaultValueFor(type) {
  if (type === 'SWITCH') return false
  if (type === 'SELECT' || type === 'RADIO' || type === 'NUMBER') return undefined
  return ''
}

function isBlank(value, type) {
  if (type === 'SWITCH') return value !== true
  if (value === undefined || value === null) return true
  if (typeof value === 'string') return value.trim().length === 0
  if (Array.isArray(value)) return value.length === 0
  return false
}

function isTruthy(v) {
  if (v === undefined || v === null || v === '') return false
  if (v === false) return false
  if (v === 0) return false
  if (Array.isArray(v)) return v.length > 0
  if (typeof v === 'string') return v.trim().length > 0
  return true
}

/**
 * 评估单个字段的条件可见性。
 * 无条件（无 visibleWhen 或缺失依赖字段）时恒为可见。
 * @param {{visibleWhen?: {field?: string, op?: string, value?: *}}} field
 * @param {Object} model 当前表单数据
 * @returns {boolean}
 */
export function evalVisibility(field, model) {
  const cond = field && field.visibleWhen
  if (!cond || !cond.field) return true
  const actual = model ? model[cond.field] : undefined
  const expect = cond.value
  switch (cond.op) {
    case 'eq':
      return String(actual) === String(expect)
    case 'ne':
      return String(actual) !== String(expect)
    case 'in': {
      const arr = Array.isArray(expect)
        ? expect.map(String)
        : String(expect ?? '').split(',').map((s) => s.trim()).filter(Boolean)
      return arr.includes(String(actual))
    }
    case 'gt':
      return Number(actual) > Number(expect)
    case 'lt':
      return Number(actual) < Number(expect)
    case 'gte':
      return Number(actual) >= Number(expect)
    case 'lte':
      return Number(actual) <= Number(expect)
    case 'truthy':
      return isTruthy(actual)
    case 'falsy':
      return !isTruthy(actual)
    default:
      return true
  }
}

/**
 * 返回当前应渲染（可见）的字段列表。
 * @param {{fields?: Array}} schema
 * @param {Object} model
 * @returns {Array}
 */
export function visibleFields(schema, model) {
  const fields = schema && Array.isArray(schema.fields) ? schema.fields : []
  return fields.filter((f) => evalVisibility(f, model))
}

/**
 * 由字段清单生成 element-plus 校验规则（供 el-form :rules 使用）。
 * 支持：必填、长度（INPUT/TEXTAREA）、数字范围（NUMBER）、正则（INPUT/TEXTAREA）。
 * @param {Array} fields
 * @param {(k: string, vars?: Object) => string} t i18n 函数
 * @returns {Object} { [key]: Array<Rule> }
 */
export function buildRules(fields, t) {
  const out = {}
  for (const f of fields) {
    const list = []
    if (f.required) {
      const msg = f.type === 'SWITCH'
        ? `请开启${f.label}`
        : `${f.label}不能为空`
      list.push({ required: true, message: msg, trigger: f.type === 'SWITCH' ? 'change' : 'blur' })
    }
    const min = Number.isInteger(f.minLength) ? f.minLength : undefined
    const max = Number.isInteger(f.maxLength) ? f.maxLength : undefined
    if ((min !== undefined || max !== undefined) && (f.type === 'INPUT' || f.type === 'TEXTAREA')) {
      list.push({ validator: makeLengthValidator(f, min, max, t), trigger: 'blur' })
    }
    const nmin = typeof f.min === 'number' ? f.min : (f.min !== undefined ? Number(f.min) : undefined)
    const nmax = typeof f.max === 'number' ? f.max : (f.max !== undefined ? Number(f.max) : undefined)
    if ((nmin !== undefined || nmax !== undefined) && f.type === 'NUMBER') {
      list.push({ validator: makeNumberRangeValidator(f, nmin, nmax, t), trigger: 'blur' })
    }
    if (f.pattern && (f.type === 'INPUT' || f.type === 'TEXTAREA')) {
      let re = null
      try {
        re = new RegExp(f.pattern)
      } catch (e) {
        re = null
      }
      if (re) {
        list.push({
          validator: (rule, value, cb) => {
            if (value == null || value === '') return cb()
            if (re.test(String(value))) return cb()
            cb(new Error(`${f.label}格式不正确`))
          },
          trigger: 'blur'
        })
      }
    }
    if (list.length) out[f.key] = list
  }
  return out
}

function makeLengthValidator(f, min, max, t) {
  return (rule, value, cb) => {
    const len = value == null ? 0 : String(value).length
    if (min !== undefined && len < min) return cb(new Error(`${f.label}至少 ${min} 个字符`))
    if (max !== undefined && len > max) return cb(new Error(`${f.label}不超过 ${max} 个字符`))
    cb()
  }
}

function makeNumberRangeValidator(f, nmin, nmax, t) {
  return (rule, value, cb) => {
    if (value == null || value === '') return cb()
    const n = Number(value)
    if (Number.isNaN(n)) return cb(new Error(`${f.label}不能为空`))
    if (nmin !== undefined && n < nmin) return cb(new Error(`${f.label}不能小于 ${nmin}`))
    if (nmax !== undefined && n > nmax) return cb(new Error(`${f.label}不能大于 ${nmax}`))
    cb()
  }
}

/**
 * 离线校验整个 model（与 buildRules 语义一致，但返回结构化错误便于调用方精细处理）。
 * 不可见字段不参与校验。
 * @param {Object} model
 * @param {{fields?: Array}} schema
 * @param {(k: string, vars?: Object) => string} t
 * @returns {{valid: boolean, errors: Object}}
 */
export function validateForm(model, schema, t) {
  const errors = {}
  const fields = schema && Array.isArray(schema.fields) ? schema.fields : []
  for (const f of fields) {
    if (!evalVisibility(f, model)) continue
    const value = model ? model[f.key] : undefined
    const label = f.label || f.key
    if (f.required) {
      if (isBlank(value, f.type)) {
        errors[f.key] = `${label}不能为空`
        continue
      }
    }
    if (isBlank(value, f.type)) continue
    if (f.type === 'INPUT' || f.type === 'TEXTAREA') {
      const len = String(value).length
      if (Number.isInteger(f.minLength) && len < f.minLength) {
        errors[f.key] = `${label}至少 ${f.minLength} 个字符`
        continue
      }
      if (Number.isInteger(f.maxLength) && len > f.maxLength) {
        errors[f.key] = `${label}不超过 ${f.maxLength} 个字符`
        continue
      }
    }
    if (f.type === 'NUMBER') {
      const n = Number(value)
      if (!Number.isNaN(n)) {
        if (typeof f.min === 'number' && n < f.min) {
          errors[f.key] = `${label}不能小于 ${f.min}`
          continue
        }
        if (typeof f.max === 'number' && n > f.max) {
          errors[f.key] = `${label}不能大于 ${f.max}`
          continue
        }
      }
    }
    if ((f.type === 'INPUT' || f.type === 'TEXTAREA') && f.pattern) {
      let re = null
      try {
        re = new RegExp(f.pattern)
      } catch (e) {
        re = null
      }
      if (re && !re.test(String(value))) {
        errors[f.key] = `${label}格式不正确`
        continue
      }
    }
  }
  return { valid: Object.keys(errors).length === 0, errors }
}
