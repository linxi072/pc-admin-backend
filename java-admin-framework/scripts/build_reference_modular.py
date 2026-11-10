#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_reference_modular.py — 从版本化结构链派生「手工全量初始化脚本 + 两份分类参考 SQL」。

输入（唯一事实源，只读）：
    scaffold-admin/src/main/resources/db/migration/ 下的**全部** V*.sql
    （版本范围由脚本运行时探测，2026-11-10 当前快照为 V32~V82，共 50 个 SQL 迁移）
    V57 的存量 tenant_id 回填原为 Java 类迁移，移除 Flyway 后改由
    db/init/02_backfill_tenant_id.sql 提供等价的可选 SQL，不参与本脚本派生。

输出（全部为生成产物，可整体重生成）：
    scaffold-admin/src/main/resources/db/init/01_full_schema.sql
        **唯一可执行的新库初始化脚本**：把全部 V*.sql 原文按版本号顺序拼接，
        末尾写入 schema_baseline 基线标记。由 DBA / 部署脚本显式执行，应用不自动执行。
    scaffold-admin/src/main/resources/db/reference/schema_ddl.sql
        迁移链全部 V*.sql 的 DDL（CREATE/ALTER/DROP/TRUNCATE/RENAME 等）语句，按来源标注。
    scaffold-admin/src/main/resources/db/reference/data_dml.sql
        迁移链全部 V*.sql 的 DML（INSERT/UPDATE/DELETE/REPLACE/MERGE）语句，按来源标注。

约束：
    - 仅读取结构链目录，绝不修改任何 V*.sql（版本文件保持不可变）。
    - **版本范围不得写死**：产物 header 由 version_span() 按实际扫描结果动态生成；
      docstring 中的 V32~V82 只是当前快照说明。新增版本文件后必须重跑本脚本。
    - 2026-10-28 审计整改：数据脚本已统一整合为两个分类参考文件，
      不再生成 db/reference/by-module/ 拆分件（避免散落重复）。
    - 2026-11-08 迁移：运行时已移除 Flyway，结构链降级为「版本化结构源 / 历史记录」，
      db/init/ 成为唯一可执行初始化入口；V*.sql 中的 Flyway 字样均为历史注释，保留原文以求证。

运行：python3 scripts/build_reference_modular.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_DIR = os.path.join(ROOT, "scaffold-admin", "src", "main", "resources", "db")
MIG_DIR = os.path.join(DB_DIR, "migration")
REF_DIR = os.path.join(DB_DIR, "reference")
INIT_DIR = os.path.join(DB_DIR, "init")

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
    """从版本文件名解析版本号；非标准命名返回 -1。"""
    m = VERSION_RE.match(fn)
    return int(m.group(1)) if m else -1


def version_span(sql_files):
    """返回结构链实际版本范围（如 V32~V82），供产物 header 动态标注。"""
    nums = [v for v in (version_of(f) for f in sql_files) if v >= 0]
    return "V%d~V%d" % (min(nums), max(nums)) if nums else "V?"


def list_migration_sql():
    files = []
    for fn in os.listdir(MIG_DIR):
        if fn.startswith("V") and fn.endswith(".sql") and "__" in fn:
            files.append(fn)
    # 按版本号数值排序：字符串排序会把 V100 排到 V99 之前，导致初始化顺序错乱
    files.sort(key=version_of)
    return files


def read_migration(fn):
    with open(os.path.join(MIG_DIR, fn), "r", encoding="utf-8") as f:
        return f.read()


def reference_header(span, count):
    return ("-- ============================================================\n"
            "-- 分类参考件（DDL / DML），由 scripts/build_reference_modular.py 从\n"
            "-- db/migration/ 版本化结构链（%s，共 %d 个 SQL 文件）自动派生，仅供阅读与追溯。\n"
            "-- 版本范围由脚本运行时探测；新增版本文件后须重跑本脚本重生成产物。\n"
            "-- 唯一可执行的新库初始化入口：db/init/01_full_schema.sql（DBA 手工执行）。\n"
            "-- 运行时已移除 Flyway；本文件不可单独执行建库，也不被应用自动加载。\n"
            "-- 重新生成：python3 scripts/build_reference_modular.py\n"
            "-- ============================================================\n\n"
            % (span, count))


def init_header(span, count):
    return ("-- ============================================================\n"
            "-- 全量初始化脚本（新库专用 / 手工执行）\n"
            "-- 由 scripts/build_reference_modular.py 从 db/migration/ 版本化结构链\n"
            "-- （%s，共 %d 个 SQL 文件）按版本号顺序逐字拼接生成，语句顺序与历史执行顺序一致。\n"
            "--\n"
            "-- 【执行方式】mysql --default-character-set=utf8mb4 -u<user> -p <db> < 01_full_schema.sql\n"
            "-- 【前置条件】先执行 00_create_database.sql 建库（utf8mb4_0900_ai_ci）。\n"
            "-- 【幂等性】仅适用于空库一次执行；内含非幂等的 CREATE TABLE / ALTER / CREATE INDEX，\n"
            "--           重复执行会报错。存量库升级请按版本自行评估差异，不要重跑本脚本。\n"
            "-- 【历史注释】下方 V*.sql 原文中出现的 Flyway 字样为历史说明，当前应用已不加载\n"
            "--           Flyway、不自动执行任何 DDL；保留原文以便逐版本追溯。\n"
            "-- 【基线标记】末尾写入 schema_baseline，供 scripts/init/verify_install.py 校验。\n"
            "-- 重新生成：python3 scripts/build_reference_modular.py\n"
            "-- ============================================================\n\n"
            % (span, count))


def init_footer(max_version, count):
    return ("\n\n-- ============================================================\n"
            "-- 初始化基线标记（用于 scripts/init/verify_install.py 校验）\n"
            "-- ============================================================\n"
            "CREATE TABLE IF NOT EXISTS `schema_baseline` (\n"
            "  `baseline_version` VARCHAR(32) NOT NULL,\n"
            "  `source_count` INT NOT NULL DEFAULT 0,\n"
            "  `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
            "  PRIMARY KEY (`baseline_version`)\n"
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;\n\n"
            "-- 清掉旧基线，确保表中只保留当前已初始化的版本\n"
            "DELETE FROM `schema_baseline`;\n\n"
            "INSERT INTO `schema_baseline` (`baseline_version`, `source_count`) "
            "VALUES ('V%d', %d);\n"
            % (max_version, count))


def main():
    if not os.path.isdir(MIG_DIR):
        print("ERROR: migration dir not found:", MIG_DIR, file=sys.stderr)
        return 1

    sql_files = list_migration_sql()
    if not sql_files:
        print("ERROR: no V*.sql found in", MIG_DIR, file=sys.stderr)
        return 1

    os.makedirs(REF_DIR, exist_ok=True)
    os.makedirs(INIT_DIR, exist_ok=True)

    ddl_all = []
    dml_all = []
    init_parts = []

    for fn in sql_files:
        content = read_migration(fn)
        init_parts.append("\n-- ############################################################\n"
                          "-- 来源: %s\n"
                          "-- ############################################################\n\n" % fn)
        init_parts.append(content)
        if not content.endswith("\n"):
            init_parts.append("\n")

        for stmt in split_statements(content):
            tok = first_meaningful_token(stmt)
            tag = "-- [from %s]" % fn
            if tok in DML_TOKENS:
                dml_all.append(tag + "\n" + stmt + ";\n")
            else:
                # 非 DML 一律归 schema_ddl（含 DDL 与 SET/USE 等 schema 级语句）
                ddl_all.append(tag + "\n" + stmt + ";\n")

    span = version_span(sql_files)
    max_version = max(version_of(f) for f in sql_files)

    with open(os.path.join(REF_DIR, "schema_ddl.sql"), "w", encoding="utf-8") as f:
        f.write(reference_header(span, len(sql_files)))
        f.write("\n".join(ddl_all))
    with open(os.path.join(REF_DIR, "data_dml.sql"), "w", encoding="utf-8") as f:
        f.write(reference_header(span, len(sql_files)))
        f.write("\n".join(dml_all))
    with open(os.path.join(INIT_DIR, "01_full_schema.sql"), "w", encoding="utf-8") as f:
        f.write(init_header(span, len(sql_files)))
        f.write("".join(init_parts))
        f.write(init_footer(max_version, len(sql_files)))

    print("OK. 版本 SQL 文件数: %d（%s）" % (len(sql_files), span))
    print("db/init/01_full_schema.sql 已生成")
    print("schema_ddl.sql 语句数: %d" % len(ddl_all))
    print("data_dml.sql  语句数: %d" % len(dml_all))
    return 0


if __name__ == "__main__":
    sys.exit(main())
