#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
connectivity-probe.py — 前后端联通运行时探针（零依赖，仅用标准库）

在「用户本机、后端已启动」的环境下运行，对真实后端发起 HTTP 请求，验证：
  1) 正常请求/响应：成功包络 code=='0'，data 字段齐全且与前端契约一致；
  2) 网络超时：对故意慢响应的端点发起请求，断言触发超时（urllib 超时异常）。

用法：
  python3 scripts/connectivity-probe.py
  python3 scripts/connectivity-probe.py --base-url http://backend:8080 --timeout-ms 1500
  # 配合本地 stub 离线验证：
  #   python3 scripts/connectivity-stub.py &        # 默认 8080
  #   python3 scripts/connectivity-probe.py

说明：若后端不可达（沙箱/未启动），脚本打印清晰的 SKIP 提示而非崩溃，方便在 CI
之外的开发者机器上按需运行。
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SPEC = ROOT / "connectivity-spec.json"


def load_spec() -> dict:
    with SPEC.open(encoding="utf-8") as f:
        return json.load(f)


def do_request(method: str, url: str, timeout_ms: int):
    req = urllib.request.Request(url, method=method.upper())
    with urllib.request.urlopen(req, timeout=timeout_ms / 1000.0) as resp:
        body = resp.read().decode("utf-8")
        return resp.status, json.loads(body)


def check_endpoint(spec: dict, ep: dict, timeout_ms: int) -> tuple[bool, str]:
    url = spec["baseUrl"].rstrip("/") + ep["path"]
    try:
        status, body = do_request(ep["method"], url, timeout_ms)
    except urllib.error.HTTPError as e:
        # 后端返回非 2xx 也带回了包络，这里仍尝试解析包络以校验错误码链路
        try:
            body = json.loads(e.read().decode("utf-8"))
        except Exception:
            return False, f"HTTP {e.code} 且响应体非 JSON: {e.reason}"
        return _assert_envelope(ep, body, e.code)
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        return False, f"无法连接后端: {e.reason if hasattr(e, 'reason') else e}"
    return _assert_envelope(ep, body, status)


def _assert_envelope(ep: dict, body: dict, status: int) -> tuple[bool, str]:
    if not isinstance(body, dict) or "code" not in body:
        return False, f"响应不是统一包络 Result{{code,...}}: {body}"
    if body.get("code") != ep.get("expectCode", "0"):
        return False, f"code 期望 {ep.get('expectCode')} 实际 {body.get('code')}"
    for fld in ep.get("expectFields", []):
        if fld not in (body.get("data") or {}):
            return False, f"data 缺少字段 {fld}: {body.get('data')}"
    fw = ep.get("expectFramework")
    if fw and (body.get("data") or {}).get("framework") != fw:
        return False, f"framework 期望 {fw} 实际 {(body.get('data') or {}).get('framework')}"
    return True, f"OK (HTTP {status}, code={body.get('code')})"


def check_timeout(spec: dict, ep: dict, timeout_ms: int) -> tuple[bool, str]:
    url = spec["baseUrl"].rstrip("/") + ep["path"]
    try:
        do_request(ep["method"], url, timeout_ms)
    except TimeoutError:
        return True, "OK (触发超时 TimeoutError，符合预期)"
    except urllib.error.URLError as e:
        if "timed out" in str(e.reason).lower() or "timeout" in str(e.reason).lower():
            return True, f"OK (触发超时: {e.reason})"
        return False, f"非超时类连接错误: {e.reason}"
    except Exception as e:
        return False, f"未触发超时，且请求异常: {e}"
    return False, "未触发超时（后端在阈值内返回，需确认 stub/后端确实慢响应）"


def main() -> int:
    ap = argparse.ArgumentParser(description="前后端联通运行时探针")
    ap.add_argument("--base-url", default=None, help="后端基址（覆盖 spec）")
    ap.add_argument("--timeout-ms", type=int, default=None, help="超时阈值毫秒（覆盖 spec）")
    args = ap.parse_args()

    spec = load_spec()
    base = args.base_url or spec.get("baseUrl")
    timeout = args.timeout_ms or spec.get("timeoutMs", 2000)
    spec["baseUrl"] = base

    print(f"联通探针 -> 后端 {base} (超时阈值 {timeout}ms)\n")
    failures = 0

    for ep in spec.get("endpoints", []):
        ok, msg = check_endpoint(spec, ep, timeout)
        print(f"[{'PASS' if ok else 'FAIL'}] {ep['name']} {ep['method']} {ep['path']}: {msg}")
        failures += 0 if ok else 1

    te = spec.get("timeoutEndpoint")
    if te:
        ok, msg = check_timeout(spec, te, timeout)
        print(f"[{'PASS' if ok else 'FAIL'}] {te['name']} {te['method']} {te['path']}: {msg}")
        failures += 0 if ok else 1

    print()
    if failures == 0:
        print("全部联通检查通过。")
        return 0
    print(f"{failures} 项检查未通过，请查看上方详情。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
