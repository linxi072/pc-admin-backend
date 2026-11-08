#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_reference_modular.py — 从 Flyway 迁移链派生「两个初始化参考 SQL」。

输入（唯一事实源，只读）：
    scaffold-admin/src/main/resources/db/migration/ 下的**全部** V*.sql
    （版本范围由脚本运行时探测，2026-11-08 当前快照为 V32~V81，共 49 个 SQL 迁移；
      V57 为 Java 类迁移，位于
      scaffold-admin/src/main/java/db/migration/V57__backfill_tenant_id.java，
      无对应 .sql，不参与本脚本派生）

输出（参考件，不被 Flyway 扫描，可整体重生成）：
    scaffold-admin/src/main/resources/db/reference/schema_ddl.sql
        迁移链全部 V*.sql 的 DDL（CREATE/ALTER/DROP/TRUNCATE/RENAME 等）语句，按来源标注。
    scaffold-admin/src/main/resources/db/reference/data_dml.sql
        迁移链全部 V*.sql 的 DML（INSERT/UPDATE/DELETE/REPLACE/MERGE）语句，按来源标注。

约束：
    - 仅读取迁移目录，绝不修改任何迁移文件（Flyway checksum 不可变）。
    - 派生文件为「参考/追溯」用途；权威可执行来源永远是 db/migration/ 迁移链。
    - **版本范围不得写死**：参考件 header 由 version_span() 按实际扫描结果动态生成；
      docstring 中的 V32~V81 只是当前快照说明。新增迁移后必须重跑本脚本，
      否则参考件会落后于迁移链（2026-11-08 曾发现 V60~V81 共 22 个迁移漏派生）。
    - 2026-10-28 审计整改：数据脚本已统一整合为上述两个初始化文件，
      不再生成 db/reference/by-module/ 拆分件（避免散落重复），历史 by-module
      件已删除并提交清理。如需恢复逐模块视图，从 git 历史取回即可。

运行：python3 scripts/build_reference_modular.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIG_DIR = os.path.join(ROOT, "scaffold-admin", "src", "main", "resources", "db", "migration")
REF_DIR = os.path.join(ROOT, "scaffold-admin", "src", "main", "resources", "db", "reference")

DDL_KW = re.compile(r"^\s*(CREATE|ALTER|DROP|TRUNCATE|RENAME)\b", re.IGNORECASE)
DML_KW = re.compile(r"^\s*(INSERT|UPDATE|DELETE|REPLACE|MERGE)\b", re.IGNORECASE)
DML_TOKENS = {"INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE"}
VERSION_RE = re.compile(r"^V(\d+)__")


def split_statements(sql):
    """按 ';' 切分 SQL 语句，正确处理单/双引号（含 MySQL '' 与 \\' 转义）与 -- / /* */ 注释。"""
    stmts = []
    cur = []
    i, n = 0, len(sql)
    in_s = in_d = in_line = in_block = False
    while i < n:
        c = sql[i]
        nx = sql[i + 1] if i + 1 < n else ""
        if in_line:
            cur.append(c)
            if c == "\n":
                in_line = False
            i += 1
            continue
        if in_block:
            cur.append(c)
            if c == "*" and nx == "/":
                in_block = False
                i += 2
                continue
            i += 1
            continue
        if c == "-" and nx == "-":
            in_line = True
            cur.append(c); cur.append(nx)
            i += 2
            continue
        if c == "/" and nx == "*":
            in_block = True
            i += 2
            continue
        if c == "'" and not in_d:
            cur.append(c); i += 1
            while i < n:
                d = sql[i]; dn = sql[i + 1] if i + 1 < n else ""
                cur.append(d)
                if d == "'" and dn == "'":          # MySQL 双写转义 ''
                    cur.append(dn); i += 2; continue
                if d == "'" and len(cur) >= 2 and cur[-2] == "\\":  # \' 转义
                    i += 1; continue
                if d == "'":                        # 真正的字符串结束
                    i += 1; break
                i += 1
            continue
        if c == '"' and not in_s:
            cur.append(c); i += 1
            while i < n:
                d = sql[i]; dn = sql[i + 1] if i + 1 < n else ""
                cur.append(d)
                if d == '"' and dn == '"':          # MySQL 双写转义 ""
                    cur.append(dn); i += 2; continue
                if d == '"' and len(cur) >= 2 and cur[-2] == "\\":  # \" 转义
                    i += 1; continue
                if d == '"':                        # 真正的字符串结束
                    i += 1; break
                i += 1
            continue
        if c == ";" and not in_s and not in_d:
            s = "".join(cur).strip()
            if s:
                stmts.append(s)
            cur = []
            i += 1
            continue
        cur.append(c)
        i += 1
    rem = "".join(cur).strip()
    if rem:
        stmts.append(rem)
    return stmts


def first_meaningful_token(stmt):
    # 去掉前导 -- 行注释，取首词
    cleaned = []
    for ln in stmt.split("\n"):
        idx = ln.find("--")
        cleaned.append(ln[:idx] if idx != -1 else ln)
    s = " ".join(cleaned).strip()
    m = re.match(r"^\s*([A-Za-z]+)", s, re.IGNORECASE)
    return m.group(1).upper() if m else ""


def version_of(fn):
    """从迁移文件名解析版本号；非标准命名返回 -1。"""
    m = VERSION_RE.match(fn)
    return int(m.group(1)) if m else -1


def version_span(sql_files):
    """返回迁移链实际版本范围（如 V32~V81），供参考件 header 动态标注。"""
    nums = [v for v in (version_of(f) for f in sql_files) if v >= 0]
    return "V%d~V%d" % (min(nums), max(nums)) if nums else "V?"


def list_migration_sql():
    files = []
    for fn in os.listdir(MIG_DIR):
        if fn.startswith("V") and fn.endswith(".sql") and "__" in fn:
            files.append(fn)
    # 按版本号数值排序：字符串排序会把 V100 排到 V99 之前，导致参考件顺序错乱
    files.sort(key=version_of)
    return files


def main():
    if not os.path.isdir(MIG_DIR):
        print("ERROR: migration dir not found:", MIG_DIR, file=sys.stderr)
        return 1

    sql_files = list_migration_sql()
    if not sql_files:
        print("ERROR: no V*.sql found in", MIG_DIR, file=sys.stderr)
        return 1

    ddl_all = []
    dml_all = []

    for fn in sql_files:
        fpath = os.path.join(MIG_DIR, fn)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()

        for stmt in split_statements(content):
            tok = first_meaningful_token(stmt)
            tag = "-- [from %s]" % fn
            if tok in DML_TOKENS:
                dml_all.append(tag + "\n" + stmt + ";\n")
            else:
                # 非 DML 一律归 schema_ddl（含 DDL 与 SET/USE 等 schema 级语句）
                ddl_all.append(tag + "\n" + stmt + ";\n")

    # 写扁平参考件：版本范围按实际扫描结果动态生成，杜绝 header 再次落后于迁移链
    span = version_span(sql_files)
    header = ("-- ============================================================\n"
              "-- 分类参考件（DDL / DML），由 scripts/build_reference_modular.py 从\n"
              "-- db/migration/ 迁移链（%s，共 %d 个 SQL 迁移）自动派生，仅供阅读与追溯。\n"
              "-- 版本范围由脚本运行时探测；新增迁移后须重跑本脚本重生成参考件。\n"
              "-- 权威可执行来源：db/migration/ 迁移链（V57 为 Java 类迁移，位于\n"
              "--   scaffold-admin/src/main/java/db/migration/，无对应 .sql）。\n"
              "-- 不可当迁移执行；Flyway 只扫描 db/migration/。\n"
              "-- 重新生成：python3 scripts/build_reference_modular.py\n"
              "-- ============================================================\n\n"
              % (span, len(sql_files)))

    with open(os.path.join(REF_DIR, "schema_ddl.sql"), "w", encoding="utf-8") as f:
        f.write(header)
        f.write("\n".join(ddl_all))
    with open(os.path.join(REF_DIR, "data_dml.sql"), "w", encoding="utf-8") as f:
        f.write(header)
        f.write("\n".join(dml_all))

    print("OK. 迁移 SQL 文件数: %d（%s）" % (len(sql_files), span))
    print("schema_ddl.sql 语句数: %d" % len(ddl_all))
    print("data_dml.sql  语句数: %d" % len(dml_all))
    return 0


if __name__ == "__main__":
    sys.exit(main())
