// i18n 硬编码查漏扫描器（仅用于本任务的漏译排查，不进生产构建）。
//
// 策略：在 .vue 文件中抽取「字面量中文」，排除：
//  1) 注释（// 行注释、/* */ 块注释、<!-- --> 模板注释）
//  2) t('...') / t("...") 的实参（已是 i18n key，其译文在 messages.js）
//  3) 绑定属性（:prop / v- / @event，值来自表达式而非字面量）
//  4) placeholder 属性值（按本任务约定暂不作漏译处理）
//
// 输出 JSON 到 /tmp/i18n-leaks.json：[{file, line, kind, text}]
import fs from 'node:fs'
import path from 'node:path'

const root = process.argv[2] || 'src'
const out = process.argv[3] || '/tmp/i18n-leaks.json'
const HAN = /[一-鿿]/

function walk(dir) {
  const out = []
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) out.push(...walk(p))
    else if (e.name.endsWith('.vue')) out.push(p)
  }
  return out
}

function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/<!--[\s\S]*?-->/g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:])(\/\/)[^\n]*/g, (m, pre) => pre + ' '.repeat(m.length - pre.length))
}

const leaks = []
for (const file of walk(root)) {
  const raw = fs.readFileSync(file, 'utf8')
  const src = stripComments(raw)
  const lines = src.split('\n')
  lines.forEach((line, idx) => {
    if (!HAN.test(line)) return
    const lineNo = idx + 1
    // 1) 模板静态文本节点：>...中文...< （不含 { 或 < 或 > 干扰）
    const textRe = />([^<>{}]*[一-鿿][^<>{}]*)</g
    let m
    while ((m = textRe.exec(line))) {
      leaks.push({ file, line: lineNo, kind: 'text', text: m[1].trim() })
    }
    // 2) 静态属性值：prop="中文"（排除 : @ v- 绑定，排除 placeholder）
    const attrRe = /(?:^|[^: @])(\b[a-zA-Z][\w-]*)="([^"{}]*[一-鿿][^"{}]*)"/g
    while ((m = attrRe.exec(line))) {
      const name = m[1]
      if (name.startsWith(':') || name.startsWith('@') || name.startsWith('v-')) continue
      if (name === 'placeholder') continue
      leaks.push({ file, line: lineNo, kind: `attr:${name}`, text: m[2].trim() })
    }
    // 3) 脚本字符串字面量：含中文，且不是 t(...) 的实参
    const strRe = /(['"`])((?:\\.|(?!\1).)*?)\1/g
    while ((m = strRe.exec(line))) {
      const lit = m[2]
      if (!HAN.test(lit)) continue
      const before = line.slice(0, m.index).trimEnd()
      // 直接前置为 t( 的，视作 i18n key，忽略
      if (/(^|[^.\w])t\(\s*$/.test(before)) continue
      leaks.push({ file, line: lineNo, kind: 'literal', text: lit.trim() })
    }
  })
}
fs.writeFileSync(out, JSON.stringify(leaks, null, 2))
console.log(`scanned ${walk(root).length} files, ${leaks.length} candidate leaks -> ${out}`)
