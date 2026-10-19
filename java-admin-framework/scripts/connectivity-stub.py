#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
connectivity-stub.py — 后端联通接口本地 stub（零依赖，仅用标准库）

作用：在没有真实后端、或沙箱无出网的场景下，模拟 java-admin-framework 的统一包络
Result{code,message,data,traceId,timestamp} 与各错误码，便于：
  - 本机离线验证 connectivity-probe.py（无需启动真实 Spring Boot）；
  - 与前端 scripts/fe-connectivity.mjs 共享同一套契约语义（后端侧镜像）。

实现端点：
  GET /api/connectivity/ping        -> 200 成功包络（含 serverTime/receivedLocale/nonce/framework）
  GET /api/connectivity/slow        -> 故意延迟（> 默认超时 2s）以触发客户端超时
  GET /api/connectivity/error/500   -> 500 包络 COMMON_500
  GET /api/connectivity/error/401   -> 401 包络 COMMON_401
  GET /api/connectivity/error/504   -> 504 包络 COMMON_504（网络超时映射）

用法：
  python3 scripts/connectivity-stub.py                 # 监听 8080
  python3 scripts/connectivity-stub.py --port 9099     # 自定义端口
随后运行：python3 scripts/connectivity-probe.py --base-url http://127.0.0.1:9099
"""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

FRAMEWORK = "java-admin-framework"
SLOW_DELAY = 5  # 秒，必须 > 探针超时阈值


def envelope(code: str, message: str, data=None) -> bytes:
    payload = {
        "code": code,
        "message": message,
        "data": data,
        "traceId": "-",
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")


def probe_data() -> dict:
    return {
        "serverTime": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "receivedLocale": "zh-CN",
        "nonce": "stub-" + str(int(time.time() * 1000)),
        "framework": FRAMEWORK,
    }


class Handler(BaseHTTPRequestHandler):
    def _send(self, status: int, body: bytes):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/connectivity/ping":
            self._send(200, envelope("0", "OK", probe_data()))
            return
        if path == "/api/connectivity/slow":
            time.sleep(SLOW_DELAY)
            self._send(200, envelope("0", "OK", {"ok": True}))
            return
        if path == "/api/connectivity/error/500":
            self._send(500, envelope("COMMON_500", "系统内部错误"))
            return
        if path == "/api/connectivity/error/401":
            self._send(401, envelope("COMMON_401", "未认证或登录已失效"))
            return
        if path == "/api/connectivity/error/504":
            self._send(504, envelope("COMMON_504", "请求超时，请稍后重试"))
            return
        self._send(404, envelope("COMMON_404", "资源不存在"))

    def log_message(self, fmt, *args):
        pass  # 静默，避免淹没探针输出


def main() -> None:
    ap = argparse.ArgumentParser(description="后端联通接口本地 stub")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--host", default="127.0.0.1")
    args = ap.parse_args()
    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"connectivity-stub 监听 http://{args.host}:{args.port}")
    print("端点: /api/connectivity/ping | /slow | /error/401 | /error/500 | /error/504")
    print("按 Ctrl+C 停止。")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。")


if __name__ == "__main__":
    main()
