#!/usr/bin/env node
/**
 * i18n-leak-scan.mjs：i18n 残留泄漏扫描（重刻版）。
 *
 * 用法：node scripts/i18n-leak-scan.mjs <src 目录>
 *
 * 背景：V1.0.9 已移除自研 i18n（静态文案内联中文；动态键走 src/locales/zh-cn.js 静态字典 tx()）。
 * 扫描目标目录下 .vue/.js：
 * L1 不得 import vue-i18n / 调用 useI18n(；
 * L2 不得存在遗留独立 t('...') 调用（tx( 除外）；
 * L3 不得新增 $t( / i18n.t( 形式调用。
 *
 * 退出码：0 通过；1 存在泄漏（阻断）。
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const target = process.argv[2];
if (!target) {
  console.error("[i18n-leak-scan] 未指定目录。用法：node scripts/i18n-leak-scan.mjs <src 目录>");
  process.exit(1);
}

const SKIP_DIRS = new Set(["node_modules", "dist", ".git", "__pycache__"]);
let scanned = 0;
const leaks = [];

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) {
      if (!SKIP_DIRS.has(name)) walk(p);
      continue;
    }
    if (!/\.(vue|js)$/.test(name) || name === "i18n-leak-scan.mjs") continue;
    scanned++;
    const text = readFileSync(p, "utf8");
    // 剔除注释内容（// 行注释、/* */ 块注释），避免注释文案中的 "t('key')" 误报
    const stripped = text
      .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "))
      .replace(/(^|\n)(\s*\/\/[^\n]*)/g, (m, nl) => nl + m.slice(nl.length).replace(/[^\n]/g, " "));
    const lines = stripped.split("\n");
    lines.forEach((line, i) => {
      if (/from\s+['"]vue-i18n['"]/.test(line) || /useI18n\s*\(/.test(line)) {
        leaks.push(`${p}:${i + 1} L1 vue-i18n/useI18n`);
      }
      if (/(^|[^\w$.])t\(\s*['"][^'"]+['"]/.test(line)) {
        leaks.push(`${p}:${i + 1} L2 独立 t('...') 调用`);
      }
      if (/\$t\s*\(|i18n\.t\s*\(/.test(line)) {
        leaks.push(`${p}:${i + 1} L3 $t/i18n.t 调用`);
      }
    });
  }
}

walk(target);

if (leaks.length > 0) {
  for (const l of leaks) console.error(`[FAIL] ${l}`);
  console.error(`[i18n-leak-scan] EXIT=1（${leaks.length} 处泄漏 / 扫描 ${scanned} 文件）`);
  process.exit(1);
}
console.log(`[i18n-leak-scan] EXIT=0（扫描 ${scanned} 文件，无泄漏）`);
