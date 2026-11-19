#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-version.py：版本一致性门禁（V1.1.0 新增，作为版本治理的唯一判据）。

检查项：
V1 后端根 POM project version 与全部 14 个模块 parent version 一致；
V2 模块间依赖不得硬编码与根 POM 不同的版本；
V3 前端 package.json version 与后端根 POM version 一致；
V4 仓库根 CHANGELOG.md 存在且包含当前版本条目；
V5 文档中的可运行 jar 版本引用与 POM version 一致（scaffold-admin-<V>.jar）。

退出码：0 通过；1 存在违规（阻断）。
"""
from __future__ import annotations

import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import (  # noqa: E402
    BACKEND_ROOT, REPO_ROOT, MODULES_EXPECTED, fail, finish, info,
    module_poms, parse_pom, read_text, rel,
)

# 手工运维版本豁免：允许出现在文档/代码中但不受 V5 管控的版本串
DOC_JAR_FILES = [
    os.path.join(BACKEND_ROOT, "README.md"),
    os.path.join(BACKEND_ROOT, "docs", "USAGE.md"),
]

CHANGELOG_PATH = os.path.join(REPO_ROOT, "CHANGELOG.md")


def main() -> int:
    root_pom = parse_pom(os.path.join(BACKEND_ROOT, "pom.xml"))
    version = root_pom["project_version"]
    if not version:
        fail("根 POM 未解析到 project version")
        return finish("check-version")
    info(f"权威版本（根 POM）：{version}")

    # V1 模块 parent version 一致（接受 ${project.version} 继承写法）
    INHERIT = {"${project.version}", "${revision}"}
    for name, pom_path in module_poms().items():
        p = parse_pom(pom_path)
        if p["parent_artifact"] != "java-admin-framework":
            fail(f"{name}/pom.xml parent artifactId 应为 java-admin-framework")
        elif p["parent_version"] not in INHERIT and p["parent_version"] != version:
            fail(f"{name}/pom.xml parent version={p['parent_version']} ≠ 根 POM {version}")
        if p["project_version"] not in (None, version) and p["project_version"] not in INHERIT:
            # 子模块不允许自带与根不同的 project version（应省略或用 ${project.version} 继承）
            fail(f"{name}/pom.xml 自身 project version={p['project_version']} 与根 POM 不一致（应省略以继承）")

    # V2 模块间依赖不得硬编码错误版本（允许省略 version / ${project.version}；若写死必须等于根版本）
    dep_ver_re = re.compile(
        r"<dependency>\s*<groupId>com\.acme\.scaffold</groupId>\s*"
        r"<artifactId>(scaffold-[a-z]+)</artifactId>\s*<version>([^<]+)</version>", re.S)
    for name, pom_path in module_poms().items():
        text = read_text(pom_path)
        for m in dep_ver_re.finditer(text):
            aid, ver = m.group(1), m.group(2)
            if ver not in INHERIT and ver != version:
                fail(f"{name}/pom.xml 内部依赖 {aid} 硬编码 version={ver} ≠ {version}（建议省略 version）")

    # V3 前端版本一致
    pkg_path = os.path.join(REPO_ROOT, "pc-admin-vue3-vite", "package.json")
    pkg = json.loads(read_text(pkg_path))
    if pkg.get("version") != version:
        fail(f"package.json version={pkg.get('version')} ≠ 根 POM {version}")

    # V4 CHANGELOG 存在且含当前版本
    if not os.path.isfile(CHANGELOG_PATH):
        fail(f"仓库根缺少 CHANGELOG.md（{rel(CHANGELOG_PATH)}）")
    else:
        changelog = read_text(CHANGELOG_PATH)
        if not re.search(rf"^#+\s*\[?{re.escape(version)}\]?", changelog, re.M):
            fail(f"CHANGELOG.md 未包含版本 {version} 的条目")

    # V5 文档 jar 版本引用
    for doc in DOC_JAR_FILES:
        if not os.path.isfile(doc):
            continue
        text = read_text(doc)
        for m in re.finditer(r"scaffold-admin-([\d.]+)\.jar", text):
            if m.group(1) != version:
                fail(f"{rel(doc)} 引用 scaffold-admin-{m.group(1)}.jar ≠ 当前版本 {version}")

    return finish("check-version")


if __name__ == "__main__":
    sys.exit(main())
