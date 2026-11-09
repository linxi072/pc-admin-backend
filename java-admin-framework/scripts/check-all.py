#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统一门禁入口（零依赖，仅标准库）。

把分散在 ``scripts/`` 的校验编排成一次可复现的门禁运行，逐项打印退出码与耗时：

  ==========  ============================  ======  ================================
  步骤         脚本                          性质     内容
  ==========  ============================  ======  ================================
  conventions  check-conventions.py          硬性    H1–H5 / W1–W4 编码与架构约定
  naming       check-naming.py               硬性    N1–N2 类型/常量命名 + N3–N4 方法/字段命名
  directory    check-directory.py            硬性    D1–D11 目录约定
  cluster-layout check-cluster-layout.py      硬性    D12 子域簇结构与 §4 表一致性
  api-contract check-api-contract.py         信息性  前后端契约静态对称（不阻断）
  script-tests scripts/test_*.py 单测        硬性    校验器行为回归
  ==========  ============================  ======  ================================

硬性项任一失败 -> EXIT=1；仅信息性项失败 -> EXIT=0 但明确列为风险提示。
本脚本只做编排：不修改文件、不参与编译与运行时，各步骤仍可单独执行。

用法::

    python3 scripts/check-all.py
    python3 scripts/check-all.py --only conventions,directory
    python3 scripts/check-all.py --skip-tests
    python3 scripts/check-all.py --test-tmpdir <dir>   # 受限环境钉定临时目录
    python3 scripts/check-all.py --quiet               # 抑制子命令输出

``--test-tmpdir`` 说明：某些沙箱 / 受限环境会阻止 ``tempfile`` 的默认目录探测
（创建再删除随机探测文件），导致使用 ``TemporaryDirectory`` 的脚本单测在
``setUp`` 阶段报错。显式指定一个已存在且可写的目录可绕开探测；该参数只影响
script-tests 子进程，不改变被测脚本的行为。

退出码：0 = 硬性项全通过；1 = 存在硬性失败；2 = 参数错误 / 工程路径不存在。
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from dataclasses import dataclass

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BACKEND_ROOT = os.path.dirname(SCRIPT_DIR)
TEST_PATTERN = "test_*.py"

# 受限环境的单测启动器：钉定 tempfile.tempdir，并让 TemporaryDirectory 的清理
# 容忍「删除被策略拦截」，再委托 unittest.defaultTestLoader.discover。
# 仅当显式传 --test-tmpdir 时启用；正常环境仍走标准的 -m unittest discover。
UNITTEST_BOOTSTRAP = """\
import shutil, sys, tempfile, unittest
tempfile.tempdir = {tmpdir!r}
_orig = tempfile.TemporaryDirectory
class _Tolerant(_orig):
    def cleanup(self):
        try:
            self._finalizer.detach()
        except Exception:
            pass
        shutil.rmtree(self.name, ignore_errors=True)
tempfile.TemporaryDirectory = _Tolerant
suite = unittest.defaultTestLoader.discover(
    start_dir={start!r}, pattern={pattern!r}, top_level_dir={top!r})
result = unittest.TextTestRunner(verbosity=1).run(suite)
sys.exit(0 if result.wasSuccessful() else 1)
"""


@dataclass(frozen=True)
class Step:
    name: str
    script: str | None
    hard: bool
    desc: str


STEPS = (
    Step("conventions", "check-conventions.py", True, "H1–H5 / W1–W4 编码与架构约定"),
    Step("naming", "check-naming.py", True, "N1–N2 类型/常量命名 + N3–N4 方法/字段命名"),
    Step("directory", "check-directory.py", True, "D1–D11 目录约定"),
    Step("cluster-layout", "check-cluster-layout.py", True, "D12 子域簇结构与 §4 表一致性"),
    Step("api-contract", "check-api-contract.py", False, "前后端契约对称性（信息性）"),
    Step("script-tests", None, True, "scripts 行为回归单测"),
)


def select_steps(only=None, skip_tests=False):
    """按 --only / --skip-tests 选出待跑步骤；未知步骤名抛 ValueError。"""
    names = None
    if only:
        names = {x.strip() for x in only.split(",") if x.strip()}
        unknown = names - {s.name for s in STEPS}
        if unknown:
            raise ValueError(
                f"未知门禁步骤: {', '.join(sorted(unknown))}；"
                f"可选: {', '.join(s.name for s in STEPS)}"
            )
    chosen = [s for s in STEPS if names is None or s.name in names]
    if skip_tests:
        chosen = [s for s in chosen if s.name != "script-tests"]
    return chosen


def build_command(step: Step, scripts_dir: str, test_tmpdir=None):
    """构造单个步骤的子进程命令（纯函数，便于行为回归测试）。"""
    if step.script:
        return [sys.executable, os.path.join(scripts_dir, step.script)]
    if test_tmpdir:
        code = UNITTEST_BOOTSTRAP.format(
            tmpdir=os.path.abspath(test_tmpdir),
            start=scripts_dir,
            pattern=TEST_PATTERN,
            top=scripts_dir,
        )
        return [sys.executable, "-c", code]
    return [sys.executable, "-m", "unittest", "discover",
            "-s", scripts_dir, "-p", TEST_PATTERN]


def run_step(cmd, cwd, quiet=False):
    """执行子命令并返回 (退出码, 耗时秒)。启动失败按 127 计，视作硬性失败。"""
    kwargs = {"cwd": cwd}
    if quiet:
        kwargs["stdout"] = subprocess.DEVNULL
        kwargs["stderr"] = subprocess.DEVNULL
    started = time.monotonic()
    try:
        rc = subprocess.run(cmd, **kwargs).returncode
    except OSError as exc:
        if not quiet:
            print(f"      [启动失败] {exc}", file=sys.stderr)
        rc = 127
    return rc, time.monotonic() - started


def main(argv=None):
    ap = argparse.ArgumentParser(description="统一门禁入口（约定 / 目录 / 契约 / 脚本单测）")
    ap.add_argument("--only", default=None, help="逗号分隔的步骤名，仅跑选中项")
    ap.add_argument("--skip-tests", action="store_true", help="跳过 script-tests 步骤")
    ap.add_argument("--scripts-dir", default=SCRIPT_DIR, help="校验脚本目录（单测用）")
    ap.add_argument("--backend-root", default=BACKEND_ROOT, help="后端仓根（子命令 cwd）")
    ap.add_argument("--test-tmpdir", default=None, help="受限环境下钉定脚本单测的临时目录")
    ap.add_argument("--quiet", action="store_true", help="抑制子命令输出")
    args = ap.parse_args(argv)

    scripts_dir = os.path.abspath(args.scripts_dir)
    backend_root = os.path.abspath(args.backend_root)
    if not os.path.isdir(scripts_dir) or not os.path.isdir(backend_root):
        print(f"[ERROR] 工程路径不存在: scripts={scripts_dir} backend={backend_root}",
              file=sys.stderr)
        return 2
    try:
        steps = select_steps(args.only, args.skip_tests)
    except ValueError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 2
    if not steps:
        print("[ERROR] 没有选中任何门禁步骤", file=sys.stderr)
        return 2
    if args.test_tmpdir:
        try:
            os.makedirs(args.test_tmpdir, exist_ok=True)
        except OSError as exc:
            print(f"[ERROR] 无法创建 --test-tmpdir: {exc}", file=sys.stderr)
            return 2

    print("=" * 64)
    print("统一门禁 check-all")
    print("=" * 64)
    print(f"脚本目录: {scripts_dir}")
    print(f"后端仓根: {backend_root}")
    print(f"本次步骤: {', '.join(s.name for s in steps)}")
    print("-" * 64)

    results = []
    for i, step in enumerate(steps, 1):
        kind = "硬性" if step.hard else "信息性"
        print(f"[{i}/{len(steps)}] {step.name:<14} {step.desc}")
        cmd = build_command(step, scripts_dir, args.test_tmpdir)
        rc, elapsed = run_step(cmd, backend_root, args.quiet)
        results.append((step, rc))
        print(f"      -> EXIT={rc}  ({elapsed:.2f}s)  {kind}")

    print("=" * 64)
    hard_failed = [(s, rc) for s, rc in results if rc != 0 and s.hard]
    info_failed = [(s, rc) for s, rc in results if rc != 0 and not s.hard]
    for s, rc in info_failed:
        print(f"[风险提示] 信息性步骤 {s.name} 返回 EXIT={rc}（不阻断门禁）")
    if hard_failed:
        print(f"硬性门禁未通过（{len(hard_failed)} 项）：")
        for s, rc in hard_failed:
            print(f"  - {s.name}: EXIT={rc}")
        print("\n可单独复跑对应脚本定位；确需临时跳过用 --only / --skip-tests。")
        return 1
    print("统一门禁通过：硬性项全部 EXIT=0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
