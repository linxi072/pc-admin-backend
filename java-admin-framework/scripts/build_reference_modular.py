#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_reference_modular.py — 从 Flyway 迁移链派生「按功能模块分类」的参考 SQL。

输入（唯一事实源，只读）：
    scaffold-admin/src/main/resources/db/migration/V32__*.sql ... V59__*.sql
    （V57 为 Java 类迁移 V57__backfill_tenant_id.java，无 .sql，单独标注）

输出（参考件，不被 Flyway 扫描，可整体重生成）：
    scaffold-admin/src/main/resources/db/reference/by-module/<module>.sql
        每个文件 = 该模块相关迁移的原文拼接（带来源头注释），按版本序。
    scaffold-admin/src/main/resources/db/reference/by-module/README.md
        模块 -> 迁移映射与依赖说明。
    scaffold-admin/src/main/resources/db/reference/schema_ddl.sql
        V32~V59 的全部 DDL（CREATE/ALTER/DROP/TRUNCATE 等）语句，按来源标注。
    scaffold-admin/src/main/resources/db/reference/data_dml.sql
        V32~V59 的全部 DML（INSERT/UPDATE/DELETE/REPLACE/MERGE）语句，按来源标注。

约束：
    - 仅读取迁移目录，绝不修改任何迁移文件（Flyway checksum 不可变）。
    - 派生文件为「参考/追溯」用途；权威可执行来源永远是 db/migration/ 迁移链。
    - by-module 文件是逻辑抽取，依赖 V32 基线已存在；只在已含基线的库上整体阅读/执行，
      不要单独抽出某模块在无基线库上执行。

运行：python3 scripts/build_reference_modular.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIG_DIR = os.path.join(ROOT, "scaffold-admin", "src", "main", "resources", "db", "migration")
REF_DIR = os.path.join(ROOT, "scaffold-admin", "src", "main", "resources", "db", "reference")
BYMODULE_DIR = os.path.join(REF_DIR, "by-module")

# 模块分组：group_key -> (title, [version 前缀...], 说明)
MODULES = [
    ("baseline", "基础框架（V1~V31 合并）", ["V32"],
     "用户/角色/菜单/部门/岗位/字典/参数/审计/登录日志/权限/接口资源/工作流基线等核心表与种子"),
    ("file-center", "文件中心", ["V33", "V50"],
     "文件管理（V33）+ 文件去重（V50）"),
    ("csv-export-center", "CSV 导出中心", ["V34"], "导出任务与模板"),
    ("post-management", "岗位管理", ["V35"], "岗位表（并修复菜单 id 碰撞）"),
    ("import-center", "导入中心", ["V36"], "导入任务与错误明细"),
    ("sms-record", "短信记录", ["V37"], "短信发送记录"),
    ("system-parameters", "系统参数", ["V38"], "参数配置表"),
    ("observability-center", "可观测中心", ["V39"], "链路/N+1/幂等/告警收件箱；修复 V35/V36 菜单碰撞"),
    ("notification-center", "通知中心", ["V40", "V41", "V42", "V43", "V44", "V45"],
     "公告已读回执 + 通知模板/记录/偏好/免打扰/摘要"),
    ("mask-strategy", "脱敏策略中心", ["V46"], "脱敏策略与规则"),
    ("form-center", "表单中心", ["V47", "V53", "V58"], "表单定义试点（V47）+ 提交存储（V53）+ 定义菜单（V58）"),
    ("openapi-client", "开放平台", ["V48"], "OpenAPI 客户端"),
    ("workflow-monitor", "工作流监控", ["V49"], "工作流监控视图/表"),
    ("tenant-management", "租户管理（SaaS）", ["V51", "V54", "V57"],
     "租户与隔离基础（V51）+ 隔离态势菜单（V54）+ 租户回填（V57，Java 类迁移无 .sql）"),
    ("dashboard", "报表大屏", ["V52"], "大屏菜单播种"),
    ("realtime", "实时推送", ["V55"], "实时推送菜单"),
    ("data-scope", "数据权限", ["V56"], "数据权限配置菜单"),
    ("job-dependency", "任务依赖编排", ["V59"], "DAG 任务依赖"),
]

# 版本前缀 -> (group_key, title)
VERSION_TO_MODULE = {}
for key, title, vers, _desc in MODULES:
    for v in vers:
        VERSION_TO_MODULE[v] = (key, title)

DDL_KW = re.compile(r"^\s*(CREATE|ALTER|DROP|TRUNCATE|RENAME)\b", re.IGNORECASE)
DML_KW = re.compile(r"^\s*(INSERT|UPDATE|DELETE|REPLACE|MERGE)\b", re.IGNORECASE)
DML_TOKENS = {"INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE"}


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


def list_migration_sql():
    files = []
    for fn in os.listdir(MIG_DIR):
        if fn.startswith("V") and fn.endswith(".sql") and "__" in fn:
            files.append(fn)
    files.sort()
    return files


def main():
    if not os.path.isdir(MIG_DIR):
        print("ERROR: migration dir not found:", MIG_DIR, file=sys.stderr)
        return 1
    os.makedirs(BYMODULE_DIR, exist_ok=True)

    sql_files = list_migration_sql()
    if not sql_files:
        print("ERROR: no V*.sql found in", MIG_DIR, file=sys.stderr)
        return 1

    # 初始化每个模块的缓冲区
    module_buf = {m[0]: [] for m in MODULES}
    ddl_all = []
    dml_all = []

    for fn in sql_files:
        ver = fn.split("__", 1)[0]
        fpath = os.path.join(MIG_DIR, fn)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()

        mod = VERSION_TO_MODULE.get(ver)
        if mod is None:
            print("WARN: %s 未匹配到模块分组，跳过 by-module 归类" % fn)
            group_key = None
        else:
            group_key = mod[0]

        # by-module 拼接（原文 + 来源头）
        block = ["", "-- ============================================================",
                 "-- Source migration: %s" % fn,
                 "-- ============================================================", "",
                 content.rstrip(), ""]
        if group_key is not None:
            module_buf[group_key].append("\n".join(block))

        # 扁平 DDL/DML 分类
        for stmt in split_statements(content):
            tok = first_meaningful_token(stmt)
            tag = "-- [from %s]" % fn
            if tok in DML_TOKENS:
                dml_all.append(tag + "\n" + stmt + ";\n")
            else:
                # 非 DML 一律归 schema_ddl（含 DDL 与 SET/USE 等 schema 级语句）
                ddl_all.append(tag + "\n" + stmt + ";\n")

    # 写 by-module 文件
    written = []
    for key, title, _vers, desc in MODULES:
        buf = module_buf[key]
        out_path = os.path.join(BYMODULE_DIR, key + ".sql")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write("-- ============================================================\n")
            f.write("-- 模块参考 SQL：%s\n" % title)
            f.write("-- 说明：%s\n" % desc)
            f.write("-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）\n")
            f.write("-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，\n")
            f.write("--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。\n")
            f.write("-- 重新生成：python3 scripts/build_reference_modular.py\n")
            f.write("-- ============================================================\n")
            if not buf:
                f.write("\n-- （本模块无 .sql 迁移；如 V57 为 Java 类迁移，仅做数据回填，无 DDL/DML 源）\n")
            else:
                f.write("\n".join(buf))
                f.write("\n")
        written.append((key, len(buf)))

    # 写 by-module README
    readme = ["# 按功能模块分类的参考 SQL（db/reference/by-module/）\n",
              "\n",
              "本目录由 `scripts/build_reference_modular.py` 从 `db/migration/` 迁移链（V32~V59）自动派生，**仅供阅读与追溯**。\n",
              "\n",
              "> ⚠️ 这些 `.sql` 不是 Flyway 迁移，不被扫描，也**不可当迁移执行**。\n",
              "> 权威可执行来源始终是 `db/migration/` 迁移链（V57 为 Java 类迁移 `V57__backfill_tenant_id.java`）。\n",
              "> by-module 文件是逻辑抽取、依赖 V32 基线已存在，仅应在已含基线的库上整体阅读。\n",
              "\n",
              "## 模块 → 迁移映射\n",
              "\n",
              "| 模块文件 | 标题 | 来源迁移 | 说明 |\n",
              "| --- | --- | --- | --- |\n"]
    for key, title, vers, desc in MODULES:
        ver_list = ", ".join(vers)
        readme.append("| `%s.sql` | %s | %s | %s |\n" % (key, title, ver_list, desc))
    readme.append("\n")
    readme.append("重新生成命令：\n\n```bash\npython3 scripts/build_reference_modular.py\n```\n")
    with open(os.path.join(BYMODULE_DIR, "README.md"), "w", encoding="utf-8") as f:
        f.write("".join(readme))

    # 写扁平参考件
    header = ("-- ============================================================\n"
              "-- 分类参考件（DDL / DML），由 scripts/build_reference_modular.py 从\n"
              "-- db/migration/ 迁移链（V32~V59）自动派生，仅供阅读与追溯。\n"
              "-- 权威可执行来源：db/migration/ 迁移链（V57 为 Java 类迁移）。\n"
              "-- 不可当迁移执行；Flyway 只扫描 db/migration/。\n"
              "-- 重新生成：python3 scripts/build_reference_modular.py\n"
              "-- ============================================================\n\n")

    with open(os.path.join(REF_DIR, "schema_ddl.sql"), "w", encoding="utf-8") as f:
        f.write(header)
        f.write("\n".join(ddl_all))
    with open(os.path.join(REF_DIR, "data_dml.sql"), "w", encoding="utf-8") as f:
        f.write(header)
        f.write("\n".join(dml_all))

    print("OK. 迁移 SQL 文件数: %d" % len(sql_files))
    print("by-module 文件:")
    for k, n in written:
        print("  - %s.sql  (来源块数=%d)" % (k, n))
    print("schema_ddl.sql 语句数: %d" % len(ddl_all))
    print("data_dml.sql  语句数: %d" % len(dml_all))
    return 0


if __name__ == "__main__":
    sys.exit(main())
