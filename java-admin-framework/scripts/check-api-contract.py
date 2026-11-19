#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-api-contract.py：前后端契约扫描（信息性检查，重刻版）。

行为：
- 扫描 14 个 scaffold-*/src/main/java 的 @RequestMapping/@*Mapping 拼装后端路由；
- 扫描前端 src/api/*.js 中的 '/api/...' 请求路径；
- 输出三类差异：frontend_only（前端调用但后端未见）、backend_only（信息性，不必处置）、
  duplicate_backend（后端同路径同方法重复注册）；
- 默认不阻断；--strict 时 frontend_only 非空返回 1。

退出码：0 通过（或 strict 下无 frontend_only）；1 strict 且有 frontend_only。
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import BACKEND_ROOT, FRONTEND_ROOT, info, walk_files, read_text  # noqa: E402

CLASS_BASE_RE = re.compile(r"@RequestMapping\(\s*(?:value\s*=\s*)?[\"']([^\"']+)[\"']")
METHOD_MAP_RE = re.compile(r"@(?:Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:value\s*=\s*)?[\"']([^\"']*)[\"'])?")
FRONT_URL_RE = re.compile(r"[\"'](/api/[A-Za-z0-9_\-/${}:.]*)[\"']")

norm = lambda p: re.sub(r"\{[^}]+\}", "{}", p.rstrip("/"))  # noqa: E731


def backend_routes() -> dict:
    """返回 {normalized_path: [Controller#ctx]}。"""
    routes = {}
    for path in walk_files(BACKEND_ROOT, (".java",)):
        if "/controller/" not in path.replace("\\", "/"):
            continue
        text = read_text(path)
        base_m = CLASS_BASE_RE.search(text)
        base = base_m.group(1) if base_m else ""
        cls = os.path.splitext(os.path.basename(path))[0]
        for m in METHOD_MAP_RE.finditer(text):
            sub = m.group(1) or ""
            full = (base + sub) if sub.startswith("/") else (base + ("/" + sub if sub else ""))
            routes.setdefault(norm(full), []).append(cls)
    return routes


def frontend_urls() -> dict:
    urls = {}
    for path in walk_files(FRONTEND_ROOT, (".js",), skip_dirs=(".git", "node_modules", "dist")):
        if "/src/api/" not in path.replace("\\", "/"):
            continue
        for m in FRONT_URL_RE.finditer(read_text(path)):
            u = norm(m.group(1))
            urls.setdefault(u, os.path.relpath(path, FRONTEND_ROOT))
    return urls


def main() -> int:
    strict = "--strict" in sys.argv
    be = backend_routes()
    fe = frontend_urls()

    dupes = {k: v for k, v in be.items() if len(v) > 1}
    fe_only = sorted(u for u in fe if u not in be)
    be_only = sorted(u for u in be if u not in fe)

    for u, classes in sorted(dupes.items()):
        print(f"[WARN] duplicate_backend: {u} 注册于 {classes}")
    for u in fe_only:
        print(f"[WARN] frontend_only: {u}（{fe[u]}）→ 后端未见同形路由")
    for u in be_only:
        print(f"[INFO] backend_only: {u}")

    info(f"后端路由 {len(be)} 条；前端引用 {len(fe)} 条；frontend_only {len(fe_only)}；duplicate {len(dupes)}")
    if strict and fe_only:
        print("[check-api-contract] EXIT=1（strict：存在 frontend_only）")
        return 1
    print("[check-api-contract] EXIT=0（信息性，默认不阻断）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
