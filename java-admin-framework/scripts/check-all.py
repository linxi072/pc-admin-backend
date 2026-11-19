#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-all.py：质量门禁统一入口（重刻版）。

编排 7 个硬性检查步骤 + 1 个信息性契约步骤；任一硬性步骤失败即整体 EXIT=1（阻断）。

用法：
    python3 scripts/check-all.py
    python3 scripts/check-all.py --only version,naming       # 只跑指定步骤
    python3 scripts/check-all.py --skip api-contract         # 跳过指定步骤
    python3 scripts/check-all.py --strict                    # 契约步升级为阻断

步骤清单（硬性=失败阻断）：
  1 version          硬性  版本一致性（POM/package.json/CHANGELOG/文档 jar 引用）
  2 conventions      硬性  分层/依赖 DAG/装配唯一/跨模块封闭
  3 naming           硬性  标识符命名（N1–N4）
  4 directory        硬性  目录约定（模块白名单/配置与视图归位/文档死链）
  5 cluster-layout   硬性  scaffold-system 子域簇 ≡ 权威清单
  6 permission       硬性  菜单权限码 ⊆ Java 权限码 + 豁免
  7 api-contract     信息性 前后端契约差异（--strict 升级为阻断）
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))

STEPS = [
    ("version", "check-version.py", True),
    ("conventions", "check-conventions.py", True),
    ("naming", "check-naming.py", True),
    ("directory", "check-directory.py", True),
    ("cluster-layout", "check-cluster-layout.py", True),
    ("permission", "check-permission.py", True),
    ("api-contract", "check-api-contract.py", False),
]


def main() -> int:
    ap = argparse.ArgumentParser(description="统一质量门禁入口")
    ap.add_argument("--only", help="只跑指定步骤（逗号分隔）")
    ap.add_argument("--skip", help="跳过指定步骤（逗号分隔）")
    ap.add_argument("--skip-tests", action="store_true", help="兼容旧参数：跳过脚本行为回归（当前为空集，恒可省略）")
    ap.add_argument("--test-tmpdir", help="兼容旧参数：钉定脚本单测临时目录")
    ap.add_argument("--strict", action="store_true", help="api-contract 升级为阻断")
    args = ap.parse_args()

    only = {s.strip() for s in args.only.split(",")} if args.only else None
    skip = {s.strip() for s in args.skip.split(",")} if args.skip else set()

    failures, passed = [], []
    for name, script, hard in STEPS:
        if only and name not in only:
            continue
        if name in skip:
            print(f"---- [skip] {name} ----")
            continue
        cmd = [sys.executable, os.path.join(SCRIPTS_DIR, script)]
        if name == "api-contract" and args.strict:
            cmd.append("--strict")
        print(f"---- [{name}] {'(hard)' if hard else '(informational)'} {script} ----")
        r = subprocess.run(cmd)
        if r.returncode != 0:
            failures.append(name)
        else:
            passed.append(name)

    print("=" * 60)
    print(f"门禁汇总：通过 {passed}；失败 {failures}")
    hard_failures = [n for n in failures if next(h for s, _, h in STEPS if s == n)]
    if hard_failures:
        print(f"check-all EXIT=1（硬性步骤失败：{hard_failures}，阻断合并/发布）")
        return 1
    print("check-all EXIT=0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
