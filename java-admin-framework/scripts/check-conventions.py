#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-conventions.py — 编码风格与目录结构约定校验器（零依赖，仅用标准库）

目标：在不引入任何第三方依赖（兼容离线 `mvn -o` / CI 环境）的前提下，对
Java 源码做静态约定校验，防止架构随时间漂移。

V1.0.3 起工程拆分为多 Maven 模块（scaffold-*），本校验器默认自动发现全部
`scaffold-*/src/main/java` 源码根做**全局**包级校验，并新增模块级依赖环检测；
仍可用 `--src` 指定单个/多个目录（兼容单模块与单元测试场景）。

校验项：
  H1. 包级循环依赖（强连通分量检测）—— 违反则失败。
  H2. 重复的全限定类名 —— 违反则失败。
  H3. 依赖方向不变量 —— 低层包不得依赖高层包（见 TIERS/ FORBIDDEN），违反则失败。
  H4. 数据类放置 —— *DTO/*VO/*Request/*Command/*Query/*Response 应位于 dto/vo 等
      子包；*DO/*Entity/*Model/PO 应位于 domain/entity/model 子包（包路径任意一段
      命中即合规）。违反即失败，输出中附带应归位的子包指引。
  H5. 模块级循环依赖（V1.0.3 新增）—— 解析父 POM <modules> 与各模块 POM 依赖，
      模块依赖图必须成 DAG；违反即失败（Maven 反应堆同样无法成环，提前拦截）。
  W1. 类名后缀与层不一致（特性分包下允许，仅报告）。
  W2. 模块分层概览（已分层 vs 平铺，供人工参考，不强制）。
  W3. 非启动模块依赖 scaffold-admin（V1.0.3 新增，仅报告——部署依赖只应收敛在 admin）。
  W4. 模块 artifactId 未使用 scaffold- 前缀（V1.0.3 新增，仅报告）。

退出码：发现任何 H* 硬违规 -> 1；仅 W* 警告或无问题 -> 0。

用法：
  python3 scripts/check-conventions.py                        # 自动发现 scaffold-*/src/main/java
  python3 scripts/check-conventions.py --src src/main/java    # 指定单目录（单模块模式）
  python3 scripts/check-conventions.py --src a/src/main/java --src b/src/main/java
"""
import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT_PKG = "com.acme.scaffold"
MODULE_GROUP = "com.acme.scaffold"   # 模块坐标 groupId（与包根一致）
ADMIN_MODULE = "scaffold-admin"      # 启动装配模块（唯一可部署模块）
MODULE_PREFIX = "scaffold-"          # 模块 artifactId 命名规范前缀

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
# 避免数据类散落在特性包根部。H4 为硬规则：违反即阻断（V1.0.2 起由 W3 警告升级，
# 历史放置已于拆分迭代中全部归位，见 docs/manual/coding-standard.md §7）。
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


# ---- 多模块支持（V1.0.3）----

def find_base(start_dirs):
    """从给定起点逐级上溯，寻找含 scaffold-common 子模块的工程根（父 POM 所在目录）。"""
    for start in start_dirs:
        d = os.path.abspath(start)
        while True:
            if os.path.isfile(os.path.join(d, "scaffold-common", "pom.xml")):
                return d
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
    return None


def discover_src_roots(base):
    """发现全部 scaffold-*/src/main/java 源码根（按目录名排序，保证输出稳定）。"""
    roots = []
    if not os.path.isdir(base):
        return roots
    for name in sorted(os.listdir(base)):
        candidate = os.path.join(base, name, "src", "main", "java")
        if name.startswith(MODULE_PREFIX) and os.path.isdir(candidate):
            roots.append(candidate)
    return roots


def _local(tag):
    return tag.split("}")[-1] if "}" in tag else tag


def parse_pom(path):
    """解析 POM：返回 (<modules> 列表, <dependencies> 中 (groupId, artifactId) 列表)。零依赖 stdlib。"""
    root = ET.parse(path).getroot()
    modules = []
    deps = []
    mods_el = None
    deps_el = None
    for child in root:
        name = _local(child.tag)
        if name == "modules":
            mods_el = child
        elif name == "dependencies":
            deps_el = child
    if mods_el is not None:
        for m in mods_el:
            if _local(m.tag) == "module" and m.text and m.text.strip():
                modules.append(m.text.strip())
    if deps_el is not None:
        for dep in deps_el:
            if _local(dep.tag) != "dependency":
                continue
            g = a = None
            for f in dep:
                if _local(f.tag) == "groupId":
                    g = (f.text or "").strip()
                elif _local(f.tag) == "artifactId":
                    a = (f.text or "").strip()
            if g and a:
                deps.append((g, a))
    return modules, deps


def find_cycles(graph):
    """Tarjan 强连通分量：返回长度 > 1 的环列表（包级/模块级通用）。"""
    index = {}
    low = {}
    onstack = {}
    idx = 0
    stack = []
    sccs = []

    def strongconnect(v):
        nonlocal idx
        index[v] = idx
        low[v] = idx
        onstack[v] = True
        stack.append(v)
        idx += 1
        for w in graph.get(v, ()):
            if w not in index:
                strongconnect(w)
                low[v] = min(low[v], low[w])
            elif onstack.get(w):
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            comp = []
            while True:
                w = stack.pop()
                onstack[w] = False
                comp.append(w)
                if w == v:
                    break
            sccs.append(comp)

    nodes = set(graph) | {d for ds in graph.values() for d in ds}
    for n in sorted(nodes):
        if n not in index:
            strongconnect(n)
    return [c for c in sccs if len(c) > 1]


def check_module_dag(base, hard_errors, warnings):
    """H5 模块级依赖环检测 + W3/W4 模块命名与依赖方向告警。"""
    root_pom = os.path.join(base, "pom.xml")
    if not os.path.isfile(root_pom):
        return
    modules, _ = parse_pom(root_pom)
    if not modules:
        return
    graph = {}
    for m in modules:
        mp = os.path.join(base, m, "pom.xml")
        if not os.path.isfile(mp):
            warnings.append(f"模块目录缺失 pom.xml: {m}")
            continue
        _, deps = parse_pom(mp)
        graph[m] = sorted({a for g, a in deps if g == MODULE_GROUP})
        if not m.startswith(MODULE_PREFIX):
            warnings.append(f"W4 模块命名不规范: {m}（artifactId 应以 {MODULE_PREFIX} 开头）")
        if m != ADMIN_MODULE and ADMIN_MODULE in graph[m]:
            warnings.append(
                f"W3 依赖方向可疑: {m} 依赖启动模块 {ADMIN_MODULE}（部署依赖只应收敛在 {ADMIN_MODULE}）")

    cycles = find_cycles(graph)
    for c in cycles:
        hard_errors.append("模块级循环依赖(H5): " + " <-> ".join(sorted(c)))

    print("=" * 64)
    print("模块依赖图（H5 校验，共 %d 个模块）" % len(graph))
    print("=" * 64)
    for m in sorted(graph):
        deps = graph[m]
        print(f"  {m} -> {', '.join(deps) if deps else '(无内部依赖)'}")


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", action="append", default=None,
                    help="Java 源码目录（可重复传入多个）；默认自动发现 <base>/scaffold-*/src/main/java")
    args = ap.parse_args(argv)

    script_base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    if args.src:
        # 显式 --src 模式（单目录/多目录/回归测试）：仅做包级校验，不运行 H5，
        # 避免在校验任意源码目录时误连到真实工程根。
        roots = args.src
        base = None
    else:
        # 默认模式：自动发现 scaffold-*/src/main/java，并附带 H5 模块依赖环检测
        base = find_base([os.getcwd(), script_base])
        if base:
            roots = discover_src_roots(base)
            if not roots:
                print(f"[ERROR] 工程根 {base} 下未发现 {MODULE_PREFIX}*/src/main/java", file=sys.stderr)
                return 2
        else:
            roots = ["src/main/java"]

    missing = [r for r in roots if not os.path.isdir(r)]
    if missing:
        print(f"[ERROR] 源码目录不存在: {', '.join(missing)}", file=sys.stderr)
        return 2

    files = []
    for r in roots:
        files.extend(iter_java(r))
    print(f"扫描源码根: {len(roots)} 个")
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
    cycles = find_cycles(imports_by_pkg)
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
        last = pkg.split(".")
        # config 模块按关注点拆分（config.web/config.jooq/config.redis 等），
        # *Config 类落在这些子包属正常放置，不按错层告警。
        if expected == "config" and "config" in last:
            continue
        # 仅当“类名期望层”与“包末段层”均为显式 tier 且冲突时才报（如 *Controller 落在 *service 包）。
        # filter/properties/aspect 等无 tier 的后缀允许出现在任意包（含 config），不误报。
        exp_tier = TIER.get(expected)
        last_tier = TIER.get(last[-1])
        if exp_tier is not None and last_tier is not None and exp_tier != last_tier:
            mis_tier.append(f"{pkg}.{name}（类名含『{expected}』但包末段为『{last[-1]}』）")
    if mis_tier:
        warnings.append(
            f"疑似错层放置（{len(mis_tier)}，建议复核）:\n    "
            + "\n    ".join(mis_tier[:40])
            + ("\n    ..." if len(mis_tier) > 40 else "")
        )

    # W2: 模块分层概览（中性信息，不强制拆分）—— 统计各特性模块是否已分层子包。
    #     层 token 出现在任意子包深度即视为已分层（如 common.api.dto 的二层 dto）。
    feat = defaultdict(set)
    feat_count = defaultdict(int)
    for name, pkg, fp in class_names:
        parts = pkg.split(".")
        if len(parts) >= 4 and parts[0:3] == ROOT_PKG.split("."):
            feat[parts[3]].update(parts[4:] if len(parts) >= 5 else {"(root)"})
            feat_count[parts[3]] += 1
    layer_subs = {"controller", "service", "config", "domain", "entity", "dto", "model"}
    layered, flat = [], []
    for mod in sorted(feat_count):
        subs = feat[mod]
        (layered if (layer_subs & subs) else flat).append(f"{mod}({feat_count[mod]})")
    overview = f"已分层子包模块: {', '.join(layered) or '无'}\n    平铺特性模块: {', '.join(flat) or '无'}"
    warnings.append("模块分层概览:\n    " + overview)

    # H4: 数据类放置校验（硬规则）—— *DTO/*VO/*Request 等应在 dto/vo 子包，
    #     *DO/*Entity/*Model/PO 应在 domain/entity/model 子包（包路径任意一段命中
    #     即合规）。违反即阻断合并；输出附带应归位的子包指引（V1.0.2 起由 W3 升级）。
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
            misplaced_data.append(f"{pkg}.{name} -> 应移入 {'/'.join(sorted(allowed))} 子包")
    if misplaced_data:
        hard_errors.append(
            f"数据类放置违规（H4），共 {len(misplaced_data)} 项：\n    "
            + "\n    ".join(misplaced_data[:40])
            + ("\n    ..." if len(misplaced_data) > 40 else "")
        )

    # H5: 模块级依赖环检测（多模块工程；单模块模式自动跳过）
    if base:
        check_module_dag(base, hard_errors, warnings)

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
