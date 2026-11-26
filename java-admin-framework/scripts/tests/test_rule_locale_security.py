#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生产 Java Locale 安全回归：标识符/枚举/状态码归一不得使用平台默认 Locale。

tr-TR 下 'i'.toUpperCase() 会得到带点 'İ'，使 INFO / DISK_USAGE / PUBLISHED /
PROCESSING / INITIATOR / MINUTES / API_CALL 等解析失配。此前门禁只扫规则包，
本轮扩展为全部 src/main/java，测试源码不纳入。纳入 check-all.py 的 gate-tests。
"""
from __future__ import annotations

import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from gate_lib import BACKEND_ROOT, read_text, walk_files  # noqa: E402

UNQUALIFIED_CASE_RE = re.compile(r"\.(?:toUpperCase|toLowerCase)\(\)")


def is_production_java(rel_path: str) -> bool:
    norm = rel_path.replace("\\", "/")
    return "/src/main/java/" in norm and "/src/test/" not in norm and norm.endswith(".java")


def find_unqualified_case_calls(text: str) -> list[int]:
    return [text.count("\n", 0, m.start()) + 1 for m in UNQUALIFIED_CASE_RE.finditer(text)]


class ProductionLocaleSecurityTests(unittest.TestCase):
    def test_detector_flags_unqualified_case_call(self):
        self.assertEqual(find_unqualified_case_calls("value.trim().toUpperCase()"), [1])

    def test_detector_accepts_locale_root_call(self):
        text = "value.trim().toUpperCase(Locale.ROOT)"
        self.assertEqual(find_unqualified_case_calls(text), [])

    def test_source_filter_covers_non_rule_main_code(self):
        self.assertTrue(is_production_java("scaffold-job/src/main/java/com/acme/JobService.java"))
        self.assertFalse(is_production_java("scaffold-job/src/test/java/com/acme/JobServiceTest.java"))

    def test_all_production_sources_have_no_default_locale_case_calls(self):
        violations: list[str] = []
        scanned = 0
        for path in walk_files(BACKEND_ROOT, (".java",)):
            rel = os.path.relpath(path, BACKEND_ROOT).replace("\\", "/")
            if not is_production_java(rel):
                continue
            scanned += 1
            for line in find_unqualified_case_calls(read_text(path)):
                violations.append(f"{rel}:{line}")
        self.assertGreater(scanned, 0, "应扫描到生产 Java 源码")
        self.assertEqual(violations, [])


if __name__ == "__main__":
    _ = unittest.main(verbosity=2)
