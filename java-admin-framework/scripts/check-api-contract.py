#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-api-contract.py — 前后端接口契约对称性静态检查（零依赖，仅用标准库）

目的：防止「前端调用了某接口、后端却没暴露」或「后端改了路径、前端还在用旧路径」
这类前后端契约漂移。纯静态扫描，不启动后端、不发网络请求，离线可跑。

扫描对象：
  - 后端：src/main/java 下所有 Controller 的 @RequestMapping/@GetMapping/...
    提取 (HTTP方法, 归一化路径)。
  - 前端：pc-admin-vue3-vite/src/api/*.js 下 request({method,url}) 与
    axios.get/post/... 调用，提取 (HTTP方法, 归一化路径)。

匹配策略：除精确归一化（小写、去查询串、合并斜杠、去尾部斜杠）外，再做「参数无关」
匹配——把路径参数段（{id}、${id}、纯数字/UUID 段）统一替换为 *，使
/api/user/{id} 与 /api/user/123 视为同一契约。

输出：
  - matched          两端都存在的接口（契约一致）。
  - backend_only     后端暴露但前端未调用的接口（多为内部/管理接口，仅供参考）。
  - frontend_only    前端调用但后端无对应路由的接口（重点风险：可能漏实现或路径写错）。
  - 退出码：默认 0（信息性）；--strict 且 frontend_only 非空时返回 1。
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BACKEND_SRC = ROOT / "src" / "main" / "java"
FRONTEND_API = ROOT.parent / "pc-admin-vue3-vite" / "src" / "api"

VERBS = ("GET", "POST", "PUT", "DELETE", "PATCH")


def normalize(path: str) -> str:
    """精确归一化：去查询串、合并斜杠、去尾部斜杠、小写。"""
    p = path.split("?")[0].split("#")[0]
    # 代码生成控制器常把 Java 格式化串（%4$s）作为字面路径，统一视作参数段
    p = re.sub(r"%[0-9]*\$?s", "*", p)
    p = re.sub(r"/+", "/", p)
    p = p.rstrip("/")
    if not p:
        p = "/"
    return p.lower()


def param_key(method: str, path: str) -> str:
    """参数无关键：把路径参数段（{x}、${x}、纯数字/UUID）替换为 *。"""
    norm = normalize(path)

    def seg(s: str) -> str:
        if re.fullmatch(r"\{[^}]+\}", s):
            return "*"
        if "${" in s:
            return "*"
        if re.fullmatch(r"[0-9a-fA-F-]{8,}", s):
            return "*"
        return s.lower()

    parts = [seg(x) for x in norm.split("/")]
    return f"{method.upper()} {'/'.join(parts)}"


def first_quoted(attrs: str) -> str:
    """从注解属性串中取路径：优先 value=/path=，否则取首个字符串字面量。"""
    m = re.search(r'(?:value|path)\s*=\s*"([^"]+)"', attrs)
    if m:
        return m.group(1)
    m = re.search(r'"([^"]+)"', attrs)
    return m.group(1) if m else ""


def scan_backend() -> set:
    keys = set()
    if not BACKEND_SRC.exists():
        return keys
    for java in BACKEND_SRC.rglob("*.java"):
        try:
            text = java.read_text(encoding="utf-8")
        except Exception:
            continue
        # 类级基路径：取文件内首个 @RequestMapping（可无参，代表根路径）作为前缀
        base = ""
        class_base = re.search(r"@RequestMapping\s*(?:\(([^)]*)\))?", text)
        if class_base:
            cb_attrs = class_base.group(1) if class_base.group(1) is not None else ""
            base = first_quoted(cb_attrs)
            class_base_start = class_base.start()
        else:
            class_base_start = -1
        # 方法级映射（显式动词注解）。括号可省略：@GetMapping 即「继承类基路径」
        verb_re = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(([^)]*)\))?")
        for m in verb_re.finditer(text):
            verb = m.group(1).upper()
            inner = m.group(2) if m.group(2) is not None else ""
            path = first_quoted(inner)
            keys.add(param_key(verb, base + path))
        # 方法级 @RequestMapping：跳过类级基路径那一条，避免 base+base 重复
        req_re = re.compile(r"@RequestMapping\s*(?:\(([^)]*)\))?")
        for m in req_re.finditer(text):
            if m.start() == class_base_start:
                continue
            attrs = m.group(1) if m.group(1) is not None else ""
            if not attrs.strip():
                # 无参 @RequestMapping：等价于 GET 映射在类基路径
                keys.add(param_key("GET", base))
                continue
            methods = re.findall(r"RequestMethod\.(\w+)", attrs)
            verbs = [v.upper() for v in methods] or ["GET"]
            path = first_quoted(attrs)
            for v in verbs:
                keys.add(param_key(v, base + path))
    return keys


def scan_frontend() -> set:
    keys = set()
    if not FRONTEND_API.exists():
        return keys
    for js in FRONTEND_API.rglob("*.js"):
        try:
            text = js.read_text(encoding="utf-8")
        except Exception:
            continue
        # 模式 A：request({ method, url })
        for m in re.finditer(r"request\s*\(\s*\{([^}]*)\}", text):
            body = m.group(1)
            um = re.search(r"url\s*:\s*['\"`]([^'\"`]+)['\"`]", body)
            if not um:
                continue
            mm = re.search(r"method\s*:\s*['\"`]([^'\"`]+)['\"`]", body)
            verb = (mm.group(1).upper() if mm else "GET")
            url_literal = um.group(1)
            # 兼容前端路径变量拼接：url: '/api/x/y/' + expr
            # 动态段附加在尾部斜杠之后，等价 /api/x/y/{id}，末段记作 *
            if re.search(r"url\s*:\s*['\"`][^'\"`]*['\"`]\s*\+", body) and url_literal.endswith("/"):
                url_literal = url_literal + "*"
            keys.add(param_key(verb, url_literal))
        # 模式 B：axios.get/post/...('/path')
        for m in re.finditer(
            r"\.(get|post|put|delete|patch)\s*\(\s*['\"`]([^'\"`]+)['\"`]", text
        ):
            keys.add(param_key(m.group(1).upper(), m.group(2)))
    return keys


def main() -> int:
    ap = argparse.ArgumentParser(description="前后端接口契约对称性静态检查")
    ap.add_argument("--strict", action="store_true", help="frontend_only 非空时退出码置 1")
    ap.add_argument("--json", action="store_true", help="以 JSON 输出结果")
    args = ap.parse_args()

    backend = scan_backend()
    frontend = scan_frontend()

    matched = backend & frontend
    backend_only = backend - frontend
    frontend_only = frontend - backend

    if args.json:
        print(json.dumps({
            "matched": sorted(matched),
            "backend_only": sorted(backend_only),
            "frontend_only": sorted(frontend_only),
        }, ensure_ascii=False, indent=2))
    else:
        print(f"后端接口数: {len(backend)}  前端调用数: {len(frontend)}")
        print(f"契约一致(matched): {len(matched)}")
        if backend_only:
            print(f"\n[参考] 后端暴露但前端未调用 ({len(backend_only)}):")
            for k in sorted(backend_only):
                print(f"  - {k}")
        if frontend_only:
            print(f"\n[风险] 前端调用但后端无路由 ({len(frontend_only)}):")
            for k in sorted(frontend_only):
                print(f"  - {k}")
        if not frontend_only:
            print("\n未发现前端→后端契约漂移。")

    return 1 if (args.strict and frontend_only) else 0


if __name__ == "__main__":
    sys.exit(main())
