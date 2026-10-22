// M12 SFC 校验：parse + compileScript + compileTemplate（沙箱内 vite build 受资源限制，绕过）
// 用法：node scripts/check-sfc.mjs <file.vue> [more.vue ...]
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

let failed = 0
for (const file of process.argv.slice(2)) {
  const source = readFileSync(file, 'utf-8')
  const { descriptor, errors } = parse(source, { filename: file })
  if (errors.length) {
    failed++
    console.error(`PARSE FAIL ${file}`)
    errors.forEach((e) => console.error('  -', String(e.message || e)))
    continue
  }
  try {
    if (descriptor.script || descriptor.scriptSetup) {
      compileScript(descriptor, { id: 'm12-check' })
    }
    if (descriptor.template) {
      const tpl = compileTemplate({
        source: descriptor.template.content,
        filename: file,
        id: 'm12-check'
      })
      if (tpl.errors.length) {
        throw new Error('template errors: ' + tpl.errors.map(String).join('; '))
      }
    }
    console.log(`SFC OK   ${file}`)
  } catch (e) {
    failed++
    console.error(`COMPILE FAIL ${file}`)
    console.error('  -', String(e.message || e))
  }
}
process.exit(failed === 0 ? 0 : 1)
