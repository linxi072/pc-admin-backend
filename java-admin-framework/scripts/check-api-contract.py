#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-api-contract.py：前后端契约扫描（信息性检查，重刻版）。

行为：
- 扫描 14 个 scaffold-*/src/main/java 的 @RequestMapping/@*Mapping 拼装后端路由；
- 扫描前端 src/api/*.js 中的 '/api/...' 请求路径（含反引号模板字符串中的 ${...} 动态段）；
- 在路径口径之外，进一步解析 request/requestRaw 对象字面量、axios sugar 与本地 POST helper
  的 HTTP 方法，按 (method, path) 比对，避免 GET 改写 POST 后契约门禁仍放行；
- 输出四类差异：frontend_only（前端调用但后端未见）、method_mismatch（后端有同形路径但无同方法）、
  backend_only（信息性，不必处置）、duplicate_backend（后端同路径同方法重复注册）；
- 默认不阻断；--strict 时 frontend_only 或 method_mismatch 非空返回 1。

退出码：0 通过（或 strict 下无 frontend_only / method_mismatch）；1 strict 且存在差异。
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
# 前端 URL 两种写法分开匹配：
#   1) 单/双引号字面量：'/api/system/users'（沿用原有口径）；
#   2) 反引号模板字符串：`/api/system/users/${id}`（此前漏扫，导致大量 backend_only 误报）。
# 模板中的 ${expr} 在 norm_frontend_url 中归一为 {}，与后端 @GetMapping("/{id}") 对齐。
FRONT_URL_RE = re.compile(r"[\"'](/api/[^\"'\s]*)[\"']")
FRONT_TEMPLATE_RE = re.compile(r"`(/api/[^`]*)`")
TEMPLATE_EXPR_RE = re.compile(r"\$\{[^}]*\}")

# 方法级契约：对象字面量中的 method: 'POST'、axios sugar（.post('/api/...')），
# 以及 api/approvalActions.js 这类本地 helper（函数体含 method，调用点只传 url）。
HTTP_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH"}
REQUEST_CALL_RE = re.compile(r"\b(?:request|requestRaw)\s*\(")
OBJECT_METHOD_RE = re.compile(r"\bmethod\s*:\s*[\"']([A-Za-z]+)[\"']", re.I)
SUGAR_CALL_RE = re.compile(
    r"\.\s*(get|post|put|delete|patch)\s*\(\s*([\"'`])(/api/[^\"'`]*)\2", re.I
)
HELPER_DEF_RE = re.compile(r"\bfunction\s+(\w+)\s*\(\s*url\b[^)]*\)\s*\{")

norm = lambda p: re.sub(r"\{[^}]+\}", "{}", p.rstrip("/"))  # noqa: E731


def norm_frontend_url(raw: str) -> str:
    """把前端 URL 归一到后端路由形状：${expr} → {}（含 encodeURIComponent(...) 等复合表达式）。"""
    return norm(TEMPLATE_EXPR_RE.sub("{}", raw))


def strip_comments(text: str) -> str:
    """剥离行注释与块注释，避免注释里的 URL 示例被当成真实请求。"""
    text = re.sub(r"//[^\n]*", "", text)
    return re.sub(r"/\*.*?\*/", "", text, flags=re.S)


def normalize_url_with_tail(raw: str, tail: str) -> str:
    """兼容 '/api/xxx/' + id 这类动态尾段，与后端 /xxx/{id} 对齐。"""
    if raw.endswith("/") and re.match(r"\s*\+\s*[A-Za-z_$]", tail):
        return norm_frontend_url(raw + "{}")
    return norm_frontend_url(raw)


def normalized_url(text: str, match: re.Match, raw: str) -> str:
    return normalize_url_with_tail(raw, text[match.end():match.end() + 12])


def iter_url_matches(text: str):
    for m in FRONT_URL_RE.finditer(text):
        yield m, m.group(1)
    for m in FRONT_TEMPLATE_RE.finditer(text):
        yield m, m.group(1)


def matching_brace(text: str, open_idx: int) -> int:
    """返回与 open_idx 处 '{' 配对的 '}' 下标；跳过字符串内的花括号。"""
    depth = 0
    quote = None
    escaped = False
    for i in range(open_idx, len(text)):
        ch = text[i]
        if quote:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            continue
        if ch in "'\"`":
            quote = ch
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
    return -1


def helper_methods(text: str) -> dict:
    """发现本地 helper 的方法：函数签名含 url 参数、函数体含 method: 'X'。"""
    helpers: dict[str, str] = {}
    for m in HELPER_DEF_RE.finditer(text):
        open_idx = text.find("{", m.start())
        if open_idx < 0:
            continue
        end = matching_brace(text, open_idx)
        if end < 0:
            continue
        method_m = OBJECT_METHOD_RE.search(text[open_idx + 1:end])
        if not method_m:
            continue
        method = method_m.group(1).upper()
        if method in HTTP_METHODS:
            helpers[m.group(1)] = method
    return helpers


def frontend_calls_from_text(text: str, rel_path: str = "sample.js") -> dict:
    """解析单个前端源码片段，返回 {(METHOD, normalized_path): file}。"""
    calls: dict[tuple[str, str], str] = {}
    text = strip_comments(text)

    for call in REQUEST_CALL_RE.finditer(text):
        brace = call.end()
        while brace < len(text) and text[brace].isspace():
            brace += 1
        if brace >= len(text) or text[brace] != "{":
            continue
        end = matching_brace(text, brace)
        if end < 0:
            continue
        body = text[brace + 1:end]
        method_m = OBJECT_METHOD_RE.search(body)
        if not method_m:
            continue
        method = method_m.group(1).upper()
        if method not in HTTP_METHODS:
            continue
        for m, raw in iter_url_matches(body):
            key = (method, normalized_url(body, m, raw))
            calls.setdefault(key, rel_path)

    for m in SUGAR_CALL_RE.finditer(text):
        method = m.group(1).upper()
        raw = normalize_url_with_tail(m.group(3), text[m.end():m.end() + 12])
        calls.setdefault((method, raw), rel_path)

    for name, method in helper_methods(text).items():
        call_re = re.compile(r"\b" + re.escape(name) + r"\s*\(\s*([\"'`])(/api/[^\"'`]*)\1")
        for m in call_re.finditer(text):
            calls.setdefault((method, norm_frontend_url(m.group(2))), rel_path)
    return calls


def frontend_calls() -> dict:
    """扫描真实前端 API 源码，返回方法级调用集合。"""
    calls: dict[tuple[str, str], str] = {}
    for path in walk_files(FRONTEND_ROOT, (".js",), skip_dirs=(".git", "node_modules", "dist")):
        norm_path = path.replace("\\", "/")
        if "/src/api/" not in norm_path or "/src/api/tests/" in norm_path:
            continue
        rel = os.path.relpath(path, FRONTEND_ROOT)
        for key, source in frontend_calls_from_text(read_text(path), rel).items():
            calls.setdefault(key, source)
    return calls


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
        norm_path = path.replace("\\", "/")
        # src/api/tests/ 下为单测契约断言（含 r-1 / pi-1 等假 ID），不是真实 API 定义，必须排除，
        # 否则模板串支持开启后会把这些夹具值误报为 frontend_only 并破坏 --strict 门禁。
        if "/src/api/" not in norm_path or "/src/api/tests/" in norm_path:
            continue
        # 注释里的 URL 示例（如 "// GET /api/system/files/page -> ..."）不是真实请求，先剥离注释再扫描，
        # 否则类级前缀 "/api/system/files" 会被误当独立端点而报 frontend_only。
        text = strip_comments(read_text(path))
        for m, raw in iter_url_matches(text):
            u = normalized_url(text, m, raw)
            urls.setdefault(u, os.path.relpath(path, FRONTEND_ROOT))
    return urls


def main() -> int:
    strict = "--strict" in sys.argv
    be = backend_routes()
    fe = frontend_urls()
    fe_calls = frontend_calls()

    be_paths = {path for _, path in be}
    dupes = {k: v for k, v in be.items() if len(v) > 1}
    fe_only = sorted(u for u in fe if u not in be_paths)
    be_only = sorted(path for path in be_paths if path not in fe)
    method_mismatch = sorted(k for k in fe_calls if k not in be)
    unknown_method_paths = len(set(fe) - {path for _, path in fe_calls})

    for (method, u), classes in sorted(dupes.items()):
        print(f"[WARN] duplicate_backend: {method} {u} 注册于 {classes}")
    for u in fe_only:
        print(f"[WARN] frontend_only: {u}（{fe[u]}）→ 后端未见同形路由")
    for method, u in method_mismatch:
        print(f"[WARN] method_mismatch: {method} {u}（{fe_calls[(method, u)]}）→ 后端未见同方法同形路由")
    for u in be_only:
        print(f"[INFO] backend_only: {u}")

    info(
        f"后端路由 {len(be)} 条；前端路径 {len(fe)} 条；方法级调用 {len(fe_calls)} 条"
        f"（未识别方法 {unknown_method_paths} 条）；frontend_only {len(fe_only)}；"
        f"method_mismatch {len(method_mismatch)}；duplicate {len(dupes)}"
    )
    reasons = []
    if fe_only:
        reasons.append("frontend_only")
    if method_mismatch:
        reasons.append("method_mismatch")
    if strict and reasons:
        print(f"[check-api-contract] EXIT=1（strict：存在 {'/'.join(reasons)}）")
        return 1
    print("[check-api-contract] EXIT=0（信息性，默认不阻断）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
