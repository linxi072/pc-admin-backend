#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前端导出一致性回归：导出失败不得写入页面主错误状态 error.value。

导出是旁路操作，失败应仅用 ElMessage 提示；写入 error.value 会让 v-if="error"
替换整块已加载数据，迫使用户重新查询。本测试纳入 check-all.py 的 gate-tests。
"""
from __future__ import annotations

import os
import re
import unittest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
VIEWS_DIR = os.path.join(REPO_ROOT, "pc-admin-vue3-vite", "src")
SKIP_DIRS = {".git", "node_modules", "dist"}

EXPORT_FN_RE = re.compile(
    r"(?:async\s+)?function\s+(?:download|onExport\w*|export\w*)\s*\([^)]*\)\s*\{", re.I
)
ERROR_ASSIGN_RE = re.compile(r"\berror\.value\s*=")


def iter_vue_files():
    for root, dirs, files in os.walk(VIEWS_DIR):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if name.endswith(".vue"):
                yield os.path.join(root, name)


def find_offending(text: str) -> list[int]:
    """返回违规的函数起始行号。"""
    hits: list[int] = []
    for m in EXPORT_FN_RE.finditer(text):
        depth = 1
        i = m.end()
        while i < len(text) and depth:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        if ERROR_ASSIGN_RE.search(text[m.end():i - 1]):
            hits.append(text.count("\n", 0, m.start()) + 1)
    return hits


class FrontendExportConsistencyTests(unittest.TestCase):
    def test_detector_flags_error_assignment_in_export_handler(self):
        sample = "async function download() { try { error.value = '导出失败' } finally {} }"
        self.assertEqual(find_offending(sample), [1])

    def test_detector_accepts_message_only_export_handler(self):
        sample = "async function download() { ElMessage.error('导出失败') }"
        self.assertEqual(find_offending(sample), [])

    def test_detector_ignores_error_assignment_in_load_handler(self):
        sample = "async function load() { error.value = '加载失败' }"
        self.assertEqual(find_offending(sample), [])

    def test_all_export_handlers_are_consistent(self):
        violations: list[str] = []
        scanned = 0
        for path in iter_vue_files():
            scanned += 1
            for line in find_offending(open(path, encoding="utf-8").read()):
                violations.append(f"{os.path.relpath(path, REPO_ROOT)}:{line}")
        self.assertGreater(scanned, 0, "应扫描到前端视图文件")
        self.assertEqual(violations, [])


if __name__ == "__main__":
    _ = unittest.main(verbosity=2)
