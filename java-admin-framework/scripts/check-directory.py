#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""目录约定校验器（零依赖，仅标准库）。

把 ``docs/DIRECTORY.md`` 的关键约定编码成可执行守护，防止目录结构在后续迭代中漂移：

  D1. 按模块登记顶层包根；新增包根 / 新增模块必须同步登记白名单与 ``docs/MODULES.md``。
  D2. 仅 ``scaffold-admin`` 可在 ``com.acme.scaffold`` 根级放启动类。
  D3. 仅 ``scaffold-admin`` 可持有运行期配置（application*.yml / logback*.xml）。
  D4. 前端 ``src/`` 根不得放 CSS；全局样式集中在 ``src/styles/`` 且三份文件齐全。
  D5. ``common/constant/CommonConstants.java`` 必须存在（跨模块常量单一事实来源）。
  D6. 三份规范文档齐全，且 ``docs/README.md`` 索引引用齐全（防死链）。
  D7. 子模块 POM 的 ``<dependencies>`` 不得写具体版本号，版本统一在父 POM 收敛。
  D8. ``src/main/java`` 一级目录白名单：只允许 ``com``；admin 例外允许 ``db``
      （Flyway Java 迁移 ``db/migration/V57__backfill_tenant_id.java``）。
  D9. ``src/test/java`` 包根须与 main 白名单对齐；admin 集成测试的 9 个包根与
      ``BaseIntegrationTest.java`` 根级文件按历史例外登记。
  D10. ``docs/`` 下所有文档须被 ``docs/README.md`` 索引，且索引里的相对链接目标存在。
  D11. ``src/test/resources`` 仅 admin 可持 ``application*.yml`` / ``logback*.xml``。

本脚本只读：不修改任何文件，不参与编译与运行时，不替代 ``check-conventions.py``
（后者负责包级环 / 重复 FQN / 依赖方向 / 数据类 / 模块 DAG）。

运行::

    python3 scripts/check-directory.py
    python3 scripts/check-directory.py --backend <dir> --frontend <dir>   # 单测用

退出码：0 = 全部通过；1 = 存在硬性违规；2 = 工程路径不存在。
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT_PKG_PARTS = ("com", "acme", "scaffold")
MODULE_PREFIX = "scaffold-"
ADMIN_MODULE = "scaffold-admin"
COMMON_MODULE = "scaffold-common"
STYLE_FILES = ("base.css", "theme-tokens.css", "element-theme.css")
REQUIRED_DOCS = ("DIRECTORY.md", "RUOYI-ALIGNMENT.md", "MODULES.md")
REQUIRED_INDEX_LINKS = ("DIRECTORY.md", "RUOYI-ALIGNMENT.md")
CONFIG_FILE_RE = re.compile(r"^(application.*\.(yml|yaml|properties)|logback.*\.xml)$")
DOC_FILE_RE = re.compile(r"\.(md|html)$", re.IGNORECASE)
MD_LINK_RE = re.compile(r"\[[^\]]*\]\(([^)]+)\)")

# 按模块登记的顶层包根（磁盘现状，2026-11-08 结构整合后取证，14 个模块）。
# 历史例外：common 含 jooq；framework 无同名包根，类分布在
# config/observability/monitor/chaos；admin 的 JacksonConfig 位于 config 包（跨模块同包）。
# 2026-11-08 整合：plugin -> common、approval -> workflow、ai -> codegen，
# 类包路径保持原样，因此这三个包根分别登记到接收模块（不改变任何 FQN）。
# 新增模块或包根时：先改 docs/MODULES.md 与 docs/DIRECTORY.md，再在此登记，否则本校验阻断。
EXPECTED_ROOTS = {
    "scaffold-admin": {"admin", "config"},
    "scaffold-cms": {"cms"},
    "scaffold-codegen": {"ai", "codegen"},
    "scaffold-common": {"common", "jooq", "plugin"},
    "scaffold-framework": {"chaos", "config", "monitor", "observability"},
    "scaffold-im": {"im"},
    "scaffold-job": {"job"},
    "scaffold-kafka": {"kafka"},
    "scaffold-monitor": {"monitor"},
    "scaffold-realtime": {"realtime"},
    "scaffold-security": {"security"},
    "scaffold-system": {"system"},
    "scaffold-workbench": {"workbench"},
    "scaffold-workflow": {"approval", "workflow"},
}

# D8：src/main/java 一级目录。仅 admin 的 db 为合法例外（Flyway Java 迁移，
# 该文件必须落在 db/migration 才能被 Flyway 按类名发现，不能搬进 com.acme.scaffold）。
MAIN_ROOTS_ALLOWED = {"com"}
MAIN_ROOT_EXCEPTIONS = {ADMIN_MODULE: {"db"}}

# D9：src/test/java 包根默认复用 EXPECTED_ROOTS；admin 承载跨模块集成/E2E 测试，
# 磁盘实证的 9 个包根 + 根级 BaseIntegrationTest.java 按历史例外登记。
TEST_ROOT_EXCEPTIONS = {
    ADMIN_MODULE: {
        "admin", "config", "consistency", "e2e", "integration",
        "perf", "security", "tools", "workflow",
    },
}
TEST_ROOT_JAVA_ALLOWED = {ADMIN_MODULE: {"BaseIntegrationTest.java"}}


def default_paths():
    """默认定位：脚本位于 <repo>/java-admin-framework/scripts/，前端与 java-admin-framework 同级。"""
    backend = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    frontend = os.path.join(os.path.dirname(backend), "pc-admin-vue3-vite")
    return backend, frontend


def iter_modules(backend):
    return sorted(
        n for n in os.listdir(backend)
        if n.startswith(MODULE_PREFIX) and os.path.isdir(os.path.join(backend, n))
    )


def pkg_root_of(backend, module):
    return os.path.join(backend, module, "src", "main", "java", *ROOT_PKG_PARTS)


def _local(tag):
    return tag.split("}")[-1] if "}" in tag else tag


def _child_text(el, name):
    for c in el:
        if _local(c.tag) == name:
            return (c.text or "").strip()
    return ""


def check_package_roots(backend, modules, errors):
    """D1/D2：包根白名单 + 启动类位置。只看目录名，不读 Java 源码，避免大仓扫描超时。"""
    for m in modules:
        expected = EXPECTED_ROOTS.get(m)
        if expected is None:
            errors.append(
                f"D1 模块未登记: {m}（新增模块须同步 docs/MODULES.md、docs/DIRECTORY.md "
                f"与 check-directory.py 的 EXPECTED_ROOTS）"
            )
            continue
        pkg_root = pkg_root_of(backend, m)
        if not os.path.isdir(pkg_root):
            continue
        actual = {
            n for n in os.listdir(pkg_root)
            if os.path.isdir(os.path.join(pkg_root, n))
        }
        for extra in sorted(actual - expected):
            errors.append(f"D1 未登记顶层包根: {m} -> com.acme.scaffold.{extra}")
        root_java = sorted(n for n in os.listdir(pkg_root) if n.endswith(".java"))
        if root_java and m != ADMIN_MODULE:
            errors.append(
                f"D2 非启动模块在 com.acme.scaffold 根级放置 Java: {m} -> {', '.join(root_java)}"
            )


def check_runtime_config(backend, modules, errors):
    """D3：运行期配置只属于唯一可部署模块。"""
    for m in modules:
        if m == ADMIN_MODULE:
            continue
        res = os.path.join(backend, m, "src", "main", "resources")
        if not os.path.isdir(res):
            continue
        hits = sorted(n for n in os.listdir(res) if CONFIG_FILE_RE.match(n))
        if hits:
            errors.append(f"D3 非启动模块持有运行期配置: {m} -> {', '.join(hits)}")


def check_frontend(frontend, errors):
    """D4：全局样式集中在 src/styles/，src 根不再散落 CSS。"""
    src = os.path.join(frontend, "src")
    if not os.path.isdir(src):
        errors.append("D4 前端 src/ 目录不存在")
        return
    root_css = sorted(n for n in os.listdir(src) if n.endswith(".css"))
    if root_css:
        errors.append(f"D4 前端 src/ 根级散落 CSS: {', '.join(root_css)}（应放 src/styles/）")
    styles = os.path.join(src, "styles")
    for f in STYLE_FILES:
        if not os.path.isfile(os.path.join(styles, f)):
            errors.append(f"D4 缺少全局样式文件: src/styles/{f}")


def check_common_constants(backend, errors):
    """D5：跨模块常量单一事实来源不得被删除或改名。"""
    path = os.path.join(
        backend, COMMON_MODULE, "src", "main", "java", *ROOT_PKG_PARTS,
        "common", "constant", "CommonConstants.java",
    )
    if not os.path.isfile(path):
        errors.append("D5 缺少 common/constant/CommonConstants.java（跨模块常量单一事实来源）")


def check_docs(backend, errors):
    """D6：规范文档齐全且索引不死链。"""
    docs = os.path.join(backend, "docs")
    for d in REQUIRED_DOCS:
        if not os.path.isfile(os.path.join(docs, d)):
            errors.append(f"D6 缺少规范文档: docs/{d}")
    index = os.path.join(docs, "README.md")
    if not os.path.isfile(index):
        errors.append("D6 缺少文档索引: docs/README.md")
        return
    with open(index, encoding="utf-8", errors="ignore") as fh:
        text = fh.read()
    for link in REQUIRED_INDEX_LINKS:
        if link not in text:
            errors.append(f"D6 docs/README.md 索引缺少引用: {link}（疑似死链或文档漏挂）")


def check_child_pom_versions(backend, modules, errors):
    """D7：子模块不写死第三方版本，统一交给父 POM dependencyManagement。"""
    for m in modules:
        pom = os.path.join(backend, m, "pom.xml")
        if not os.path.isfile(pom):
            continue
        try:
            root = ET.parse(pom).getroot()
        except ET.ParseError as exc:
            errors.append(f"D7 子模块 POM 解析失败: {m} -> {exc}")
            continue
        deps = next((c for c in root if _local(c.tag) == "dependencies"), None)
        if deps is None:
            continue
        for d in deps:
            if _local(d.tag) != "dependency":
                continue
            version = _child_text(d, "version")
            if version and not version.startswith("${"):
                errors.append(
                    f"D7 子模块写死依赖版本: {m} -> "
                    f"{_child_text(d, 'groupId')}:{_child_text(d, 'artifactId')} = {version}"
                    "（版本统一放父 POM dependencyManagement）"
                )


def check_main_java_roots(backend, modules, errors):
    """D8：main 源码根一级目录白名单，堵住逃逸出 com.acme.scaffold 的源码包。"""
    for m in modules:
        java = os.path.join(backend, m, "src", "main", "java")
        if not os.path.isdir(java):
            continue
        allowed = MAIN_ROOTS_ALLOWED | MAIN_ROOT_EXCEPTIONS.get(m, set())
        actual = {n for n in os.listdir(java) if os.path.isdir(os.path.join(java, n))}
        for extra in sorted(actual - allowed):
            errors.append(
                f"D8 未登记 src/main/java 一级目录: {m} -> {extra} "
                "(新增源码根须同步 docs/DIRECTORY.md 与 MAIN_ROOT_EXCEPTIONS)"
            )
        stray = sorted(n for n in os.listdir(java) if n.endswith(".java"))
        if stray:
            errors.append(f"D8 src/main/java 根级散落 Java: {m} -> {', '.join(stray)}")


def check_test_package_roots(backend, modules, errors):
    """D9：测试包根与 main 白名单对齐，防止测试树悄悄长出新的一级包。"""
    for m in modules:
        expected = EXPECTED_ROOTS.get(m)
        if expected is None:
            continue  # 未登记模块由 D1 报告，避免重复噪声
        root = os.path.join(backend, m, "src", "test", "java", *ROOT_PKG_PARTS)
        if not os.path.isdir(root):
            continue
        allowed = set(expected) | TEST_ROOT_EXCEPTIONS.get(m, set())
        actual = {n for n in os.listdir(root) if os.path.isdir(os.path.join(root, n))}
        for extra in sorted(actual - allowed):
            errors.append(f"D9 未登记测试包根: {m} -> com.acme.scaffold.{extra}")
        allowed_java = TEST_ROOT_JAVA_ALLOWED.get(m, set())
        for f in sorted(n for n in os.listdir(root) if n.endswith(".java")):
            if f not in allowed_java:
                errors.append(f"D9 测试源码根级散落 Java: {m} -> {f}")


def check_docs_index(backend, errors):
    """D10：docs/ 下每份文档都要挂进总索引，且索引里的相对链接不能是死链。"""
    docs = os.path.join(backend, "docs")
    index = os.path.join(docs, "README.md")
    if not os.path.isdir(docs) or not os.path.isfile(index):
        return  # 缺失由 D6 报告
    with open(index, encoding="utf-8", errors="ignore") as fh:
        text = fh.read()
    for dp, _, fs in os.walk(docs):
        for f in sorted(fs):
            if not DOC_FILE_RE.search(f):
                continue
            rel = os.path.relpath(os.path.join(dp, f), docs).replace(os.sep, "/")
            if rel == "README.md":
                continue
            if f not in text and rel not in text:
                errors.append(f"D10 docs/README.md 索引漏挂文档: {rel}")
    for target in MD_LINK_RE.findall(text):
        clean = target.strip().split("#")[0].strip()
        if not clean or clean.startswith(("http://", "https://", "mailto:", "//")):
            continue
        if not os.path.isfile(os.path.join(docs, clean)):
            errors.append(f"D10 docs/README.md 索引死链: {target}")


def check_test_resources(backend, modules, errors):
    """D11：测试期运行配置只属于 admin；其它模块仅可放 schema / 夹具等非配置文件。"""
    for m in modules:
        if m == ADMIN_MODULE:
            continue
        res = os.path.join(backend, m, "src", "test", "resources")
        if not os.path.isdir(res):
            continue
        hits = sorted(n for n in os.listdir(res) if CONFIG_FILE_RE.match(n))
        if hits:
            errors.append(f"D11 非启动模块持有测试期运行配置: {m} -> {', '.join(hits)}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="目录约定校验（docs/DIRECTORY.md 的可执行守护）")
    parser.add_argument("--backend", default=None, help="java-admin-framework 根目录")
    parser.add_argument("--frontend", default=None, help="pc-admin-vue3-vite 根目录")
    args = parser.parse_args(argv)

    def_backend, def_frontend = default_paths()
    backend = args.backend or def_backend
    frontend = args.frontend or def_frontend
    if not os.path.isdir(backend) or not os.path.isdir(frontend):
        print(
            f"[ERROR] 工程路径不存在: backend={backend} frontend={frontend}",
            file=sys.stderr,
        )
        return 2

    modules = iter_modules(backend)
    errors = []
    check_package_roots(backend, modules, errors)
    check_runtime_config(backend, modules, errors)
    check_frontend(frontend, errors)
    check_common_constants(backend, errors)
    check_docs(backend, errors)
    check_child_pom_versions(backend, modules, errors)
    check_main_java_roots(backend, modules, errors)
    check_test_package_roots(backend, modules, errors)
    check_docs_index(backend, errors)
    check_test_resources(backend, modules, errors)

    root_count = len({r for rs in EXPECTED_ROOTS.values() for r in rs})
    print("=" * 64)
    print("目录约定校验（D1–D11）")
    print("=" * 64)
    print(f"后端模块: {len(modules)} 个；登记白名单模块: {len(EXPECTED_ROOTS)} 个；顶层包根: {root_count} 种")
    print(f"前端样式: src/styles/{{{', '.join(STYLE_FILES)}}}")
    print(f"规范文档: {', '.join(REQUIRED_DOCS)}")
    if errors:
        print(f"\n未通过，共 {len(errors)} 项硬性违规：")
        for e in errors:
            print(f"  - {e}")
        print("\n修复指引见 docs/DIRECTORY.md；确需新增包根/模块时同步更新白名单与文档。")
        return 1
    print("\n目录约定校验通过（EXIT=0）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
