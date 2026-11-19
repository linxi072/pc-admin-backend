import { describe, it, expect } from 'vitest'
import {
  FIELD_TYPES,
  defaultValueFor,
  evalVisibility,
  visibleFields,
  buildRules,
  validateForm
} from '../formSchema'

describe('defaultValueFor', () => {
  it('SWITCH 默认 false，选择/数字默认 undefined，其余空串', () => {
    expect(defaultValueFor('SWITCH')).toBe(false)
    expect(defaultValueFor('SELECT')).toBeUndefined()
    expect(defaultValueFor('RADIO')).toBeUndefined()
    expect(defaultValueFor('NUMBER')).toBeUndefined()
    expect(defaultValueFor('INPUT')).toBe('')
    expect(defaultValueFor('DATE')).toBe('')
  })
})

describe('evalVisibility / visibleFields', () => {
  const schema = {
    fields: [
      { key: 'a', label: 'A' }, // 无条件：恒可见
      { key: 'b', label: 'B', visibleWhen: { field: 'a', op: 'eq', value: 'x' } },
      { key: 'c', label: 'C', visibleWhen: { field: 'a', op: 'in', value: ['x', 'y'] } },
      { key: 'd', label: 'D', visibleWhen: { field: 'a', op: 'truthy' } },
      { key: 'e', label: 'E', visibleWhen: { field: 'missing', op: 'eq', value: 1 } }
    ]
  }

  it('eq/in/truthy 操作符按依赖字段求值', () => {
    const model = { a: 'x' }
    expect(visibleFields(schema, model).map((f) => f.key)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('truthy 操作符：非空字符串为真值，字段保持可见', () => {
    const model = { a: 'z' }
    expect(visibleFields(schema, model).map((f) => f.key)).toEqual(['a', 'd'])
  })

  it('依赖字段缺失时 visibleWhen 求值为不可见', () => {
    expect(visibleFields(schema, {}).map((f) => f.key)).toEqual(['a'])
  })
})

describe('buildRules', () => {
  const fields = [
    { key: 'name', label: '名称', type: 'INPUT', required: true, minLength: 2, maxLength: 5 },
    { key: 'age', label: '年龄', type: 'NUMBER', min: 1, max: 120 },
    { key: 'code', label: '编码', type: 'INPUT', pattern: '^[a-z]+$' },
    { key: 'on', label: '开关', type: 'SWITCH', required: true }
  ]

  const runRules = (rules, value) => new Promise((resolve) => {
    const rule = rules[0]
    rule.validator(rule, value, (err) => resolve(err ? err.message : null))
  })

  it('必填字段生成 required 规则且消息内联中文', () => {
    expect(buildRules(fields).name[0]).toMatchObject({ required: true, message: '名称不能为空' })
    expect(buildRules(fields).on[0].message).toBe('请开启开关')
  })

  it('长度校验器：过短/过长分别报错，合法通过', async () => {
    const rules = buildRules(fields)
    const lenRule = rules.name.find((r) => r.validator)
    expect(await runRules([lenRule], 'a')).toContain('至少 2 个字符')
    expect(await runRules([lenRule], 'abc')).toBeNull()
    expect(await runRules([lenRule], 'abcdef')).toContain('不超过 5 个字符')
  })

  it('数字范围校验器：NaN/越界报错，合法通过', async () => {
    const ageRule = buildRules(fields).age[0]
    expect(await runRules([ageRule], 'abc')).toContain('不能为空')
    expect(await runRules([ageRule], '0')).toContain('不能小于 1')
    expect(await runRules([ageRule], '121')).toContain('不能大于 120')
    expect(await runRules([ageRule], '30')).toBeNull()
  })

  it('非法正则不应抛异常而是生成规则缺失', () => {
    const bad = [{ key: 'x', label: 'X', type: 'INPUT', pattern: '([a-z' }]
    expect(buildRules(bad).x).toBeUndefined()
  })
})

describe('validateForm', () => {
  const schema = {
    fields: [
      { key: 'name', label: '名称', type: 'INPUT', required: true, minLength: 2 },
      { key: 'qty', label: '数量', type: 'NUMBER', min: 1 },
      { key: 'extra', label: '附加', type: 'INPUT', required: true,
        visibleWhen: { field: 'name', op: 'eq', value: 'show' } }
    ]
  }

  it('全部合法时 valid=true 且无错误', () => {
    const r = validateForm({ name: '订单', qty: 3 }, schema)
    expect(r.valid).toBe(true)
    expect(r.errors).toEqual({})
  })

  it('必填/长度/范围错误分别生成中文消息', () => {
    const r = validateForm({ name: 'A', qty: 0 }, schema)
    expect(r.valid).toBe(false)
    expect(r.errors.name).toBe('名称至少 2 个字符')
    expect(r.errors.qty).toBe('数量不能小于 1')
  })

  it('不可见字段不参与校验', () => {
    const r = validateForm({ name: 'hidden', qty: 1 }, schema)
    expect(r.errors.extra).toBeUndefined()
    expect(r.valid).toBe(true)
  })

  it('非 required 字段空值跳过范围校验（不报错）', () => {
    const r = validateForm({ name: '', qty: '' }, schema)
    expect(r.errors.name).toBe('名称不能为空')
    expect(r.errors.qty).toBeUndefined()
  })
})

describe('FIELD_TYPES', () => {
  it('应覆盖全部 7 种字段类型', () => {
    expect(FIELD_TYPES).toEqual(['INPUT', 'TEXTAREA', 'NUMBER', 'SELECT', 'RADIO', 'SWITCH', 'DATE'])
  })
})
