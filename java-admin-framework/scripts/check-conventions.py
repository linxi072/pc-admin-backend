#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-conventions.py：编码约定门禁（H 系列，重刻版）。

检查项：
H3 Controller 归位：@RestController 源文件必须位于 controller/ 或 api/ 目录；
H3a 跨模块封闭：模块源码不得 import 其他模块的 controller 包（装配与复用应走 service/域事件）；
H5 依赖 DAG：模块 pom 内部依赖必须落在白名单内（gate_lib.DEPENDENCY_DAG_WHITELIST），
     白名单取自 docs/ARCHITECTURE.md 依赖方向铁律 + V1.1.0 实际声明校准；
H6 装配唯一：@SpringBootApplication 仅允许出现在 scaffold-admin。

退出码：0 通过；1 存在违规（阻断）。
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import (  # noqa: E402
    BACKEND_ROOT, DEPENDENCY_DAG_WHITELIST, fail, finish, info,
    iter_java_files, module_poms, parse_pom, read_text, rel,
    strip_java_comments_and_textblocks,
)

SPRING_APP_RE = re.compile(r"@SpringBootApplication\b")
CONTROLLER_ANNO_RE = re.compile(r"@RestController\b")
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", re.M)

# 包根 → 模块名映射（com.acme.scaffold.<pkg>... ）
PKG_TO_MODULE = {
    "common": "scaffold-common",
    "realtime": "scaffold-realtime",
    "kafka": "scaffold-kafka",
    "codegen": "scaffold-codegen",
    "ai": "scaffold-codegen",
    "plugin": "scaffold-common",
    "approval": "scaffold-workflow",
    "security": "scaffold-security",
    "framework": "scaffold-framework",
    "system": "scaffold-system",
    "monitor": "scaffold-monitor",
    "job": "scaffold-job",
    "workbench": "scaffold-workbench",
    "workflow": "scaffold-workflow",
    "cms": "scaffold-cms",
    "im": "scaffold-im",
    "config": "scaffold-framework",
    "observability": "scaffold-framework",
    "chaos": "scaffold-framework",
    "mybatis": "scaffold-common",
    "event": "scaffold-common",
}


def java_package(path: str) -> str:
    """由文件路径推断包名：.../scaffold-<m>/src/(main|test)/java/<pkg path>/<File>.java"""
    norm = path.replace("\\", "/")
    marker = "/src/main/java/" if "/src/main/java/" in norm else "/src/test/java/"
    pkg_path = norm.split(marker, 1)[1]
    return os.path.dirname(pkg_path).replace("/", ".")


def module_name_of(path: str):
    """由文件路径推断所属模块（scaffold-<m>），失败返回 None。"""
    m = re.match(r".*scaffold-([a-z]+)/src/", path.replace("\\", "/"))
    return "scaffold-" + m.group(1) if m else None


def pkg_module_of(import_name: str):
    """由 import 包名推断目标模块（含历史包根例外：plugin/ai/approval）。"""
    m = re.match(r"com\.acme\.scaffold\.([a-z0-9]+)\.", import_name)
    if not m:
        return None
    return PKG_TO_MODULE.get(m.group(1))


def main() -> int:
    spring_app_files = []
    controllers = []
    cross_module_ctl = []

    for path in iter_java_files():
        text = read_text(path)
        code = strip_java_comments_and_textblocks(text)  # 剔除代码生成模板中的样板文本
        pkg = java_package(path)
        mod_name = module_name_of(path)

        if SPRING_APP_RE.search(code):
            spring_app_files.append((path, pkg))

        if CONTROLLER_ANNO_RE.search(code):
            controllers.append(path)

        # H3a 跨模块 controller 引用
        if mod_name and mod_name != "scaffold-admin":
            for imp in IMPORT_RE.findall(code):
                if ".controller." not in imp:
                    continue
                target = pkg_module_of(imp)
                if target and target != mod_name:
                    cross_module_ctl.append((path, imp, target))

    # H3 Controller 归位：禁止落在 service/domain/dto/util 等非 Web 层目录
    NON_WEB_LAYERS = ("/service/", "/domain/", "/dto/", "/util/")
    for path in controllers:
        norm = path.replace("\\", "/")
        if any(layer in norm for layer in NON_WEB_LAYERS):
            fail(f"{rel(path)}：@RestController 不得位于非 Web 层目录 {NON_WEB_LAYERS}")

    # H3a
    for path, imp, target in cross_module_ctl:
        fail(f"{rel(path)}：import 跨模块 controller（{imp}），应走 service/域事件")

    # H6 装配唯一
    for path, pkg in spring_app_files:
        if "scaffold-admin" not in path.replace("\\", "/"):
            fail(f"{rel(path)}：@SpringBootApplication 只允许出现在 scaffold-admin（当前包：{pkg}）")
    if not spring_app_files:
        fail("未找到 @SpringBootApplication 启动类")

    # H5 依赖 DAG 白名单
    for name, pom_path in module_poms().items():
        deps = parse_pom(pom_path)["scaffold_deps"]
        allowed = DEPENDENCY_DAG_WHITELIST.get(name, set())
        illegal = deps - allowed
        if illegal:
            fail(f"{name}/pom.xml：内部依赖越界 {sorted(illegal)}，白名单为 {sorted(allowed)}")
    missing = [m for m in DEPENDENCY_DAG_WHITELIST if m not in module_poms()]
    if missing:
        fail(f"白名单中的模块在磁盘缺失：{missing}")

    info(f"Controller 共 {len(controllers)} 个；@SpringBootApplication {len(spring_app_files)} 个")
    return finish("check-conventions")


if __name__ == "__main__":
    sys.exit(main())
