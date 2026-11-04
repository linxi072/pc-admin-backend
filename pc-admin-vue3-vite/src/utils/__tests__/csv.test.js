import { test } from 'node:test'
import assert from 'node:assert/strict'
import { escapeCsvField, toCsv, buildCsvBlobContent } from '../csv.js'

test('escapeCsvField: 普通文本与数字原样返回', () => {
  assert.equal(escapeCsvField('hello'), 'hello')
  assert.equal(escapeCsvField(123), '123')
  assert.equal(escapeCsvField(0), '0')
})

test('escapeCsvField: 空值统一返回空串', () => {
  assert.equal(escapeCsvField(null), '')
  assert.equal(escapeCsvField(undefined), '')
})

test('escapeCsvField: 含逗号/双引号/换行须包裹并按 RFC4180 翻倍引号', () => {
  assert.equal(escapeCsvField('a,b'), '"a,b"')
  assert.equal(escapeCsvField('he said "hi"'), '"he said ""hi"""')
  assert.equal(escapeCsvField('line1\nline2'), '"line1\nline2"')
})

test('toCsv: 表头与数据按列拼接，特殊字段自动转义', () => {
  const csv = toCsv(
    ['name', 'age'],
    [['张三', 20], ['Li, Lei', 21]]
  )
  assert.equal(csv, 'name,age\r\n张三,20\r\n"Li, Lei",21')
})

test('toCsv: 空数据仅含表头', () => {
  assert.equal(toCsv(['a', 'b'], []), 'a,b')
})

test('buildCsvBlobContent: 前置 UTF-8 BOM（﻿）', () => {
  assert.equal(buildCsvBlobContent('a,b'), '﻿a,b')
  assert.ok(buildCsvBlobContent('x').startsWith('﻿'))
})
