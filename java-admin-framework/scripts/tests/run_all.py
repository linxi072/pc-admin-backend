#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁脚本单测统一入口：发现并执行 scripts/tests/test_*.py。"""
from __future__ import annotations

import os
import sys
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, SCRIPTS_DIR)
TESTS_DIR = os.path.join(SCRIPTS_DIR, "tests")

suite = unittest.defaultTestLoader.discover(TESTS_DIR, pattern="test_*.py")
result = unittest.TextTestRunner(verbosity=2).run(suite)
sys.exit(0 if result.wasSuccessful() else 1)
