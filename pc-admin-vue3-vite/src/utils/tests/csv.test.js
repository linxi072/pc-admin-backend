import { describe, expect, it } from 'vitest'
import { buildCsvBlobContent, escapeCsvField, neutralizeFormula, toCsv } from '../csv'

describe('CSV 导出工具', () => {
  describe('RFC 4180 转义（回归）', () => {
    it('空值返回空串，普通值原样保留', () => {
      expect(escapeCsvField(null)).toBe('')
      expect(escapeCsvField(undefined)).toBe('')
      expect(escapeCsvField('普通文本')).toBe('普通文本')
      expect(escapeCsvField(0)).toBe('0')
    })

    it('含逗号/引号/换行的字段加引号并翻倍内部引号', () => {
      expect(escapeCsvField('a,b')).toBe('"a,b"')
      expect(escapeCsvField('a"b')).toBe('"a""b"')
      expect(escapeCsvField('a\r\nb')).toBe('"a\r\nb"')
    })
  })

  describe('公式注入防护（CWE-1236，与后端同口径）', () => {
    it('危险首字符前置单引号', () => {
      for (const prefix of ['=', '+', '-', '@', '\t', '\r']) {
        expect(neutralizeFormula(`${prefix}CMD`)).toBe(`'${prefix}CMD`)
      }
      for (const prefix of ['=', '+', '-', '@', '\t']) {
        expect(escapeCsvField(`${prefix}CMD`)).toBe(`'${prefix}CMD`)
      }
      // CR 同时触发 RFC 4180 引号包裹，中和后的单引号位于引号内仍是首字符。
      expect(escapeCsvField('\rCMD')).toBe('"\'\rCMD"')
    })

    it('经典公式载荷被中和且仍按 RFC 4180 加引号', () => {
      expect(neutralizeFormula('=HYPERLINK("http://evil","点我")'))
        .toBe("'=HYPERLINK(\"http://evil\",\"点我\")")
      expect(escapeCsvField('=HYPERLINK("http://evil","点我")'))
        .toBe('"\'=HYPERLINK(""http://evil"",""点我"")"')
    })

    it('合法数值不中和，避免数值列整列转文本', () => {
      expect(neutralizeFormula('-5')).toBe('-5')
      expect(neutralizeFormula('+3.14')).toBe('+3.14')
      expect(neutralizeFormula(-5)).toBe('-5')
      expect(escapeCsvField('-0.5')).toBe('-0.5')
    })

    it('非纯数字的 -/+ 前缀仍中和；多小数点与空数值不算数值', () => {
      expect(neutralizeFormula('-abc')).toBe("'-abc")
      // 与后端 isPlainNumber 同口径：单个小数点也算数值候选，保持两端导出结果一致
      expect(neutralizeFormula('-.')).toBe('-.')
      expect(neutralizeFormula('1.2.3')).toBe('1.2.3', '首字符非危险符号，不需中和')
      expect(neutralizeFormula('-')).toBe("'-")
    })

    it('内部危险字符不受影响，仅检查首字符', () => {
      expect(neutralizeFormula('a=1+2')).toBe('a=1+2')
    })
  })

  it('toCsv 逐单元格应用防护，BOM 仅由 buildCsvBlobContent 添加', () => {
    const csv = toCsv(['任务', '备注'], [['=1+1', '-5'], ['正常', '@evil']])
    expect(csv).toBe('任务,备注\r\n\'=1+1,-5\r\n正常,\'@evil')
    expect(buildCsvBlobContent(csv)).toBe(`\uFEFF${csv}`)
  })
})
