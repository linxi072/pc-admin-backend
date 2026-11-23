#!/usr/bin/env python3
"""轻量网关压测脚本（E1 验收，零外部依赖，仅用 Python 标准库）。

对同一目标 URL 以并发线程发起 GET 请求，统计吞吐（QPS）、时延分位（P50/P95/P99）、
状态码分布（含 429 限流命中率），用于对比「网关路径」与「直连路径」的性能差异。

用法：
    python3 gateway_bench.py --url http://127.0.0.1:8080/api/health \
        --requests 2000 --concurrency 20 \
        --label gateway --out /tmp/gateway-bench.json
    python3 gateway_bench.py --url http://127.0.0.1:8080/actuator/health \
        --expect-status 403 --label actuator   # 验证内网准入拒绝

输出：JSON 到 stdout（或 --out），含 qps / latency 分位 / status 分布 / 错误数。
"""
import argparse
import concurrent.futures
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request


class Stats:
    def __init__(self):
        self.lock = threading.Lock()
        self.latencies_ms = []
        self.statuses = {}
        self.errors = 0

    def record(self, latency_ms, status=None, error=False):
        with self.lock:
            self.latencies_ms.append(latency_ms)
            if error:
                self.errors += 1
            else:
                key = str(status)
                self.statuses[key] = self.statuses.get(key, 0) + 1

    def summarize(self, label, elapsed_s):
        lats = sorted(self.latencies_ms)
        total = len(lats)

        def pct(p):
            if not lats:
                return 0.0
            idx = min(len(lats) - 1, int(len(lats) * p))
            return round(lats[idx], 2)

        return {
            "label": label,
            "requests": total,
            "elapsed_seconds": round(elapsed_s, 3),
            "qps": round(total / elapsed_s, 2) if elapsed_s > 0 else 0,
            "latency_ms": {
                "min": round(lats[0], 2) if lats else 0,
                "p50": pct(0.50),
                "p95": pct(0.95),
                "p99": pct(0.99),
                "max": round(lats[-1], 2) if lats else 0,
                "mean": round(statistics.fmean(lats), 2) if lats else 0,
            },
            "status_distribution": dict(sorted(self.statuses.items())),
            "errors": self.errors,
            "rate_limited_429": self.statuses.get("429", 0),
        }


def fire(url, timeout, expect_status, stats):
    start = time.perf_counter()
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "gateway-bench/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.getcode()
            resp.read()
        latency = (time.perf_counter() - start) * 1000
        stats.record(latency, status=status)
        if expect_status is not None and status != expect_status:
            return ("unexpected_status", status)
    except urllib.error.HTTPError as e:
        latency = (time.perf_counter() - start) * 1000
        stats.record(latency, status=e.code)
        if expect_status is not None and e.code != expect_status:
            return ("unexpected_status", e.code)
    except Exception:
        latency = (time.perf_counter() - start) * 1000
        stats.record(latency, error=True)
        return ("network_error", None)
    return (None, None)


def run(args):
    stats = Stats()
    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(fire, args.url, args.timeout, args.expect_status, stats)
                   for _ in range(args.requests)]
        anomalies = {}
        for f in concurrent.futures.as_completed(futures):
            kind, code = f.result()
            if kind:
                anomalies[kind] = anomalies.get(kind, 0) + 1
    elapsed = time.perf_counter() - start
    result = stats.summarize(args.label, elapsed)
    result["url"] = args.url
    result["concurrency"] = args.concurrency
    result["anomalies"] = anomalies
    return result


def main():
    p = argparse.ArgumentParser(description="轻量网关压测（纯标准库）")
    p.add_argument("--url", required=True, help="目标 URL")
    p.add_argument("--requests", type=int, default=1000, help="总请求数（默认 1000）")
    p.add_argument("--concurrency", type=int, default=20, help="并发线程数（默认 20）")
    p.add_argument("--timeout", type=float, default=5.0, help="单请求超时秒（默认 5）")
    p.add_argument("--label", default="bench", help="结果标签（如 gateway/direct）")
    p.add_argument("--expect-status", type=int, default=None, help="期望 HTTP 状态码（如 403/429 校验）")
    p.add_argument("--out", default=None, help="JSON 输出文件（默认 stdout）")
    args = p.parse_args()

    if args.requests <= 0 or args.concurrency <= 0:
        print("requests 与 concurrency 必须为正数", file=sys.stderr)
        return 2

    result = run(args)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text + "\n")
        print(f"结果已写入 {args.out}", file=sys.stderr)
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
