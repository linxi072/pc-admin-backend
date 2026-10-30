#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
java-admin-framework 后端性能压测脚本（零依赖，仅标准库）。

覆盖三种核心场景：
  TC-P1  匿名探针      GET  /api/connectivity/ping       （框架开销基线，无 DB / 无鉴权）
  TC-P2  鉴权读接口    GET  /api/system/menus/tree        （Bearer 鉴权 + DB 读取）
  TC-P3  登录鉴权      POST /api/auth/login               （口令校验 + JWT 签发 + DB）

用法：
  python3 load_test.py --base http://127.0.0.1:8080 --total 3000 --concurrency 50

说明：
  - 登录场景每次请求随机化 X-Forwarded-For，规避 RateLimitFilter 的「login:IP」60s/10 次限流。
  - 输出为 JSON，包含 p50/p95/p99、平均/最大延迟、吞吐（req/s）、错误率与状态码分布。
  - 本机 loopback + 同机 MySQL 的实测值反映「应用层容量」，非生产网络/DB 分离后的 SLA。
"""

import argparse
import json
import random
import statistics
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed


def _do_request(url, method="GET", body=None, headers=None, timeout=15):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    hdrs = dict(headers or {})
    if data is not None:
        hdrs["Content-Type"] = "application/json"
    for k, v in hdrs.items():
        req.add_header(k, v)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            _ = resp.read()
            status = resp.status
        ok = 200 <= status < 400
    except urllib.error.HTTPError as e:
        status = e.code
        ok = False
    except Exception:
        status = -1
        ok = False
    dt = time.perf_counter() - t0
    return dt, status, ok


def _rand_ip():
    return "10.%d.%d.%d" % (random.randint(1, 254), random.randint(1, 254), random.randint(1, 254))


def run_scenario(name, url, method="GET", body=None, headers=None, header_fn=None,
                 total=2000, concurrency=50, warmup=50):
    # 预热：少量请求拉起连接池 / JIT，不计入统计
    for _ in range(warmup):
        _do_request(url, method, body, headers if header_fn is None else header_fn())

    latencies = []
    statuses = {}
    errors = 0
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        futures = []
        for _ in range(total):
            h = headers if header_fn is None else header_fn()
            futures.append(ex.submit(_do_request, url, method, body, h))
        for f in as_completed(futures):
            dt, status, ok = f.result()
            latencies.append(dt)
            statuses[status] = statuses.get(status, 0) + 1
            if not ok:
                errors += 1
    elapsed = time.perf_counter() - start
    latencies.sort()

    def _pct(p):
        if not latencies:
            return 0.0
        idx = max(0, min(len(latencies) - 1, int(round((p / 100.0) * (len(latencies) - 1)))))
        return latencies[idx] * 1000.0

    return {
        "scenario": name,
        "total": total,
        "concurrency": concurrency,
        "elapsed_s": round(elapsed, 3),
        "throughput_rps": round(total / elapsed, 1) if elapsed > 0 else 0.0,
        "errors": errors,
        "error_rate": round(errors / total, 4) if total else 0.0,
        "p50_ms": round(_pct(50), 2),
        "p95_ms": round(_pct(95), 2),
        "p99_ms": round(_pct(99), 2),
        "avg_ms": round((sum(latencies) / len(latencies)) * 1000, 2) if latencies else 0.0,
        "min_ms": round(latencies[0] * 1000, 2) if latencies else 0.0,
        "max_ms": round(latencies[-1] * 1000, 2) if latencies else 0.0,
        "statuses": statuses,
    }


def fetch_token(base, user, password):
    try:
        body = json.dumps({"username": user, "password": password}).encode()
        req = urllib.request.Request(base + "/api/auth/login", data=body, method="POST",
                                    headers={"Content-Type": "application/json",
                                             "X-Forwarded-For": _rand_ip()})
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode())
        data = payload.get("data") or {}
        return data.get("accessToken")
    except Exception as e:
        print("[warn] 获取令牌失败: %s" % e, flush=True)
        return None


def main():
    ap = argparse.ArgumentParser(description="java-admin-framework 性能压测")
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="admin123")
    ap.add_argument("--total", type=int, default=3000)
    ap.add_argument("--concurrency", type=int, default=50)
    ap.add_argument("--ramp", action="store_true", help="额外做并发爬坡 10→200 探测吞吐饱和点")
    args = ap.parse_args()

    token = fetch_token(args.base, args.user, args.password)
    print("[info] token=%s" % ("已获取" if token else "未获取（TC-P2 将跳过）"), flush=True)

    results = []
    # TC-P1 匿名探针（无 body、无鉴权）
    results.append(run_scenario(
        "TC-P1 ping(匿名)", args.base + "/api/connectivity/ping", "GET", None,
        {"X-Forwarded-For": _rand_ip()}, total=args.total, concurrency=args.concurrency))

    # TC-P2 鉴权读接口
    if token:
        results.append(run_scenario(
            "TC-P2 menus/tree(鉴权读)", args.base + "/api/system/menus/tree", "GET", None,
            {"Authorization": "Bearer " + token, "X-Forwarded-For": _rand_ip()},
            total=max(200, args.total // 4), concurrency=max(10, args.concurrency // 2)))

    # TC-P3 登录鉴权（每次随机 IP 规避限流）
    login_body = {"username": args.user, "password": args.password}
    results.append(run_scenario(
        "TC-P3 login(登录鉴权)", args.base + "/api/auth/login", "POST", login_body, None,
        header_fn=lambda: {"X-Forwarded-For": _rand_ip()},
        total=max(200, args.total // 5), concurrency=max(10, args.concurrency // 2)))

    # 并发爬坡：登录场景从 10 到 200，定位吞吐饱和 / 错误起点
    if args.ramp:
        ramp = []
        for c in [10, 25, 50, 100, 150, 200]:
            r = run_scenario(
                "TC-P3-ramp login@c=%d" % c, args.base + "/api/auth/login", "POST", login_body,
                None, header_fn=lambda: {"X-Forwarded-For": _rand_ip()},
                total=max(200, args.total // 10), concurrency=c, warmup=20)
            ramp.append(r)
        results.append({"ramp": ramp})

    print(json.dumps(results, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
