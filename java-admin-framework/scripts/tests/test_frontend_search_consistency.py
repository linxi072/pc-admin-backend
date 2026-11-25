#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前端筛选一致性回归：定义了 onSearch()（重置页码）的视图，筛选框回车不得绕过它直接调用 load()。

此前的缺陷：第 2 页以上改筛选条件后按回车，仍按旧页码请求，得到空页或错位数据。
本测试纳入 check-all.py 的 gate-tests 统一入口，防止同类遗漏再次出现。
"""
from __future__ import annotations

import os
import re
import sys
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, SCRIPTS_DIR)

from gate_lib import REPO_ROOT, read_text, rel, walk_files  # noqa: E402

ENTER_LOAD_RE = re.compile(r"@keyup\.enter\s*=\s*['\"]\s*load\s*['\"]")
HAS_ON_SEARCH_RE = re.compile(r"(?:function\s+onSearch\s*\(|const\s+onSearch\s*=)")


def find_offending(text: str) -> list[tuple[int, str]]:
    """返回 (行号, 命中片段)；仅对定义了 onSearch 的视图判定。"""
    if not HAS_ON_SEARCH_RE.search(text):
        return []
    return [
        (text.count("\n", 0, m.start()) + 1, m.group(0))
        for m in ENTER_LOAD_RE.finditer(text)
    ]


class FrontendSearchConsistencyTests(unittest.TestCase):
    def test_detector_flags_direct_load_on_enter(self):
        sample = '<el-input @keyup.enter="load" />\nfunction onSearch() { query.page = 1; load() }'
        self.assertEqual(len(find_offending(sample)), 1)

    def test_detector_accepts_on_search_handler(self):
        sample = '<el-input @keyup.enter="onSearch" />\nfunction onSearch() { query.page = 1; load() }'
        self.assertEqual(find_offending(sample), [])

    def test_detector_ignores_views_without_on_search(self):
        sample = '<el-input @keyup.enter="load" />\nasync function load() { query.page = 1 }'
        self.assertEqual(find_offending(sample), [])

    def test_all_views_are_consistent(self):
        violations = []
        scanned = 0
        for path in walk_files(REPO_ROOT, (".vue",)):
            scanned += 1
            for line, snippet in find_offending(read_text(path)):
                violations.append(f"{rel(path)}:{line} {snippet}")
        self.assertGreater(scanned, 0, "应扫描到前端视图文件")
        self.assertEqual(violations, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
