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

# 类级 @RequestMapping 支持单字符串与数组两种写法：
#   @RequestMapping("/api/a")   与   @RequestMapping({"/api/a", "/api/b"})
# 分别提取所有字符串字面量，避免数组形式被漏扫导致前端调用误报 frontend_only。
CLASS_BASE_RE = re.compile(r"@RequestMapping\s*\(([^)]*)\)")
PATH_LITERAL_RE = re.compile(r"[\"']([^\"']+)[\"']")
# 捕获 HTTP 方法，避免 GET/POST 共享同一类级路径时被误判为重复注册
METHOD_MAP_RE = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:value\s*=\s*)?[\"']([^\"']*)[\"'])?")
FRONT_URL_RE = re.compile(r"[\"'](/api/[A-Za-z0-9_\-/${}:.]*)[\"']")

norm = lambda p: re.sub(r"\{[^}]+\}", "{}", p.rstrip("/"))  # noqa: E731


def class_bases(text: str) -> list:
    """提取类级 @RequestMapping 的所有 base 路径（兼容数组形式）。"""
    m = CLASS_BASE_RE.search(text)
    if not m:
        return [""]
    paths = PATH_LITERAL_RE.findall(m.group(1))
    # 排除 value=/path= 等属性名被误当路径（保留以 / 开头的真实路径）
    paths = [p for p in paths if p.startswith("/")]
    return paths or [""]


def backend_routes() -> dict:
    """返回 {(HTTP_METHOD, normalized_path): [Controller]}。"""
    routes = {}
    for path in walk_files(BACKEND_ROOT, (".java",)):
        # 代码生成器模板中的 @RequestMapping("%4$s") 是字符串模板，不是真实路由
        if path.replace("\\", "/").endswith("/CodeTemplates.java"):
            continue
        text = read_text(path)
        # Controller 可能位于 api/ 或 controller/ 目录（项目已迁到 api/ 为主）；
        # 以 Spring 注解为准扫描，避免按目录名过滤漏掉 api/ 目录下的控制器。
        if "@RestController" not in text and "@Controller" not in text:
            continue
        bases = class_bases(text)
        cls = os.path.splitext(os.path.basename(path))[0]
        for m in METHOD_MAP_RE.finditer(text):
            method = m.group(1).upper()
            sub = m.group(2) or ""
            for base in bases:
                full = (base + sub) if sub.startswith("/") else (base + ("/" + sub if sub else ""))
                key = (method, norm(full))
                routes.setdefault(key, []).append(cls)
    return routes


def frontend_urls() -> dict:
    urls = {}
    for path in walk_files(FRONTEND_ROOT, (".js",), skip_dirs=(".git", "node_modules", "dist")):
        if "/src/api/" not in path.replace("\\", "/"):
            continue
        text = read_text(path)
        # 注释里的 URL 示例（如 "// GET /api/system/files/page -> ..."）不是真实请求，先剥离注释再扫描，
        # 否则类级前缀 "/api/system/files" 会被误当独立端点而报 frontend_only。
        text = re.sub(r"//[^\n]*", "", text)
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        for m in FRONT_URL_RE.finditer(text):
            raw = m.group(1)
            # 字符串以 '/' 结尾且紧接 '+' 拼接变量（如 '/api/system/files/download/' + id），
            # 是动态路径段，补 {} 与后端 @GetMapping("/download/{id}") 的 norm 结果对齐。
            tail = text[m.end():m.end() + 12]
            if raw.endswith("/") and re.match(r"\s*\+\s*[A-Za-z_$]", tail):
                u = norm(raw + "{}")
            else:
                u = norm(raw)
            urls.setdefault(u, os.path.relpath(path, FRONTEND_ROOT))
    return urls


def main() -> int:
    strict = "--strict" in sys.argv
    be = backend_routes()
    fe = frontend_urls()

    be_paths = {path for _, path in be}
    dupes = {k: v for k, v in be.items() if len(v) > 1}
    fe_only = sorted(u for u in fe if u not in be_paths)
    be_only = sorted(path for path in be_paths if path not in fe)

    for (method, u), classes in sorted(dupes.items()):
        print(f"[WARN] duplicate_backend: {method} {u} 注册于 {classes}")
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
