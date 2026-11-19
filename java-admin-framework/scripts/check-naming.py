#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-naming.py：标识符命名门禁（N 系列，重刻版）。

检查项：
N1 Java 文件名与顶层 public 类型 PascalCase 且一致；
N2 常量（public static final 字段）UPPER_SNAKE_CASE；
N3 方法与非常量字段 camelCase（方法首字母小写、字段不得为 UPPER_SNAKE 除非 static final）；
N4 权限码格式：@PreAuthorize hasAuthority('...') 中的权限码必须符合
   docs/NAMING.md §6 的小写段式（如 system:user:read）。

豁免白名单见文件内 NAMING_ALLOWLIST。退出码：0 通过；1 存在违规（阻断）。
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import (  # noqa: E402
    fail, finish, info, iter_java_files, read_text, rel,
    strip_java_comments_and_textblocks,
)

PASCAL_RE = re.compile(r"^[A-Z][A-Za-z0-9]*$")
UPPER_SNAKE_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
CAMEL_RE = re.compile(r"^[a-z][A-Za-z0-9]*$")

CLASS_DECL_RE = re.compile(r"^\s*(?:public|protected)?\s*(?:final\s+|abstract\s+|sealed\s+)*(?:class|interface|enum|record)\s+(\w+)", re.M)
CONST_FIELD_RE = re.compile(
    r"^\s*(?:@\w+\s+)*public\s+static\s+final\s+[\w<>\[\].,?\s]+?\s+(\w+)\s*(?:=|;)", re.M)
FIELD_DECL_RE = re.compile(
    r"^\s*(?:private|protected|public)\s+(?!static\s+final)(?:static\s+)?(?:final\s+)?[\w<>\[\].,?\s]+?\s+(\w+)\s*(?:=|;)", re.M)
METHOD_DECL_RE = re.compile(
    r"^\s*(?:@[\w.]+\s+)*(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?"
    r"[\w<>\[\].,?\s]+?\s+(\w+)\s*\([^;]*?\)\s*(?:\{|throws)", re.M)
PERM_CODE_RE = re.compile(r"hasAuthority\(\s*'([^']+)'\s*\)")

# 命名豁免：确有外部约定/平台要求的标识符（含评审记录）
NAMING_ALLOWLIST_FIELDS = {
    "serialVersionUID",
}
NAMING_ALLOWLIST_METHODS = {
    "else_",  # mybatis DSL CaseStep：SQL 关键字后缀下划线（评审登记）
}
NAMING_ALLOWLIST_FILES = {
    "CodeTemplates.java",  # 代码生成器模板源：内含生成产物的样板代码字符串
}
TYPE_KEYWORD_RE = re.compile(r"\b(class|record|interface|enum)\b")
PERMISSION_CODE_RE = re.compile(r"^[a-z][a-z0-9]*((\.[a-z0-9-]+)|:[a-z][a-z0-9-]+)+$")


def _line_of(text: str, pos: int) -> str:
    start = text.rfind("\n", 0, pos) + 1
    return text[start: text.find("\n", pos)]


def main() -> int:
    n_files = 0
    for path in iter_java_files():
        n_files += 1
        base = os.path.basename(path)
        rpath = rel(path)
        if base in NAMING_ALLOWLIST_FILES or base == "package-info.java":
            continue

        # N1 文件名 PascalCase
        stem = os.path.splitext(base)[0]
        if not PASCAL_RE.match(stem):
            fail(f"{rpath}：文件名非 PascalCase（{base}）")

        text = read_text(path)
        code = strip_java_comments_and_textblocks(text)  # 剔除注释/文本块，保留行位

        # N1 顶层 public 类型与文件名一致（仅约束列 0 的顶层声明；嵌套类型不检查）
        for cm in CLASS_DECL_RE.finditer(code):
            cname = cm.group(1)
            line_start = code.rfind("\n", 0, cm.start()) + 1
            decl_line = code[line_start: code.find("\n", cm.start())]
            if decl_line.startswith("public") and cname != stem:
                fail(f"{rpath}：顶层 public 类型 {cname} 与文件名 {stem} 不一致")

        # N2 常量 UPPER_SNAKE
        for fm in CONST_FIELD_RE.finditer(code):
            fname = fm.group(1)
            if fname in NAMING_ALLOWLIST_FIELDS:
                continue
            if not UPPER_SNAKE_RE.match(fname):
                fail(f"{rpath}：常量 {fname} 非 UPPER_SNAKE_CASE")

        # N3 字段 camelCase（排除常量与豁免）
        for fm in FIELD_DECL_RE.finditer(code):
            fname = fm.group(1)
            decl_line = _line_of(code, fm.start(1))
            if TYPE_KEYWORD_RE.search(decl_line):
                continue
            if fname in NAMING_ALLOWLIST_FIELDS:
                continue
            if UPPER_SNAKE_RE.match(fname) and "_" in fname:
                fail(f"{rpath}：非常量字段 {fname} 不得使用 UPPER_SNAKE_CASE")

        # N3 方法 camelCase
        for mm in METHOD_DECL_RE.finditer(code):
            mname = mm.group(1)
            decl_line = _line_of(code, mm.start(1))
            if TYPE_KEYWORD_RE.search(decl_line):
                continue  # record/class 声明误匹配
            if mname in NAMING_ALLOWLIST_METHODS:
                continue
            if not CAMEL_RE.match(mname):
                fail(f"{rpath}：方法 {mname} 非 camelCase")

        # N4 权限码格式（SCOPE_ 前缀为 OAuth2 scope authority，非业务权限码，豁免）
        for pm in PERM_CODE_RE.finditer(code):
            perm = pm.group(1)
            if perm.startswith("SCOPE_"):
                continue
            if not PERMISSION_CODE_RE.match(perm):
                fail(f"{rpath}：权限码格式非法（{perm}）")

    info(f"扫描 Java 文件 {n_files} 个")
    return finish("check-naming")


if __name__ == "__main__":
    sys.exit(main())
