#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-cluster-layout.py — 子域簇结构与规范文档一致性校验器（D12，多模块）

（零依赖，仅标准库）

背景：``docs/DIRECTORY.md`` §4「子域分层骨架」以「子域簇一览」表声明每个模块下
各业务子簇的**文件数**与**簇内分层**。该表是结构收口的唯一事实来源，但此前没有任何
机器守护：新增 / 移动目录后表与磁盘会静默漂移。本脚本把该表编码为可执行校验，
使「文档声明 ≡ 磁盘现状」成为硬性门禁。

§4 采用「一模块一张簇表」，标题形如::

    子域簇一览（`scaffold-system`，括号内为 Java 文件数）：
    子域簇一览（`scaffold-cms`，括号内为 Java 文件数）：

未标注模块名的旧式标题仍按 ``scaffold-system`` 解析（向后兼容既有夹具与历史文档）。
模块名到包根按 ``scaffold-<suffix>`` → ``com/acme/scaffold/<suffix>`` 自动推导，
个别不一致的模块可在 ``MODULE_PKG_OVERRIDES`` 中登记。

与既有脚本的职责边界：

  * ``check-directory.py``（D1–D14）守护**模块级**目录约定（顶层包根、前端样式、
    文档索引、模块 POM、数据访问层单一写法等），不读 §4 簇表。
  * ``check-conventions.py``（H1–H5 / W1–W4）守护**单个类**的包级环、依赖方向、
    数据类放置，不校验目录集合。
  * ``check-cluster-layout.py``（D12）守护 **§4 簇表** 与磁盘目录/文件数的双向一致性。

校验项：

  C1（硬）. 每个子域簇的 Java 文件数须与 §4 表中括号内数字一致。
  C2（硬）. 二级层集合双向一致：表里声明的层必须存在，磁盘上的层必须被声明。
  C3（硬）. 各层子目录集合双向一致（含多级路径的中间层）。
  C4（硬）. DO 落位：簇内不得存在**直挂** ``domain/`` 的 Java 文件（须在
            ``domain/model/`` 下），与 §4「领域层 ``domain/model/`` 放 ``*DO``」一致。
  C5（硬）. 簇内不得出现 §6 禁止的扁平包（``controller`` / ``service`` /
            ``entity`` / ``rules`` / ``port``）。

§4 表的记法（本脚本解析）：

  * ``/``   —— 并列的二级层，如 ``api/application/dto``；
  * ``{a,b}`` —— 该层下的子目录，如 ``domain{model,rule}``；
  * ``X/{a,b}`` —— 层 ``X`` 下的子目录 ``a`` 与 ``b``（``b`` 可含 ``/`` 表示更深一层）；
  * ``+``   —— 附加说明（不参与解析），如 ``notification`` 行的 ``unsubscribe/`` 子特性。

用法::

    python3 scripts/check-cluster-layout.py
    python3 scripts/check-cluster-layout.py --backend <repo> --doc <DIRECTORY.md>

退出码：0 = 全部通过；1 = 存在硬性违规；2 = 工程路径不存在。
"""
from __future__ import annotations

import argparse
import os
import re
import sys

DEFAULT_MODULE = "scaffold-system"
TABLE_HEADING = "子域簇一览"
TABLE_MODULE_RE = re.compile(r"子域簇一览.*?`(scaffold-[a-z0-9-]+)`")

# 模块名 → 包根后缀。默认由模块名去掉 `scaffold-` 前缀推导；
# system / cms / im / kafka / workbench / workflow 六个模块均符合该规律，无需 override。
# 2026-11-10 P2-3 批次 12 起，原与 `workflow/` 平级的 `approval/` 根包已整体并入
# `workflow/approval/` 簇，故 `scaffold-workflow` 也由默认规则覆盖。
MODULE_PKG_OVERRIDES: dict[str, str] = {}

# §4 落位口径第 8 条 / §6：簇根不得重建的扁平包。
# 注意：簇根 `dto/` 是 §4 契约层**要求**的位置（「一律簇根 dto/，不设 api/dto/」），不在此列。
# `event` / `csv` / `export` / `stats` 等属业务分层名，不是 legacy 扁平包，故不列入。
FORBIDDEN_FLAT = ("controller", "service", "entity", "rules", "port")

# 子特性子簇的识别：§4 表「+」附加说明里以 `xxx/` 形式给出的目录名
# （如 notification 行的 "`unsubscribe/` 子特性"）自带分层骨架，不参与父簇比对。
SUBCLUSTER_TOKEN_RE = re.compile(r"`([a-z0-9]+)/`")

ROW_RE = re.compile(r"^\|\s*`([a-z0-9]+)`\((\d+)\)\s*\|\s*([^|]+?)\s*\|")


def module_pkg_path(module):
    """模块名 → 该模块子域簇所在的 Java 包根（相对后端仓根）。"""
    suffix = MODULE_PKG_OVERRIDES.get(module)
    if suffix is None:
        suffix = module[len("scaffold-"):] if module.startswith("scaffold-") else module
    return os.path.join(module, "src", "main", "java", "com", "acme", "scaffold", suffix)


def split_top(text):
    """按「/」切分，忽略大括号内的 /。"""
    out, buf, depth = [], [], 0
    for ch in text:
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        if ch == "/" and depth == 0:
            out.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    out.append("".join(buf))
    return out


def parse_spec(spec):
    """→ (二级层集合, {层: 该层声明的子目录相对路径集合})"""
    layers, children = set(), {}
    chunks = [c for c in re.split(r"\s*\+\s*", spec)
              if c.strip() and not c.strip().startswith("（") and not c.strip().startswith("`")]
    for chunk in chunks:
        last = None
        for seg in split_top(chunk):
            seg = seg.strip()
            if not seg:
                continue
            if seg.startswith("{") and seg.endswith("}"):
                if last is None:
                    continue
                for alt in seg[1:-1].split(","):
                    alt = alt.strip()
                    if alt:
                        children.setdefault(last, set()).add(alt)
                continue
            if "{" in seg and "}" in seg:
                head, body = seg.split("{", 1)
                head = head.strip()
                layers.add(head)
                last = head
                for alt in body.rstrip("}").split(","):
                    alt = alt.strip()
                    if alt:
                        children.setdefault(head, set()).add(alt)
            else:
                layers.add(seg)
                last = seg
    return layers, children


def expand_with_parents(paths):
    """把 {a/b, c} 展开为含中间层的集合：{c, a, a/b}"""
    out = set()
    for p in paths:
        parts = p.split("/")
        for i in range(1, len(parts) + 1):
            out.add("/".join(parts[:i]))
    return out


def parse_doc(doc_path):
    """从 DIRECTORY.md 解析 §4 全部簇表 → {模块: [(簇名, 文件数, 分层描述)]}"""
    tables, current = {}, None
    with open(doc_path, encoding="utf-8") as fh:
        for line in fh:
            if line.startswith(TABLE_HEADING):
                m = TABLE_MODULE_RE.search(line)
                current = m.group(1) if m else DEFAULT_MODULE
                tables.setdefault(current, [])
                continue
            if current and line.startswith("## "):
                current = None
                continue
            if not current:
                continue
            m = ROW_RE.match(line)
            if m:
                tables[current].append((m.group(1), int(m.group(2)), m.group(3)))
    return tables


def disk_cluster(module_root, cluster):
    """→ (java 文件数, {二级层}, {层: 该层下所有子目录相对路径}, 直挂 domain 的文件)"""
    root = os.path.join(module_root, cluster)
    total, layers, children, flat = 0, set(), {}, []
    for dirpath, _dirnames, filenames in os.walk(root):
        rel = os.path.relpath(dirpath, root)
        if rel != ".":
            parts = rel.split(os.sep)
            layers.add(parts[0])
            if len(parts) >= 2:
                children.setdefault(parts[0], set()).add("/".join(parts[1:]))
        total += sum(1 for f in filenames if f.endswith(".java"))
        if rel == "domain":
            flat.extend(f for f in filenames if f.endswith(".java"))
    return total, layers, children, flat


def parse_subclusters(spec):
    """§4 表「+」附加说明中声明的子特性子簇目录名（如 `unsubscribe/`）。"""
    names = set()
    for chunk in re.split(r"\s*\+\s*", spec):
        for m in SUBCLUSTER_TOKEN_RE.finditer(chunk):
            names.add(m.group(1))
    return names


def check_cluster(module_root, cluster, count, spec, errors):
    """校验单个子域簇；错误追加到 errors。"""
    root = os.path.join(module_root, cluster)
    if not os.path.isdir(root):
        errors.append(f"[缺失簇] {cluster}：磁盘上不存在该子域目录")
        return
    total, layers, children, flat = disk_cluster(module_root, cluster)
    if total != count:
        errors.append(f"[C1 文件数] {cluster}：文档声明 {count} / 磁盘实际 {total}")

    # C4：DO 不得直挂 domain/
    if flat:
        errors.append(f"[C4 DO 落位] {cluster}：{sorted(flat)} 直挂 domain/，"
                      f"应置于 domain/model/")

    # C5：不得重建扁平包
    for bad in FORBIDDEN_FLAT:
        if bad in layers:
            errors.append(f"[C5 扁平包] {cluster}/{bad}/：§6 禁止的扁平包")

    exp_layers, exp_children = parse_spec(spec)
    allowed = parse_subclusters(spec)

    # C2：二级层集合双向一致
    missing = sorted(exp_layers - layers)
    extra = sorted(l for l in layers - exp_layers if l not in allowed)
    if missing:
        errors.append(f"[C2 缺层] {cluster}：文档声明 {missing} / 磁盘缺失")
    if extra:
        errors.append(f"[C2 多出层] {cluster}：磁盘多出 {extra}（文档未声明）")

    # C3：各层子目录集合双向一致
    for layer in sorted(exp_layers | layers):
        if layer in allowed:
            continue  # 子特性子簇自带分层骨架
        decl = expand_with_parents(exp_children.get(layer, set()))
        act = {p for p in children.get(layer, set()) if p.split("/")[0] not in allowed}
        if decl - act:
            errors.append(f"[C3 缺子目录] {cluster}/{layer}：文档声明 {sorted(decl - act)}")
        if act - decl:
            errors.append(f"[C3 多出子目录] {cluster}/{layer}：磁盘多出 {sorted(act - decl)}")


def default_paths():
    backend = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    return backend, os.path.join(backend, "docs", "DIRECTORY.md")


def main(argv=None):
    ap = argparse.ArgumentParser(description="子域簇结构与 §4 簇表一致性校验（D12，多模块）")
    ap.add_argument("--backend", default=None, help="后端仓根（默认由脚本位置推导）")
    ap.add_argument("--doc", default=None, help="DIRECTORY.md 路径（默认 <backend>/docs/DIRECTORY.md）")
    args = ap.parse_args(argv)

    backend, doc = default_paths()
    backend = os.path.abspath(args.backend) if args.backend else backend
    doc = os.path.abspath(args.doc) if args.doc else doc

    if not os.path.isfile(doc):
        print(f"[ERROR] 规范文档不存在: {doc}", file=sys.stderr)
        return 2

    tables = parse_doc(doc)
    if not tables:
        print(f"[ERROR] 未能在 {doc} 中定位「{TABLE_HEADING}」表", file=sys.stderr)
        return 2

    errors, summary = [], []
    for module, rows in tables.items():
        module_root = os.path.join(backend, module_pkg_path(module))
        if not os.path.isdir(module_root):
            print(f"[ERROR] 工程路径不存在: {module_root}（模块 {module}）", file=sys.stderr)
            return 2
        local = []
        for cluster, count, spec in rows:
            check_cluster(module_root, cluster, count, spec, local)
        errors.extend(f"[{module}] {e}" for e in local)
        summary.append((module, module_root, len(rows)))

    print("=" * 64)
    print("子域簇结构校验（D12：§4 簇表 ≡ 磁盘现状）")
    print("=" * 64)
    print(f"规范文档: {doc}")
    for module, module_root, count in summary:
        print(f"模块根:   {module_root}  [{module}] {count} 簇")
    print(f"合计:     {sum(c for _, _, c in summary)} 个子域簇 / {len(summary)} 个模块")
    if errors:
        print(f"\n未通过，共 {len(errors)} 项硬性违规：")
        for e in errors:
            print(f"  - {e}")
        print("\n修复指引见 docs/DIRECTORY.md §4；改目录后须同步该表。")
        return 1
    print("\n子域簇结构校验通过（EXIT=0）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
