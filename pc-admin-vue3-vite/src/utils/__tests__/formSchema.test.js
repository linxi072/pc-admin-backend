// 低代码表单 Schema 纯函数单测（零依赖，node:test 运行）。
// 覆盖：默认值、条件可见性、element-plus 规则生成、离线整体校验。

import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  defaultValueFor,
  evalVisibility,
  visibleFields,
  buildRules,
  validateForm
} from '../formSchema.js'

// 简化 i18n：直接返回 key，便于断言消息包含预期片段
const t = (key, vars) => {
  if (vars && Object.keys(vars).length) return `${key}:${JSON.stringify(vars)}`
  return key
}

const sampleSchema = {
  version: 1,
  fields: [
    { key: 'name', label: '姓名', type: 'INPUT', required: true, minLength: 2, maxLength: 10 },
    { key: 'age', label: '年龄', type: 'NUMBER', min: 18, max: 60 },
    { key: 'code', label: '编码', type: 'INPUT', pattern: '^[A-Z]{3}$' },
    { key: 'country', label: '国家', type: 'INPUT' },
    { key: 'city', label: '城市', type: 'INPUT', required: true, visibleWhen: { field: 'country', op: 'eq', value: 'CN' } }
  ]
}

test('defaultValueFor：SWITCH 默认 false；SELECT/RADIO/NUMBER 默认 undefined；其余空串', () => {
  assert.equal(defaultValueFor('SWITCH'), false)
  assert.equal(defaultValueFor('SELECT'), undefined)
  assert.equal(defaultValueFor('RADIO'), undefined)
  assert.equal(defaultValueFor('NUMBER'), undefined)
  assert.equal(defaultValueFor('INPUT'), '')
  assert.equal(defaultValueFor('TEXTAREA'), '')
})

test('evalVisibility：无条件恒可见', () => {
  assert.equal(evalVisibility({ key: 'a', type: 'INPUT' }, {}), true)
  assert.equal(evalVisibility({ key: 'a', type: 'INPUT', visibleWhen: {} }, {}), true)
})

test('evalVisibility：eq / ne', () => {
  const f = { visibleWhen: { field: 'x', op: 'eq', value: '1' } }
  assert.equal(evalVisibility(f, { x: '1' }), true)
  assert.equal(evalVisibility(f, { x: '2' }), false)
  const f2 = { visibleWhen: { field: 'x', op: 'ne', value: '1' } }
  assert.equal(evalVisibility(f2, { x: '2' }), true)
  assert.equal(evalVisibility(f2, { x: '1' }), false)
})

test('evalVisibility：in（逗号分隔或数组）', () => {
  const f = { visibleWhen: { field: 'x', op: 'in', value: 'a,b,c' } }
  assert.equal(evalVisibility(f, { x: 'b' }), true)
  assert.equal(evalVisibility(f, { x: 'z' }), false)
  const f2 = { visibleWhen: { field: 'x', op: 'in', value: ['a', 'b'] } }
  assert.equal(evalVisibility(f2, { x: 'a' }), true)
})

test('evalVisibility：数值比较 gt/lt/gte/lte', () => {
  assert.equal(evalVisibility({ visibleWhen: { field: 'n', op: 'gt', value: 5 } }, { n: 6 }), true)
  assert.equal(evalVisibility({ visibleWhen: { field: 'n', op: 'gt', value: 5 } }, { n: 5 }), false)
  assert.equal(evalVisibility({ visibleWhen: { field: 'n', op: 'gte', value: 5 } }, { n: 5 }), true)
  assert.equal(evalVisibility({ visibleWhen: { field: 'n', op: 'lt', value: 5 } }, { n: 4 }), true)
  assert.equal(evalVisibility({ visibleWhen: { field: 'n', op: 'lte', value: 5 } }, { n: 5 }), true)
})

test('evalVisibility：truthy / falsy', () => {
  assert.equal(evalVisibility({ visibleWhen: { field: 'x', op: 'truthy' } }, { x: 'hi' }), true)
  assert.equal(evalVisibility({ visibleWhen: { field: 'x', op: 'truthy' } }, { x: '' }), false)
  assert.equal(evalVisibility({ visibleWhen: { field: 'x', op: 'falsy' } }, { x: '' }), true)
  assert.equal(evalVisibility({ visibleWhen: { field: 'x', op: 'falsy' } }, { x: 'hi' }), false)
})

test('visibleFields：条件字段按 model 过滤', () => {
  const all = visibleFields(sampleSchema, { country: 'CN' })
  assert.ok(all.some((f) => f.key === 'city'))
  const none = visibleFields(sampleSchema, { country: 'US' })
  assert.ok(!none.some((f) => f.key === 'city'))
})

test('buildRules：必填生成 required 规则', () => {
  const rules = buildRules([{ key: 'name', label: '姓名', type: 'INPUT', required: true }], t)
  assert.ok(rules.name)
  assert.ok(rules.name.some((r) => r.required === true))
})

test('buildRules：长度规则仅对 INPUT/TEXTAREA', () => {
  const rules = buildRules([{ key: 'name', label: '姓名', type: 'INPUT', minLength: 2, maxLength: 10 }], t)
  assert.ok(rules.name)
  const lenRule = rules.name.find((r) => typeof r.validator === 'function')
  assert.ok(lenRule)
  // 太短应回调错误
  let err = null
  lenRule.validator(null, 'a', (e) => { err = e })
  assert.ok(err instanceof Error)
  // 合法长度回调无错误
  let ok = null
  lenRule.validator(null, 'abc', (e) => { ok = e })
  assert.equal(ok, undefined)
})

test('buildRules：数字范围规则（NUMBER）', () => {
  const rules = buildRules([{ key: 'age', label: '年龄', type: 'NUMBER', min: 18, max: 60 }], t)
  const rangeRule = rules.age.find((r) => typeof r.validator === 'function')
  assert.ok(rangeRule)
  let low = null
  rangeRule.validator(null, 10, (e) => { low = e })
  assert.ok(low instanceof Error)
  let high = null
  rangeRule.validator(null, 99, (e) => { high = e })
  assert.ok(high instanceof Error)
  let good = null
  rangeRule.validator(null, 30, (e) => { good = e })
  assert.equal(good, undefined)
})

test('buildRules：正则规则（INPUT/TEXTAREA）', () => {
  const rules = buildRules([{ key: 'code', label: '编码', type: 'INPUT', pattern: '^[A-Z]{3}$' }], t)
  const patRule = rules.code.find((r) => typeof r.validator === 'function')
  assert.ok(patRule)
  let bad = null
  patRule.validator(null, 'abc', (e) => { bad = e })
  assert.ok(bad instanceof Error)
  let good = null
  patRule.validator(null, 'ABC', (e) => { good = e })
  assert.equal(good, undefined)
})

test('validateForm：整体校验通过', () => {
  const { valid, errors } = validateForm(
    { name: '张三', age: 30, code: 'ABC', country: 'US' },
    sampleSchema,
    t
  )
  assert.equal(valid, true)
  assert.deepEqual(errors, {})
})

test('validateForm：必填/长度/范围/正则/条件联动均报错', () => {
  const { valid, errors } = validateForm(
    { name: 'a', age: 5, code: 'xx', country: 'CN' },
    sampleSchema,
    t
  )
  assert.equal(valid, false)
  assert.ok(errors.name) // 长度不足
  assert.ok(errors.age) // 小于 min
  assert.ok(errors.code) // 正则不匹配
  // country=CN 时 city 条件可见且未填 → 报错
  assert.ok(errors.city)
})

test('validateForm：country 非 CN 时 city 不可见，不参与校验', () => {
  const { valid, errors } = validateForm(
    { name: '张三', age: 30, code: 'ABC', country: 'US' },
    sampleSchema,
    t
  )
  assert.equal(valid, true)
  assert.ok(!errors.city)
})
