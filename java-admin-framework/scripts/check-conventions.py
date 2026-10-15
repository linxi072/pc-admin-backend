#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-conventions.py — 编码风格与目录结构约定校验器（零依赖，仅用标准库）

目标：在不引入任何第三方依赖（兼容离线 `mvn -o` / CI 环境）的前提下，对
`src/main/java` 下的 Java 源码做静态约定校验，防止架构随时间漂移。

校验项：
  H1. 包级循环依赖（强连通分量检测）—— 违反则失败。
  H2. 重复的全限定类名 —— 违反则失败。
  H3. 依赖方向不变量 —— 低层包不得依赖高层包（见 TIERS/ FORBIDDEN），违反则失败。
  W1. 类名后缀与层不一致（特性分包下允许，仅报告）。
  W2. 平铺特性模块提示（类数较多却无层子包，建议拆分）。
  W3. 数据类放置（*DTO/*VO/*Request/*Command/*Query/*Response 应位于 dto/vo 等
      子包；*DO/*Entity/*Model 应位于 domain/entity/model 子包）。平铺特性模块内
      的历史放置暂按警告处理，不阻断；清理完成后可升级为硬规则。

退出码：发现任何 H* 硬违规 -> 1；仅 W* 警告或无问题 -> 0。

用法：
  python3 scripts/check-conventions.py
  python3 scripts/check-conventions.py --src src/main/java
"""
import argparse
import os
import re
import sys
from collections import defaultdict, deque

ROOT_PKG = "com.acme.scaffold"

# ---- 分层定义（基于包路径“最后一段”判定层；非层段视为 neutral，不参与方向校验）----
# 越低层越基础。domain/entity/model 为最底层，controller/web 为最高层。
TIER = {
    "domain": 0, "entity": 0, "model": 0,
    "config": 1, "configuration": 1,
    "service": 2, "application": 2, "manager": 2, "executor": 2,
    "controller": 3, "web": 3,
}
# 允许的依赖方向：key(层) 不得依赖于 value 列表中的任意层（即低层不得指向高层）
FORBIDDEN = {
    "domain":      {"controller", "web", "service", "application", "manager", "executor", "config"},
    "entity":      {"controller", "web", "service", "application", "manager", "executor", "config"},
    "model":       {"controller", "web", "service", "application", "manager", "executor", "config"},
    "config":      {"controller", "web"},
    "service":     {"controller", "web"},
    "application": {"controller", "web"},
    "manager":     {"controller", "web"},
    "executor":    {"controller", "web"},
}

# 层承载后缀 -> 期望的层 token。仅这些后缀用于“错层”检测（W1），
# 其余类（实体/VO/DTO/工具类）命名自由，不强制后缀。
LAYER_BEARING = {
    "ServiceImpl": "service", "Service": "service",
    "Manager": "service", "Executor": "service",
    "RestController": "controller", "Controller": "controller",
    "Configuration": "config", "Config": "config",
    "Interceptor": "interceptor", "Filter": "filter", "Aspect": "aspect",
    "Listener": "listener",
    "Scheduler": "job", "Runner": "job", "Job": "job",
    "Properties": "properties",
}

# 数据类后缀 -> 允许出现的包段（包路径任意一段命中即合规，如 workflow.dto.design）。
# 目的：传输对象收敛到 dto/vo 子包、持久化对象收敛到 domain/entity/model 子包，
# 避免数据类散落在特性包根部。W3 目前为警告级（平铺模块历史放置不阻断合并）。
DATA_CLASS_RULES = {
    "DO": {"domain", "entity", "model", "po"},
    "PO": {"domain", "entity", "model", "po"},
    "Entity": {"domain", "entity", "model", "po"},
    "Model": {"domain", "entity", "model"},
    "DTO": {"dto", "vo", "domain", "entity", "model"},
    "VO": {"dto", "vo", "domain", "entity", "model"},
    "Request": {"dto", "vo", "domain", "entity", "model"},
    "Response": {"dto", "vo", "domain", "entity", "model"},
    "Command": {"dto", "vo", "domain", "entity", "model"},
    "Query": {"dto", "vo", "domain", "entity", "model"},
}

PKG_RE = re.compile(r"^package\s+([\w\.]+);", re.M)
TYPE_RE = re.compile(
    r"(?:public\s+|abstract\s+|final\s+|@\w+(?:\([^)]*\))?\s*)*(?:class|interface|enum|record)\s+(\w+)",
)
IMPORT_RE = re.compile(r"^import\s+(com\.acme\.scaffold\.[\w\.]+)\s*;", re.M)


def iter_java(src):
    for dp, _, fs in os.walk(src):
        for f in fs:
            if f.endswith(".java"):
                yield os.path.join(dp, f)


def layer_of(pkg):
    last = pkg.split(".")[-1]
    return TIER.get(last)  # None if neutral


def last_segment(pkg):
    return pkg.split(".")[-1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default="src/main/java")
    args = ap.parse_args()

    if not os.path.isdir(args.src):
        print(f"[ERROR] 源码目录不存在: {args.src}", file=sys.stderr)
        return 2

    files = list(iter_java(args.src))
    print(f"扫描 Java 文件: {len(files)}")

    fqn_to_pkg = {}
    pkg_of_file = {}
    imports_by_pkg = defaultdict(set)
    class_names = []
    hard_errors = []
    warnings = []

    for fp in files:
        with open(fp, encoding="utf-8", errors="ignore") as fh:
            txt = fh.read()
        pm = PKG_RE.search(txt)
        if not pm:
            warnings.append(f"无 package 声明: {fp}")
            continue
        pkg = pm.group(1)
        pkg_of_file[fp] = pkg
        for tm in TYPE_RE.finditer(txt):
            name = tm.group(1)
            fqn = f"{pkg}.{name}"
            class_names.append((name, pkg, fp))
            if fqn in fqn_to_pkg and fqn_to_pkg[fqn] != pkg:
                hard_errors.append(f"重复全限定类名: {fqn}  ({fqn_to_pkg[fqn]} vs {pkg})")
            fqn_to_pkg[fqn] = pkg
        for im in IMPORT_RE.findall(txt):
            dep = im[:-2] if im.endswith(".*") else ".".join(im.split(".")[:-1])
            if dep != pkg and dep.startswith(ROOT_PKG):
                imports_by_pkg[pkg].add(dep)

    # H2: duplicate FQN
    seen = {}
    for name, pkg, fp in class_names:
        key = (pkg, name)
        if key in seen:
            hard_errors.append(f"重复类 {pkg}.{name}: {seen[key]} 与 {fp}")
        else:
            seen[key] = fp

    # H1: package-level cycles via Tarjan SCC
    nodes = set(imports_by_pkg) | {d for ds in imports_by_pkg.values() for d in ds}
    low = {}
    onstack = {}
    idx = 0
    stack = []
    sccs = []

    def strongconnect(v):
        nonlocal idx
        low[v] = idx
        onstack[v] = True
        stack.append(v)
        idx += 1
        for w in imports_by_pkg.get(v, ()):
            if w not in low:
                strongconnect(w)
                low[v] = min(low[v], low[w])
            elif onstack.get(w):
                low[v] = min(low[v], low[w])
        if low[v] == low.get(v) and stack and stack[-1] == v:
            comp = []
            while True:
                w = stack.pop()
                onstack[w] = False
                comp.append(w)
                if w == v:
                    break
            sccs.append(comp)

    for n in list(nodes):
        if n not in low:
            strongconnect(n)

    cycles = [c for c in sccs if len(c) > 1]
    if cycles:
        for c in cycles:
            hard_errors.append("包级循环依赖: " + " <-> ".join(sorted(c)))

    # H3: dependency direction invariants (only between explicitly-layered packages)
    for src_pkg, deps in imports_by_pkg.items():
        sl = layer_of(src_pkg)
        if sl is None:
            continue
        for dep in deps:
            dl_name = last_segment(dep)
            banned = FORBIDDEN.get(last_segment(src_pkg))
            if banned and dl_name in banned:
                hard_errors.append(
                    f"依赖方向违规: {src_pkg} (层={last_segment(src_pkg)}) "
                    f"依赖 {dep} (层={dl_name}) —— 低层不得依赖高层"
                )

    # W1: 错层放置检测（低噪声，真实违规）—— 仅当“类名层承载后缀”与其所在包的
    #     最后一段（明确层 token）不一致时才告警；特性分包根（中性）不告警。
    mis_tier = []
    for name, pkg, fp in class_names:
        expected = None
        for suf in sorted(LAYER_BEARING, key=len, reverse=True):
            if name.endswith(suf):
                expected = LAYER_BEARING[suf]
                break
        if not expected:
            continue
        last = pkg.split(".")[-1]
        # 仅当“类名期望层”与“包末段层”均为显式 tier 且冲突时才报（如 *Controller 落在 *service 包）。
        # filter/properties/aspect 等无 tier 的后缀允许出现在任意包（含 config），不误报。
        exp_tier = TIER.get(expected)
        last_tier = TIER.get(last)
        if exp_tier is not None and last_tier is not None and exp_tier != last_tier:
            mis_tier.append(f"{pkg}.{name}（类名含『{expected}』但包末段为『{last}』）")
    if mis_tier:
        warnings.append(
            f"疑似错层放置（{len(mis_tier)}，建议复核）:\n    "
            + "\n    ".join(mis_tier[:40])
            + ("\n    ..." if len(mis_tier) > 40 else "")
        )

    # W2: 模块分层概览（中性信息，不强制拆分）—— 统计各特性模块是否已分层子包
    feat = defaultdict(set)
    feat_count = defaultdict(int)
    for name, pkg, fp in class_names:
        parts = pkg.split(".")
        if len(parts) >= 4 and parts[0:3] == ROOT_PKG.split("."):
            feat[parts[3]].add(parts[4] if len(parts) >= 5 else "(root)")
            feat_count[parts[3]] += 1
    layer_subs = {"controller", "service", "config", "domain", "entity", "dto", "model"}
    layered, flat = [], []
    for mod in sorted(feat_count):
        subs = feat[mod]
        (layered if (layer_subs & subs) else flat).append(f"{mod}({feat_count[mod]})")
    overview = f"已分层子包模块: {', '.join(layered) or '无'}\n    平铺特性模块: {', '.join(flat) or '无'}"
    warnings.append("模块分层概览:\n    " + overview)

    # W3: 数据类放置校验 —— *DTO/*VO/*Request 等应在 dto/vo 子包，*DO/*Entity/*Model
    #     应在 domain/entity/model 子包（包路径任意一段命中即合规）。平铺特性模块内
    #     的历史放置暂按警告处理，不阻断合并；归位完成后可升级为硬规则。
    misplaced_data = []
    for name, pkg, fp in class_names:
        allowed = None
        for suf in sorted(DATA_CLASS_RULES, key=len, reverse=True):
            if name.endswith(suf) and len(name) > len(suf):
                allowed = DATA_CLASS_RULES[suf]
                break
        if allowed is None:
            continue
        if not (set(pkg.split(".")) & allowed):
            misplaced_data.append(f"{pkg}.{name}")
    if misplaced_data:
        warnings.append(
            f"数据类放置待归位（{len(misplaced_data)}，建议后续迭代移入 dto/domain 等子包）:\n    "
            + "\n    ".join(misplaced_data[:40])
            + ("\n    ..." if len(misplaced_data) > 40 else "")
        )

    # ---- 输出 ----
    print("=" * 64)
    print("校验结果")
    print("=" * 64)
    if not hard_errors and not warnings:
        print("✅ 全部通过，无硬违规与警告。")
    else:
        if hard_errors:
            print(f"\n❌ 硬违规 (H*)，共 {len(hard_errors)} 项：")
            for e in hard_errors:
                print("  - " + e)
        if warnings:
            print(f"\n⚠️  警告 (W*)，共 {len(warnings)} 项：")
            for w in warnings:
                print("  - " + w)
    print("=" * 64)

    return 1 if hard_errors else 0


if __name__ == "__main__":
    sys.exit(main())
