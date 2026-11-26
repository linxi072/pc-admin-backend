#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""上传文件魔数安全回归：WebP 校验必须读取 RIFF 容器的 WEBP fourcc。

RIFF 仅是容器前缀，WAV/AVI 同样满足；只校验前 4 字节会让改扩展名伪装通过。
本测试纳入 check-all.py 的 gate-tests。
"""
from __future__ import annotations

import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from gate_lib import BACKEND_ROOT, read_text, walk_files  # noqa: E402

RIFF_PREFIX_RE = re.compile(r"header\[0\]\s*==\s*['\"]R['\"]")
WEBP_FOURCC_RE = re.compile(r"header\[11\]\s*==\s*['\"]P['\"]")


def find_riff_only(text: str) -> list[int]:
    """返回只校验 RIFF 前缀、未校验偏移 8~11 WEBP fourcc 的行号。"""
    if WEBP_FOURCC_RE.search(text):
        return []
    return [text.count("\n", 0, m.start()) + 1 for m in RIFF_PREFIX_RE.finditer(text)]


class FileUploadSecurityTests(unittest.TestCase):
    def test_detector_flags_riff_only_webp_check(self):
        sample = "boolean webp = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';"
        self.assertEqual(find_riff_only(sample), [1])

    def test_detector_accepts_full_webp_fourcc_check(self):
        sample = "boolean webp = header[0] == 'R' && header[9] == 'E' && header[11] == 'P';"
        self.assertEqual(find_riff_only(sample), [])

    def test_repo_has_no_riff_only_webp_check(self):
        violations: list[str] = []
        scanned = 0
        for path in walk_files(BACKEND_ROOT, (".java",)):
            norm = path.replace("\\", "/")
            if "/src/test/" in norm:
                continue
            scanned += 1
            for line in find_riff_only(read_text(path)):
                violations.append(f"{os.path.relpath(path, BACKEND_ROOT)}:{line}")
        self.assertGreater(scanned, 0, "应扫描到后端 Java 源码")
        self.assertEqual(violations, [])


if __name__ == "__main__":
    _ = unittest.main(verbosity=2)
