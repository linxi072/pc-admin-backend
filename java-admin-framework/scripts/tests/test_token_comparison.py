#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""令牌比较安全门禁：生产 Java 不得用 String.equals 直接比对 token/secret/signature。

统一走 com.acme.scaffold.common.security.ConstantTime.equals，避免匿名端点的时序侧信道。
本测试纳入 check-all.py 的 gate-tests。
"""
from __future__ import annotations

import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from gate_lib import BACKEND_ROOT, read_text, walk_files  # noqa: E402

SENSITIVE_NAMES = r"(?:token|secret|signature)"
DIRECT_EQUALS_RE = re.compile(
    rf"\.equals(?:IgnoreCase)?\(\s*{SENSITIVE_NAMES}\s*\)"
    rf"|\b{SENSITIVE_NAMES}\s*\.\s*equals(?:IgnoreCase)?\s*\(",
    re.I,
)


def find_direct_comparisons(text: str) -> list[int]:
    return [text.count("\n", 0, m.start()) + 1 for m in DIRECT_EQUALS_RE.finditer(text)]


class TokenComparisonTests(unittest.TestCase):
    def test_detector_flags_direct_token_equals(self):
        self.assertEqual(find_direct_comparisons("if (!expected.equals(token)) {}"), [1])

    def test_detector_accepts_constant_time_equals(self):
        self.assertEqual(find_direct_comparisons("if (!ConstantTime.equals(expected, token)) {}"), [])

    def test_production_code_has_no_direct_sensitive_comparisons(self):
        violations: list[str] = []
        scanned = 0
        for path in walk_files(BACKEND_ROOT, (".java",)):
            norm = path.replace("\\", "/")
            if "/src/main/java/" not in norm or "/src/test/" in norm:
                continue
            scanned += 1
            for line in find_direct_comparisons(read_text(path)):
                violations.append(f"{os.path.relpath(path, BACKEND_ROOT)}:{line}")
        self.assertGreater(scanned, 0, "应扫描到生产 Java 源码")
        self.assertEqual(violations, [])


if __name__ == "__main__":
    _ = unittest.main(verbosity=2)
