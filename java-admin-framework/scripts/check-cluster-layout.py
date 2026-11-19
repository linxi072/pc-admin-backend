#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-cluster-layout.py：scaffold-system 子域簇结构门禁（D12 重刻版）。

规则：scaffold-system 顶层子域簇 ≡ 权威簇清单（取自 docs/RUOYI-ALIGNMENT.md §4 收敛后的磁盘现状，
V1.1.0 快照）。新增/删除子域簇必须同步更新本清单，防止"文档与磁盘两套事实"。

退出码：0 通过；1 漂移（阻断）。
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gate_lib import BACKEND_ROOT, fail, finish, info  # noqa: E402

# V1.1.0 快照：scaffold-system 子域簇（36 个，按字母序）
SYSTEM_CLUSTERS_WHITELIST = sorted([
    "announcement", "apiresource", "audit", "bootstrap", "config", "dashboard",
    "dict", "export", "file", "form", "importer", "log", "maskstrategy",
    "menu", "message", "notifydigest", "notifylog", "notifymgmt", "notifypref",
    "notifyquiet", "notifysupport", "notification", "oauth2", "online", "openapi",
    "org", "password", "permission", "post", "profile", "role", "smslog",
    "support", "tenant", "token", "user",
])


def main() -> int:
    sys_pkg = os.path.join(
        BACKEND_ROOT, "scaffold-system", "src", "main", "java", "com", "acme", "scaffold", "system")
    if not os.path.isdir(sys_pkg):
        fail("scaffold-system 主包目录缺失")
        return finish("check-cluster-layout")

    on_disk = sorted(
        d for d in os.listdir(sys_pkg)
        if os.path.isdir(os.path.join(sys_pkg, d)) and not d.startswith("__")
    )
    extra = sorted(set(on_disk) - set(SYSTEM_CLUSTERS_WHITELIST))
    missing = sorted(set(SYSTEM_CLUSTERS_WHITELIST) - set(on_disk))
    if extra:
        fail(f"子域簇新增未登记：{extra}（请同步 SYSTEM_CLUSTERS_WHITELIST 与文档 §4）")
    if missing:
        fail(f"权威子域簇在磁盘缺失：{missing}")
    info(f"子域簇 {len(on_disk)} 个（权威清单 {len(SYSTEM_CLUSTERS_WHITELIST)} 个）")
    return finish("check-cluster-layout")


if __name__ == "__main__":
    sys.exit(main())
