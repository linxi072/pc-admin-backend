#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-permission.py：菜单权限码一致性门禁（P1 重刻版）。

规则（docs/NAMING.md §6）：
P1 菜单侧权限码（db/init/dml.sql 中 sys_menu 的 permission_code 列，忽略 NULL）
   − 豁免清单 ⊆ Java 侧权限码（@PreAuthorize hasAuthority('...')）。

豁免仅限确有记录的场景（如前端纯展示占位码），逐条登记于 EXEMPT 且必须注明原因。
退出码：0 通过；1 存在未登记 Java 侧的菜单权限码（阻断）。
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import BACKEND_ROOT, fail, finish, info, read_text, rel, walk_files  # noqa: E402

DML_PATH = os.path.join(BACKEND_ROOT, "scaffold-admin", "src", "main", "resources", "db", "init", "dml.sql")

# 豁免：菜单权限码 → 原因（不得随意添加）
EXEMPT = {
    # V72 预留码：/v3/api-docs 为 Springdoc 标准端点，后端未挂独立 @PreAuthorize（local 开放、
    # 生产 springdoc.api-docs.enabled=false）。见 dml.sql V72 注释。
    "system:api-docs:read": "预留码：API 文档中心纯前端页，消费 Springdoc 标准端点",
}

MENU_COLUMN_INDEX = 8  # sys_menu 列序中 permission_code 的下标（0 起）
PERMISSION_RE = re.compile(r"hasAuthority\(\s*['\"]([^'\"]+)['\"]\s*\)")


def is_production_java(path: str) -> bool:
    """仅生产源码可证明权限码已在 Java 侧登记；测试夹具不算接口实现。"""
    norm = path.replace("\\", "/")
    return "/src/main/java/" in norm and "/src/test/" not in norm


def split_sql_tuple(s: str) -> list:
    """拆分 SQL 值元组内容，尊重单引号字符串与括号（如 NOW(3)）。"""
    items, buf, depth, in_str = [], [], 0, False
    i = 0
    while i < len(s):
        c = s[i]
        if in_str:
            if c == "'":
                if i + 1 < len(s) and s[i + 1] == "'":
                    buf.append("''")
                    i += 2
                    continue
                in_str = False
            buf.append(c)
        else:
            if c == "'":
                in_str = True
                buf.append(c)
            elif c == "(":
                depth += 1
                buf.append(c)
            elif c == ")":
                depth -= 1
                buf.append(c)
            elif c == "," and depth == 0:
                items.append("".join(buf).strip())
                buf = []
            else:
                buf.append(c)
        i += 1
    tail = "".join(buf).strip()
    if tail:
        items.append(tail)
    return items


def strip_quote(v: str):
    if v is None or v == "NULL":
        return None
    v = v.strip()
    if v.startswith("'") and v.endswith("'"):
        return v[1:-1].replace("''", "'")
    return v


def menu_permission_codes(sql_text: str) -> set:
    codes = set()
    # 定位 sys_menu 的 INSERT ... VALUES 块（可能多行多值组）
    for m in re.finditer(r"INSERT\s+(?:IGNORE\s+)?INTO\s+`?sys_menu`?\s*\(([^)]*)\)\s*VALUES\s*(.*?);", sql_text, re.S | re.I):
        cols = [c.strip("`").strip() for c in m.group(1).split(",")]
        idx = cols.index("permission_code") if "permission_code" in cols else MENU_COLUMN_INDEX
        body = m.group(2)
        # 逐值组拆分（顶层括号分组）
        depth, start = 0, None
        rows = []
        for i, c in enumerate(body):
            if c == "(":
                if depth == 0:
                    start = i
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0 and start is not None:
                    rows.append(body[start + 1:i])
                    start = None
        for row in rows:
            vals = split_sql_tuple(row)
            if len(vals) > idx:
                code = strip_quote(vals[idx])
                if code:
                    codes.add(code)
    return codes


def permission_codes_from_text(text: str) -> set:
    return {m.group(1) for m in PERMISSION_RE.finditer(text)}


def java_permission_codes() -> set:
    codes: set[str] = set()
    for path in walk_files(BACKEND_ROOT, (".java",)):
        if not is_production_java(path):
            continue
        codes |= permission_codes_from_text(read_text(path))
    return codes


def main() -> int:
    if not os.path.isfile(DML_PATH):
        fail(f"种子脚本缺失：{rel(DML_PATH)}")
        return finish("check-permission")

    menu_codes = menu_permission_codes(read_text(DML_PATH))
    java_codes = java_permission_codes()

    orphan = sorted(c for c in menu_codes if c not in java_codes and c not in EXEMPT)
    for c in orphan:
        fail(f"菜单权限码 {c} 未在 Java 侧登记且未豁免（P1）")

    unused_exempt = sorted(c for c in EXEMPT if c not in menu_codes)
    for c in unused_exempt:
        fail(f"豁免清单中的权限码 {c} 已不在菜单侧，请清理 EXEMPT")

    info(f"菜单权限码 {len(menu_codes)} 个；Java 侧权限码 {len(java_codes)} 个；豁免 {len(EXEMPT)} 个")
    return finish("check-permission")


if __name__ == "__main__":
    sys.exit(main())
