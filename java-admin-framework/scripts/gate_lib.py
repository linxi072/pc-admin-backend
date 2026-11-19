#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gate_lib：质量门禁脚本公共库（路径解析、文件遍历、POM 解析、结果输出）。

约定：
- 脚本均置于 java-admin-framework/scripts/ 下，可从任意工作目录调用；
- 只依赖 Python 3 标准库；
- 检查失败统一 print [FAIL] 行并让调用方决定退出码（check-all.py 汇总阻断）。
"""
from __future__ import annotations

import os
import re
import sys

# 仓库路径锚点（本文件位于 java-admin-framework/scripts/）
SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
BACKEND_ROOT = os.path.dirname(SCRIPTS_DIR)          # java-admin-framework/
REPO_ROOT = os.path.dirname(BACKEND_ROOT)             # 仓库根 pc-admin-backend/
FRONTEND_ROOT = os.path.join(REPO_ROOT, "pc-admin-vue3-vite")

JAVA_SRC_PREFIX = "scaffold-"
MODULES_EXPECTED = [
    "scaffold-admin", "scaffold-common", "scaffold-realtime", "scaffold-kafka",
    "scaffold-codegen", "scaffold-security", "scaffold-framework",
    "scaffold-system", "scaffold-monitor", "scaffold-job",
    "scaffold-workbench", "scaffold-workflow", "scaffold-cms", "scaffold-im",
]

# 依赖 DAG 白名单（H5）：模块 → 允许依赖的内部模块集合。
# 权威来源：docs/ARCHITECTURE.md 依赖 DAG + 各模块 pom 实际声明（V1.1.0 校准）。
DEPENDENCY_DAG_WHITELIST = {
    "scaffold-common": set(),
    "scaffold-realtime": {"scaffold-common"},
    "scaffold-kafka": {"scaffold-common"},
    "scaffold-codegen": {"scaffold-common"},
    "scaffold-cms": {"scaffold-common"},
    "scaffold-im": {"scaffold-common"},
    "scaffold-security": {"scaffold-common", "scaffold-realtime"},
    "scaffold-framework": {"scaffold-common", "scaffold-realtime"},
    "scaffold-system": {"scaffold-common", "scaffold-realtime", "scaffold-security"},
    "scaffold-monitor": {"scaffold-common", "scaffold-framework", "scaffold-security", "scaffold-system"},
    "scaffold-job": {"scaffold-common", "scaffold-monitor", "scaffold-system"},
    "scaffold-workbench": {"scaffold-common", "scaffold-system"},
    "scaffold-workflow": {"scaffold-common", "scaffold-monitor", "scaffold-realtime", "scaffold-system"},
    "scaffold-admin": set(MODULES_EXPECTED) - {"scaffold-admin"},
}

FAILS: list[str] = []


def fail(msg: str) -> None:
    FAILS.append(msg)
    print(f"[FAIL] {msg}")


def info(msg: str) -> None:
    print(f"[INFO] {msg}")


def finish(name: str) -> int:
    """输出检查结论；返回退出码（0=通过，1=存在 FAIL）。"""
    if FAILS:
        print(f"[{name}] EXIT=1（{len(FAILS)} 处违规）")
        return 1
    print(f"[{name}] EXIT=0")
    return 0


def walk_files(root: str, suffixes: tuple[str, ...] = (".java",), skip_dirs: tuple[str, ...] = (".git", "target", "node_modules", "dist")):
    """遍历目录下指定后缀文件，跳过构建产物目录。"""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in skip_dirs]
        for fn in filenames:
            if fn.endswith(suffixes):
                yield os.path.join(dirpath, fn)


def iter_java_files():
    """迭代后端全部 Java 源文件（含 main 与 test）。"""
    for path in walk_files(BACKEND_ROOT, (".java",)):
        yield path


def rel(path: str) -> str:
    return os.path.relpath(path, REPO_ROOT)


def read_text(path: str) -> str:
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def strip_java_comments_and_textblocks(text: str) -> str:
    r"""剥离 Java 注释与文本块（三个双引号包裹的 text block）内容，以等长空白回填，
    保持行号与列位稳定。用途：命名/权限码等扫描跳过代码模板与文档注释中的非声明内容。
    """
    out = []
    i, n = 0, len(text)
    state = "code"  # code | line_comment | block_comment | textblock
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "code":
            if c == "/" and nxt == "/":
                state = "line_comment"
                out.append("  ")
                i += 2
                continue
            if c == "/" and nxt == "*":
                state = "block_comment"
                out.append("  ")
                i += 2
                continue
            if text.startswith('"""', i):
                state = "textblock"
                out.append('"""')
                i += 3
                continue
            out.append(c)
            i += 1
        elif state == "line_comment":
            if c == "\n":
                state = "code"
                out.append("\n")
            else:
                out.append(" " if c != "\t" else "\t")
            i += 1
        elif state == "block_comment":
            if c == "*" and nxt == "/":
                state = "code"
                out.append("  ")
                i += 2
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
        else:  # textblock
            if text.startswith('"""', i):
                state = "code"
                out.append('"""')
                i += 3
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
    return "".join(out)


POM_VERSION_RE = re.compile(r"<version>([^<]+)</version>")
POM_PARENT_BLOCK_RE = re.compile(r"<parent>(.*?)</parent>", re.S)
POM_ARTIFACT_RE = re.compile(r"<artifactId>([^<]+)</artifactId>")


def parse_pom(path: str) -> dict:
    """轻量解析 POM：返回 project version、parent block、依赖 artifactId 集合。"""
    text = read_text(path)
    parent_m = POM_PARENT_BLOCK_RE.search(text)
    parent_version = None
    parent_artifact = None
    if parent_m:
        mv = POM_VERSION_RE.search(parent_m.group(1))
        ma = POM_ARTIFACT_RE.search(parent_m.group(1))
        parent_version = mv.group(1) if mv else None
        parent_artifact = ma.group(1) if ma else None
    # project 自身 version：取 <parent> 之后第一个 <version>
    after_parent = text[parent_m.end():] if parent_m else text
    pv = POM_VERSION_RE.search(after_parent)
    project_version = pv.group(1) if pv else None
    # 依赖 artifactId（粗粒度：全部 artifactId 中含 scaffold- 的，排除自身与 parent）
    deps = set()
    for m in POM_ARTIFACT_RE.finditer(text):
        aid = m.group(1)
        if aid.startswith("scaffold-") and aid != os.path.basename(os.path.dirname(path)):
            deps.add(aid)
    return {
        "text": text,
        "project_version": project_version,
        "parent_version": parent_version,
        "parent_artifact": parent_artifact,
        "scaffold_deps": deps,
    }


def module_poms() -> dict:
    """返回 {module_name: pom_path}（14 个模块）。"""
    out = {}
    for name in MODULES_EXPECTED:
        p = os.path.join(BACKEND_ROOT, name, "pom.xml")
        if os.path.isfile(p):
            out[name] = p
    return out


def list_modules_on_disk() -> list:
    return sorted(
        d for d in os.listdir(BACKEND_ROOT)
        if d.startswith(JAVA_SRC_PREFIX) and os.path.isdir(os.path.join(BACKEND_ROOT, d))
    )
