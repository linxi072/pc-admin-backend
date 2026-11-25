#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前端筛选一致性回归：定义了 onSearch()（重置页码）的视图，筛选框回车不得绕过它直接调用 load()。

此前的缺陷：第 2 页以上改筛选条件后按回车，仍按旧页码请求，得到空页或错位数据。
本测试纳入 check-all.py 的 gate-tests 统一入口，防止同类遗漏再次出现。
"""
from __future__ import annotations

import os
import re
import unittest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
VIEWS_DIR = os.path.join(REPO_ROOT, "pc-admin-vue3-vite", "src", "views")
SKIP_DIRS = {".git", "node_modules", "dist"}


def iter_vue_files():
    """就近遍历前端视图目录，避免依赖 scripts/gate_lib 的隐式相对导入。"""
    for root, dirs, files in os.walk(VIEWS_DIR):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if name.endswith(".vue"):
                yield os.path.join(root, name)


def read_text(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()

ENTER_LOAD_RE = re.compile(r"@keyup\.enter\s*=\s*['\"]\s*load\s*['\"]")
SEARCH_BUTTON_LOAD_RE = re.compile(
    r":icon\s*=\s*['\"]Search['\"]\s+@click\s*=\s*['\"]\s*load\s*['\"]"
)
SIZE_CHANGE_LOAD_RE = re.compile(r"@size-change\s*=\s*['\"]\s*load(?:Restrictions)?\s*['\"]")
HAS_ON_SEARCH_RE = re.compile(r"(?:function\s+onSearch\s*\(|const\s+onSearch\s*=)")


def find_offending(text: str) -> list[tuple[int, str]]:
    """返回 (行号, 命中片段)；仅对定义了 onSearch 的视图判定。

    覆盖三个入口：筛选框回车、Search 图标的「查询」按钮、分页 size 切换。
    刷新/重试按钮（@click="load" 但无 Search 图标）不应重置页码，故不纳入。
    """
    if not HAS_ON_SEARCH_RE.search(text):
        return []
    patterns = (ENTER_LOAD_RE, SEARCH_BUTTON_LOAD_RE, SIZE_CHANGE_LOAD_RE)
    return [
        (text.count("\n", 0, m.start()) + 1, m.group(0))
        for pattern in patterns
        for m in pattern.finditer(text)
    ]


class FrontendSearchConsistencyTests(unittest.TestCase):
    def test_detector_flags_direct_load_on_enter(self):
        sample = '<el-input @keyup.enter="load" />\nfunction onSearch() { query.page = 1; load() }'
        self.assertEqual(len(find_offending(sample)), 1)

    def test_detector_flags_search_button_direct_load(self):
        sample = '<el-button :icon="Search" @click="load">查询</el-button>\nfunction onSearch() { query.page = 1 }'
        self.assertEqual(len(find_offending(sample)), 1)

    def test_detector_flags_size_change_direct_load(self):
        sample = '<el-pagination @size-change="load" />\nfunction onSearch() { query.page = 1 }'
        self.assertEqual(len(find_offending(sample)), 1)
        restricted = '<el-pagination @size-change="loadRestrictions" />\nfunction onSearch() { query.page = 1 }'
        self.assertEqual(len(find_offending(restricted)), 1)

    def test_detector_accepts_size_change_handler(self):
        sample = '<el-pagination @size-change="onSizeChange" />\nfunction onSearch() { query.page = 1 }'
        self.assertEqual(find_offending(sample), [])

    def test_detector_accepts_refresh_button_direct_load(self):
        sample = '<el-button :icon="Refresh" @click="load">刷新</el-button>\nfunction onSearch() { query.page = 1 }'
        self.assertEqual(find_offending(sample), [])

    def test_detector_accepts_on_search_handler(self):
        sample = '<el-input @keyup.enter="onSearch" />\nfunction onSearch() { query.page = 1; load() }'
        self.assertEqual(find_offending(sample), [])

    def test_detector_ignores_views_without_on_search(self):
        sample = '<el-input @keyup.enter="load" />\nasync function load() { query.page = 1 }'
        self.assertEqual(find_offending(sample), [])

    def test_all_views_are_consistent(self):
        violations: list[str] = []
        scanned = 0
        for path in iter_vue_files():
            scanned += 1
            for line, snippet in find_offending(read_text(path)):
                violations.append(f"{os.path.relpath(path, REPO_ROOT)}:{line} {snippet}")
        self.assertGreater(scanned, 0, "应扫描到前端视图文件")
        self.assertEqual(violations, [])


if __name__ == "__main__":
    _ = unittest.main(verbosity=2)
