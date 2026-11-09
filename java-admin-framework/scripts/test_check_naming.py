#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-naming.py 的行为回归测试（零依赖，仅标准库）。

通过 subprocess 调用真实脚本对临时 Java 片段做端到端断言，覆盖：
  * 干净文件 -> EXIT=0
  * 四类命名违规 -> 对应 N1/N2/N3/N4 命中且 EXIT=1（硬项 N1/N2 触发失败）
  * 构造函数不被误判为方法（N3）
  * serialVersionUID 等序列化惯例名豁免 N2
"""
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-naming.py")


def run_on(src):
    d = tempfile.mkdtemp()
    p = os.path.join(d, "Sample.java")
    with open(p, "w", encoding="utf-8") as fh:
        fh.write(src)
    proc = subprocess.run([sys.executable, SCRIPT, "--src", d, "--quiet"],
                          capture_output=True, text=True)
    return proc.returncode, proc.stdout + proc.stderr


class CheckNamingTest(unittest.TestCase):
    def test_clean(self):
        rc, _ = run_on(
            "package x;\n"
            "public class GoodClass {\n"
            "    public static final int MAX_VALUE = 1;\n"
            "    private Long userId;\n"
            "    public Result goodMethod(Long someParam) { return null; }\n"
            "}\n")
        self.assertEqual(rc, 0)

    def test_hard_violations(self):
        rc, out = run_on(
            "package x;\n"
            "public class badName {\n"                 # N1: 非 PascalCase
            "    public static final int maxValue = 1;\n"   # N2: 非 UPPER_SNAKE
            "    private Long user_id;\n"             # N4: 非 camelCase
            "    public Result bad_Method() { return null; }\n"  # N3: 非 camelCase
            "}\n")
        self.assertEqual(rc, 1)
        self.assertIn("N1", out)
        self.assertIn("N2", out)
        self.assertIn("N3", out)
        self.assertIn("N4", out)

    def test_constructor_not_flagged(self):
        # 构造函数名 == 类型名，不应触发 N3
        rc, out = run_on(
            "package x;\n"
            "public class Widget {\n"
            "    public Widget() {}\n"                # 构造函数，非方法
            "    public void build() {}\n"            # 合规方法
            "}\n")
        self.assertEqual(rc, 0)
        self.assertNotIn("N3", out)

    def test_serialversionuid_exempt(self):
        rc, _ = run_on(
            "package x;\n"
            "public class Widget implements java.io.Serializable {\n"
            "    private static final long serialVersionUID = 1L;\n"
            "}\n")
        self.assertEqual(rc, 0)


if __name__ == "__main__":
    unittest.main()
