#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""规则层 Locale 安全回归：枚举/状态码归一不得使用平台默认 Locale。

tr-TR 下 'i'.toUpperCase() 会得到带点 'İ'，使 INFO / DISK_USAGE / PUBLISHED 等解析失配。
本测试纳入 check-all.py 的 gate-tests。
"""
from __future__ import annotations

import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from gate_lib import BACKEND_ROOT, read_text, walk_files  # noqa: E402

UNQUALIFIED_CASE_RE = re.compile(r"\.(?:toUpperCase|toLowerCase)\(\)")


def is_rule_source(rel_path: str) -> bool:
    return "/rule/" in rel_path or "/rules/" in rel_path or rel_path.endswith("IdempotencyStatus.java")


def find_unqualified_case_calls(text: str) -> list[int]:
    return [text.count("\n", 0, m.start()) + 1 for m in UNQUALIFIED_CASE_RE.finditer(text)]


class RuleLocaleSecurityTests(unittest.TestCase):
    def test_detector_flags_unqualified_case_call(self):
        self.assertEqual(find_unqualified_case_calls("value.trim().toUpperCase()"), [1])

    def test_detector_accepts_locale_root_call(self):
        text = "value.trim().toUpperCase(Locale.ROOT)"
        self.assertEqual(find_unqualified_case_calls(text), [])

    def test_rule_sources_have_no_default_locale_case_calls(self):
        violations: list[str] = []
        scanned = 0
        for path in walk_files(BACKEND_ROOT, (".java",)):
            norm = path.replace("\\", "/")
            rel = os.path.relpath(path, BACKEND_ROOT).replace("\\", "/")
            if "/src/test/" in norm or not is_rule_source(rel):
                continue
            scanned += 1
            for line in find_unqualified_case_calls(read_text(path)):
                violations.append(f"{rel}:{line}")
        self.assertGreater(scanned, 0, "应扫描到规则层 Java 源码")
        self.assertEqual(violations, [])


if __name__ == "__main__":
    _ = unittest.main(verbosity=2)
