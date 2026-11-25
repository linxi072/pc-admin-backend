#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-no-procedure.py：数据库端例程禁用门禁（NP 系列，V1.1.0 新增）。

背景：项目已把全部业务逻辑收敛到 Java 应用层，数据库层只允许表结构、索引、
约束与必要的触发器。本门禁硬阻断存储过程/存储函数定义及其调用重新进入代码库，
锁定「存储过程迁移核实」一节的核实结论，避免后续版本回退。

检查项（均为硬性）：
NP1 SQL 不得出现 CREATE [OR REPLACE] [DEFINER=...] PROCEDURE/FUNCTION 定义；
NP2 SQL 不得出现 CALL <name>(...) 存储过程调用；
NP3 Java/XML 不得出现 {call ...} JDBC 转义调用；
NP4 Java 不得出现 CallableStatement / SimpleJdbcCall / StoredProcedure(Query)
    / @Procedure 等数据库端例程调用 API。

允许项：
- CREATE TRIGGER：需求明确允许保留必要触发器（仅计数，不阻断）；
- Markdown/文档中的历史描述：仅扫描 .sql/.java/.xml，不扫描 .md/.txt；
- 代码注释、Java 文本块、SQL 字符串字面量中的关键词：扫描前剥离。

退出码：0 通过；1 存在违规（check-all.py 汇总阻断）。
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import (  # noqa: E402
    REPO_ROOT, fail, finish, info, read_text, rel,
    strip_java_comments_and_textblocks, walk_files,
)

SQL_DEF_RE = re.compile(
    r"\bCREATE\s+(?:OR\s+REPLACE\s+)?(?:DEFINER\s*=\s*\S+\s+)?"
    r"(?:PROCEDURE|FUNCTION)\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?\w+",
    re.I,
)
SQL_CALL_RE = re.compile(r"\bCALL\s+[`\"]?[A-Za-z_][\w$]*[`\"]?\s*\(", re.I)
TRIGGER_DEF_RE = re.compile(
    r"\bCREATE\s+(?:OR\s+REPLACE\s+)?(?:DEFINER\s*=\s*\S+\s+)?TRIGGER\s+", re.I
)
JDBC_CALL_RE = re.compile(r"\{\s*CALL\s", re.I)
JAVA_PROC_API_RE = re.compile(
    r"\b(?:CallableStatement|SimpleJdbcCall|StoredProcedure|StoredProcedureQuery)\b|@Procedure\b"
)

SCAN_SUFFIXES = (".sql", ".java", ".xml")


def strip_sql_noncode(text: str) -> str:
    """剥离 SQL 行注释/块注释、单引号字符串、双引号标识符，保持行号与长度稳定。"""
    out: list[str] = []
    i, n = 0, len(text)
    state = "code"  # code | line | block | string | quoted
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "code":
            if c == "-" and nxt == "-":
                state = "line"
                out.append("  ")
                i += 2
                continue
            if c == "/" and nxt == "*":
                state = "block"
                out.append("  ")
                i += 2
                continue
            if c == "'":
                state = "string"
                out.append(" ")
                i += 1
                continue
            if c == '"':
                state = "quoted"
                out.append(" ")
                i += 1
                continue
            out.append(c)
            i += 1
        elif state == "line":
            if c == "\n":
                state = "code"
                out.append("\n")
            else:
                out.append(" ")
            i += 1
        elif state == "block":
            if c == "*" and nxt == "/":
                state = "code"
                out.append("  ")
                i += 2
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
        elif state == "string":
            if c == "'":
                if nxt == "'":
                    out.append("  ")
                    i += 2
                    continue
                state = "code"
            out.append(" " if c != "\n" else "\n")
            i += 1
        else:  # quoted identifier
            if c == '"':
                if nxt == '"':
                    out.append("  ")
                    i += 2
                    continue
                state = "code"
            out.append(" " if c != "\n" else "\n")
            i += 1
    return "".join(out)


def _line_of(text: str, pos: int) -> int:
    return text.count("\n", 0, pos) + 1


def scan_text(path: str, text: str) -> list[tuple[int, str, str]]:
    """扫描单个文件内容，返回 (行号, 规则编号, 命中片段)；非目标后缀返回空。"""
    suffix = os.path.splitext(path)[1].lower()
    hits: list[tuple[int, str, str]] = []
    if suffix == ".sql":
        code = strip_sql_noncode(text)
        for m in SQL_DEF_RE.finditer(code):
            hits.append((_line_of(code, m.start()), "NP1", m.group(0).strip()))
        for m in SQL_CALL_RE.finditer(code):
            hits.append((_line_of(code, m.start()), "NP2", m.group(0).strip()))
    elif suffix == ".java":
        code = strip_java_comments_and_textblocks(text)
        for m in JDBC_CALL_RE.finditer(code):
            hits.append((_line_of(code, m.start()), "NP3", m.group(0).strip()))
        for m in JAVA_PROC_API_RE.finditer(code):
            hits.append((_line_of(code, m.start()), "NP4", m.group(0).strip()))
    elif suffix == ".xml":
        code = re.sub(
            r"<!--.*?-->",
            lambda m: re.sub(r"[^\n]", " ", m.group(0)),
            text,
            flags=re.S,
        )
        for m in JDBC_CALL_RE.finditer(code):
            hits.append((_line_of(code, m.start()), "NP3", m.group(0).strip()))
    return hits


def main() -> int:
    scanned = 0
    triggers = 0
    for path in walk_files(REPO_ROOT, SCAN_SUFFIXES):
        text = read_text(path)
        scanned += 1
        if path.lower().endswith(".sql"):
            triggers += len(TRIGGER_DEF_RE.findall(strip_sql_noncode(text)))
        for line, rule, snippet in scan_text(path, text):
            fail(f"{rel(path)}:{line}：{rule} 检测到数据库端例程痕迹 {snippet!r}")
    info(f"扫描 {scanned} 个代码/SQL/XML 文件；允许项触发器定义 {triggers} 个")
    return finish("check-no-procedure")


if __name__ == "__main__":
    sys.exit(main())
