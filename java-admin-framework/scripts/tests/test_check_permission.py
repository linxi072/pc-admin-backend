#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""权限门禁单测：Java 侧权限码只从生产源码提取，测试夹具不得充当接口实现。"""
from __future__ import annotations

import importlib.util
import os
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location(
        "check_permission", os.path.join(SCRIPTS_DIR, "check-permission.py"))
cp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cp)


class CheckPermissionTests(unittest.TestCase):
    def test_extract_permission_codes_from_java_text(self):
        text = """
        @PreAuthorize("hasAuthority('system:user:list')")
        @PreAuthorize('hasAuthority("system:role:list")')
        """
        self.assertEqual(
            cp.permission_codes_from_text(text),
            {"system:user:list", "system:role:list"},
        )

    def test_source_filter_only_allows_main_java(self):
        self.assertTrue(cp.is_production_java("/repo/scaffold-system/src/main/java/com/acme/Foo.java"))
        self.assertFalse(cp.is_production_java("/repo/scaffold-system/src/test/java/com/acme/FooTest.java"))
        self.assertFalse(cp.is_production_java("/repo/scaffold-system/src/main/resources/db/init/dml.sql"))

    def test_repo_scan_has_real_production_permission_codes(self):
        codes = cp.java_permission_codes()
        self.assertGreater(len(codes), 0)
        self.assertTrue(any(code.startswith("system:") for code in codes))


if __name__ == "__main__":
    _ = unittest.main(verbosity=2)
