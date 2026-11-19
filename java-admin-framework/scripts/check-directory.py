#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-directory.py：目录约定门禁（D 系列精选重刻版）。

检查项：
D1 模块白名单：磁盘上的 scaffold-* 模块 ≡ 权威 14 模块清单；
D2 启动类归属：@SpringBootApplication 源码仅存在于 scaffold-admin；
D3 配置归属：src/main/resources 下 application*.yml/properties 仅存在于 scaffold-admin；
D4 前端视图归位：.vue 视图文件必须位于 pc-admin-vue3-vite/src/views/ 下（components 除外）；
D5 路由动态 import 归位：router 中的 import(...) 目标必须指向 src/views 或 src/layout；
D6 文档死链：docs/README.md、两级 README.md 中的相对链接目标必须存在；
D7 门禁脚本齐备：scripts/ 下 8 个权威门禁脚本 + suppression 文件存在。

退出码：0 通过；1 存在违规（阻断）。
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import (  # noqa: E402
    BACKEND_ROOT, FRONTEND_ROOT, MODULES_EXPECTED, REPO_ROOT,
    fail, finish, info, list_modules_on_disk, read_text, rel, walk_files,
)

GATE_SCRIPTS = [
    "check-all.py", "check-conventions.py", "check-naming.py", "check-directory.py",
    "check-cluster-layout.py", "check-permission.py", "check-api-contract.py",
    "check-version.py", "gate_lib.py", "dependency-check-suppressions.xml",
]

DOC_INDEX_FILES = [
    os.path.join(BACKEND_ROOT, "docs", "README.md"),
    os.path.join(BACKEND_ROOT, "README.md"),
    os.path.join(REPO_ROOT, "CHANGELOG.md"),
    os.path.join(FRONTEND_ROOT, "README.md"),
]

MD_LINK_RE = re.compile(r"\[[^\]]*\]\(([^)#\s]+)(?:#[^)]*)?\)")
APPL_RE = re.compile(r"^application.*\.(yml|yaml|properties)$")


def check_md_links(doc_path: str) -> None:
    if not os.path.isfile(doc_path):
        fail(f"文档索引缺失：{rel(doc_path)}")
        return
    base_dir = os.path.dirname(doc_path)
    text = read_text(doc_path)
    for m in MD_LINK_RE.finditer(text):
        target = m.group(1)
        if target.startswith(("http://", "https://", "mailto:")):
            continue
        # 代码/占位链接跳过
        if "<" in target or "{" in target:
            continue
        resolved = os.path.normpath(os.path.join(base_dir, target))
        if not os.path.exists(resolved):
            fail(f"{rel(doc_path)}：死链 {target}（解析至 {rel(resolved)} 不存在）")


def main() -> int:
    # D1 模块白名单
    on_disk = list_modules_on_disk()
    if on_disk != sorted(MODULES_EXPECTED):
        extra = sorted(set(on_disk) - set(MODULES_EXPECTED))
        missing = sorted(set(MODULES_EXPECTED) - set(on_disk))
        fail(f"模块清单漂移：多余 {extra}，缺失 {missing}")

    # D2/D3 启动类与配置归属
    spring_boot_files = []
    for path in walk_files(BACKEND_ROOT, (".java",)):
        text = read_text(path)
        if "@SpringBootApplication" in text and "/src/main/" in path.replace("\\", "/"):
            spring_boot_files.append(path)
    for path in spring_boot_files:
        if "scaffold-admin" not in path.replace("\\", "/"):
            fail(f"{rel(path)}：启动类只允许位于 scaffold-admin")

    for path in walk_files(BACKEND_ROOT, (".yml", ".yaml", ".properties")):
        fname = os.path.basename(path)
        if APPL_RE.match(fname) and "/src/main/resources/" in path.replace("\\", "/"):
            if "scaffold-admin" not in path.replace("\\", "/"):
                fail(f"{rel(path)}：application 配置只允许位于 scaffold-admin（D3）")

    # D4 前端视图归位（视图必须位于 views/；layout 壳、根 App.vue 与 components/micro-apps 除外）
    views_root = os.path.join(FRONTEND_ROOT, "src", "views")
    for path in walk_files(FRONTEND_ROOT, (".vue",), skip_dirs=(".git", "node_modules", "dist")):
        norm = path.replace("\\", "/")
        if "/src/components/" in norm or "/src/micro-apps/" in norm or "/src/layout/" in norm:
            continue
        if norm.endswith("src/App.vue"):
            continue
        if not norm.startswith(views_root.replace("\\", "/")):
            fail(f"{rel(path)}：视图 .vue 必须位于 src/views/ 下（D4）")

    # D5 路由动态 import 目标
    router_path = os.path.join(FRONTEND_ROOT, "src", "router", "index.js")
    if os.path.isfile(router_path):
        router_text = read_text(router_path)
        for m in re.finditer(r"import\(\s*['\"]([^'\"]+)['\"]\s*\)", router_text):
            target = m.group(1)
            if target.startswith("@/"):
                rel_target = target[2:]
                resolved = os.path.join(FRONTEND_ROOT, "src", rel_target)
                if not os.path.exists(resolved):
                    fail(f"src/router/index.js：动态 import 目标不存在（{target}）")
            elif not target.startswith(("views/", "layout/", "../")):
                fail(f"src/router/index.js：动态 import 未指向 views/layout（{target}）")
    else:
        fail("前端路由文件缺失：src/router/index.js")

    # D6 文档死链
    for doc in DOC_INDEX_FILES:
        if os.path.basename(doc) == "CHANGELOG.md":
            # CHANGELOG 不承担索引职责，仅要求存在（存在性由 check-version 管控）
            continue
        check_md_links(doc)

    # D7 门禁脚本齐备
    for s in GATE_SCRIPTS:
        p = os.path.join(BACKEND_ROOT, "scripts", s)
        if not os.path.isfile(p):
            fail(f"门禁脚本缺失：scripts/{s}")

    info(f"模块 {len(on_disk)} 个；文档索引 {len(DOC_INDEX_FILES)} 份；门禁脚本 {len(GATE_SCRIPTS)} 个")
    return finish("check-directory")


if __name__ == "__main__":
    sys.exit(main())
