#!/usr/bin/env node
/**
 * check-sfc.mjs：单文件组件（SFC）基础门禁（重刻版）。
 *
 * 用法：node scripts/check-sfc.mjs <file.vue> [...]
 *
 * 检查项：
 * S1 必须包含 <template> 与 <script> 块；
 * S2 script 块必须为 <script setup> 组合式 API。
 *
 * 退出码：0 通过；1 存在违规（阻断）。
 */
import { readFileSync } from "node:fs";

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error("[check-sfc] 未指定文件。用法：node scripts/check-sfc.mjs <file.vue> [...]");
  process.exit(1);
}

let failures = 0;
for (const file of files) {
  let text;
  try {
    text = readFileSync(file, "utf8");
  } catch (e) {
    console.error(`[FAIL] ${file}: 无法读取（${e.message}）`);
    failures++;
    continue;
  }
  const rel = file;
  if (!/<template[\s>]/.test(text)) { console.error(`[FAIL] ${rel}: S1 缺少 <template> 块`); failures++; }
  if (!/<script[\s>]/.test(text)) { console.error(`[FAIL] ${rel}: S1 缺少 <script> 块`); failures++; }
  else if (!/<script\s+setup[\s>]/.test(text)) { console.error(`[FAIL] ${rel}: S2 script 块未使用 <script setup>`); failures++; }
}

if (failures > 0) {
  console.error(`[check-sfc] EXIT=1（${failures} 处违规）`);
  process.exit(1);
}
console.log(`[check-sfc] EXIT=0（${files.length} 个文件通过）`);
