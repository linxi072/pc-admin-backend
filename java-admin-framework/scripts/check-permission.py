#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P1 菜单权限码 ↔ 接口权限码一致性守护（零依赖，仅标准库）。

若依规范要求 ``sys_menu.perms`` 与接口 ``@PreAuthorize`` 的权限码同源同字，
否则会出现「菜单可见、进入即 403」。本脚本静态比对三类事实来源：

  菜单侧  ``sys_menu`` 种子的 ``permission_code``
          * ``INSERT [IGNORE] INTO sys_menu ... ;`` 块内的权限码字面量
          * ``UPDATE sys_menu SET permission_code = '...'``
  接口侧  * 全部 ``scaffold-*/src/main/java`` 的 ``@PreAuthorize("hasAuthority('...')")``
          * ``sys_api_resource`` 种子的 ``permission_code``（历史资源表驱动鉴权，
            如 ``report:usage:read``，见 V32 L1268–L1270）

规则 P1：``menu_codes - EXEMPT ⊆ interface_codes``，否则 EXIT=1。
反向**不**校验：按钮级增删改权限由 ``ApiResourceAutoSyncRunner`` 自动登记并授予
SUPER_ADMIN，菜单通常只挂 ``read`` 码，接口侧本就是菜单侧的超集。

豁免见 ``DEFAULT_EXEMPT``：每条必须注明理由，禁止无理由全盘豁免。

单测注入点：``--sql-dir`` / ``--src``（可多次）/ ``--exempt`` / ``--backend-root``。

退出码：0 = 通过；1 = 存在 P1 违规；2 = 参数 / 工程路径错误。
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
from concurrent.futures import ThreadPoolExecutor

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BACKEND_ROOT = os.path.dirname(SCRIPT_DIR)

# 权限码形态：至少 3 段（2 个冒号），全小写，段内允许连字符。
# menu_code 只有 2 段（如 system:user），不会误采。
PERMISSION_RE = re.compile(
    r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*(?::[a-z][a-z0-9]*(?:-[a-z0-9]+)*){2,}$"
)

MENU_INSERT_RE = re.compile(
    r"INSERT\s+(?:IGNORE\s+)?INTO\s+sys_menu\b[^;]*;", re.S | re.I)
MENU_UPDATE_RE = re.compile(
    r"UPDATE\s+sys_menu\s+SET\s+permission_code\s*=\s*'([^']+)'", re.I)
API_INSERT_RE = re.compile(
    r"INSERT\s+(?:IGNORE\s+)?INTO\s+sys_api_resource\b[^;]*;", re.S | re.I)
HAS_AUTHORITY_RE = re.compile(r"hasAuthority\('([^']+)'\)")
QUOTED_RE = re.compile(r"'([^']*)'")
SQL_LINE_COMMENT_RE = re.compile(r"--[^\n]*")
JAVA_BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)
JAVA_LINE_COMMENT_RE = re.compile(r"//[^\n]*")

# 显式豁免：每条必须有可核查的出处与理由。
DEFAULT_EXEMPT = {
    "system:api-docs:read": (
        "V72__api_docs_menu.sql 明示预留：springdoc /v3/api-docs 为纯前端浏览端点，"
        "无后端 Controller 与 @PreAuthorize（local 开放 / prod 关闭）"
    ),
}


def strip_sql_comments(text: str) -> str:
    """剥掉 SQL 行注释但保持字符串长度，使行号仍可精确计算。"""
    return SQL_LINE_COMMENT_RE.sub(lambda m: " " * len(m.group(0)), text)


def strip_java_comments(text: str) -> str:
    """剥掉 Java 注释，避免 javadoc 示例被误采为真实接口权限码。"""
    text = JAVA_BLOCK_COMMENT_RE.sub(lambda m: " " * len(m.group(0)), text)
    return JAVA_LINE_COMMENT_RE.sub(lambda m: " " * len(m.group(0)), text)


def read_file(path: str):
    try:
        with open(path, encoding="utf-8", errors="ignore") as fh:
            return path, fh.read()
    except OSError:
        return path, ""


def java_source_roots(backend_root: str):
    """自动发现全部 scaffold-* 模块的 src/main/java。"""
    pattern = os.path.join(backend_root, "scaffold-*", "src", "main", "java")
    return sorted(p for p in glob.glob(pattern) if os.path.isdir(p))


def collect_sql(sql_dir: str, threads: int = 16):
    """返回 (menu_codes, interface_codes)；每个码记录 (code, file, line)。"""
    files = sorted(glob.glob(os.path.join(sql_dir, "*.sql")))
    with ThreadPoolExecutor(max_workers=threads) as pool:
        texts = dict(pool.map(read_file, files))

    menu, interface = {}, {}
    for path, raw in texts.items():
        text = strip_sql_comments(raw)
        short = os.path.basename(path)

        for block in MENU_INSERT_RE.finditer(text):
            line0 = text.count("\n", 0, block.start()) + 1
            for q in QUOTED_RE.finditer(block.group(0)):
                code = q.group(1)
                if not PERMISSION_RE.match(code):
                    continue
                line = line0 + block.group(0).count("\n", 0, q.start())
                menu.setdefault(code, (short, line))
        for upd in MENU_UPDATE_RE.finditer(text):
            code = upd.group(1)
            if PERMISSION_RE.match(code):
                menu.setdefault(code, (short, text.count("\n", 0, upd.start()) + 1))

        for block in API_INSERT_RE.finditer(text):
            for q in QUOTED_RE.finditer(block.group(0)):
                code = q.group(1)
                if PERMISSION_RE.match(code):
                    line0 = text.count("\n", 0, block.start()) + 1
                    interface.setdefault(
                        code, (short, line0 + block.group(0).count("\n", 0, q.start())))
    return menu, interface


def collect_java(src_dirs, threads: int = 16):
    """返回 {code: (file, line)}。"""
    files = []
    for root in src_dirs:
        for base, _, names in os.walk(root):
            for name in names:
                if name.endswith(".java"):
                    files.append(os.path.join(base, name))
    if not files:
        return {}
    with ThreadPoolExecutor(max_workers=threads) as pool:
        texts = dict(pool.map(read_file, files))

    found = {}
    for path, raw in texts.items():
        text = strip_java_comments(raw)
        for m in HAS_AUTHORITY_RE.finditer(text):
            code = m.group(1)
            if PERMISSION_RE.match(code):
                found.setdefault(code, (os.path.basename(path),
                                        text.count("\n", 0, m.start()) + 1))
    return found


def scan(sql_dir: str, src_dirs, exempt=None):
    """核心比对（纯函数式，供单测直接调用）。"""
    if not os.path.isdir(sql_dir):
        raise FileNotFoundError(f"SQL 目录不存在: {sql_dir}")
    if not src_dirs or any(not os.path.isdir(p) for p in src_dirs):
        raise FileNotFoundError("Java 源码根不存在")
    if not glob.glob(os.path.join(sql_dir, "*.sql")):
        raise FileNotFoundError(f"SQL 目录下无 .sql 文件: {sql_dir}")

    whitelist = dict(DEFAULT_EXEMPT)
    for code in exempt or ():
        whitelist[code] = "命令行显式豁免（单测 / 临时排查）"

    menu, api_sql = collect_sql(sql_dir)
    api_java = collect_java(src_dirs)
    interface = dict(api_sql)
    for code, loc in api_java.items():
        interface.setdefault(code, loc)

    violations = [
        {"code": code, "menu_source": f"{loc[0]}:{loc[1]}"}
        for code, loc in sorted(menu.items())
        if code not in interface and code not in whitelist
    ]
    return {
        "menu_codes": len(menu),
        "interface_codes": len(interface),
        "interface_from_java": len(api_java),
        "interface_from_api_resource": len(api_sql),
        "exempt": sorted(whitelist),
        "violations": violations,
        "passed": not violations,
    }


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="P1 菜单权限码 ↔ 接口权限码一致性（sys_menu / sys_api_resource / @PreAuthorize）")
    ap.add_argument("--backend-root", default=BACKEND_ROOT, help="后端仓根")
    ap.add_argument("--sql-dir", default=None, help="迁移脚本目录（单测用）")
    ap.add_argument("--src", action="append", default=None,
                    help="Java 源码根，可多次；给出后不再自动发现（单测用）")
    ap.add_argument("--exempt", action="append", default=None,
                    help="追加豁免权限码，可多次")
    ap.add_argument("--json", action="store_true", help="以 JSON 输出结构化结果")
    ap.add_argument("--quiet", action="store_true", help="仅输出结论摘要")
    args = ap.parse_args(argv)

    backend_root = os.path.abspath(args.backend_root)
    sql_dir = os.path.abspath(args.sql_dir or os.path.join(
        backend_root, "scaffold-admin", "src", "main", "resources", "db", "migration"))
    src_dirs = ([os.path.abspath(p) for p in args.src]
                if args.src else java_source_roots(backend_root))

    try:
        result = scan(sql_dir, src_dirs, args.exempt)
    except (FileNotFoundError, OSError) as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 2

    conclusion = ("P1 权限码一致性校验通过" if result["passed"] else
                  f"P1 权限码一致性校验未通过：{len(result['violations'])} 条悬空菜单权限码"
                  "（普通角色按菜单授权后将 403）")
    if args.json:
        # 纯 JSON，便于 CI / 脚本消费，不混入中文结论行
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        if not args.quiet:
            print(f"P1 权限码一致性：菜单 {result['menu_codes']} 条 / "
                  f"接口 {result['interface_codes']} 条"
                  f"（注解 {result['interface_from_java']} + "
                  f"资源表 {result['interface_from_api_resource']}）")
            print(f"豁免 {len(result['exempt'])} 条：{', '.join(result['exempt'])}")
            for v in result["violations"]:
                print(f"[P1] 菜单权限码未在接口侧登记: {v['code']}  "
                      f"(来源 {v['menu_source']})")
        print(conclusion)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
