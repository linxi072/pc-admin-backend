#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-directory.py 的行为回归测试（零依赖，仅标准库）。

用临时目录构造最小工程骨架，覆盖 D1–D7 的合规与违规分支，确保守护脚本本身可信：

- 合规骨架 -> EXIT=0；
- 每类违规（未登记包根 / 未登记模块 / 根级 Java / 非 admin 配置 / 根级 CSS /
  样式缺失 / 常量类缺失 / 文档缺失 / 索引死链 / 子模块写死版本）-> EXIT=1 且命中对应规则号；
- 属性占位版本（${...}）合法；工程路径不存在 -> EXIT=2。

运行：
  python3 -m unittest discover -s scripts -p "test_*.py" -v
"""
from __future__ import annotations

import contextlib
import importlib.util
import io
import os
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CHECKER_PATH = os.path.join(SCRIPT_DIR, "check-directory.py")


def load_checker():
    """以 importlib 加载连字符文件名的校验器模块（每次返回全新模块，隔离状态）。"""
    spec = importlib.util.spec_from_file_location("check_directory_under_test", CHECKER_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class DirectoryCheckerTestCase(unittest.TestCase):
    """公共夹具：构造最小后端 + 前端工程骨架。"""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        base = Path(tmp.name)
        self.backend = base / "java-admin-framework"
        self.frontend = base / "pc-admin-vue3-vite"
        self._make_repo()
        self.mod = load_checker()

    @staticmethod
    def _write(path, text=""):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def _pkg_root(self, module):
        return self.backend / module / "src" / "main" / "java" / "com" / "acme" / "scaffold"

    def _make_repo(self):
        docs = self.backend / "docs"
        for name in ("DIRECTORY.md", "RUOYI-ALIGNMENT.md", "MODULES.md"):
            self._write(docs / name, "# " + name)
        self._write(
            docs / "README.md",
            "[目录](DIRECTORY.md) [若依对齐](RUOYI-ALIGNMENT.md)",
        )

        for module, roots in (
            ("scaffold-common", ("common", "jooq")),
            ("scaffold-system", ("system",)),
            ("scaffold-admin", ("admin", "config")),
        ):
            pkg = self._pkg_root(module)
            for root in roots:
                (pkg / root).mkdir(parents=True, exist_ok=True)
            self._write(
                self.backend / module / "pom.xml",
                "<project><dependencies></dependencies></project>",
            )

        self._write(
            self._pkg_root("scaffold-admin") / "FrameworkApplication.java",
            "package com.acme.scaffold;",
        )
        self._write(self.backend / "scaffold-admin" / "src" / "main" / "resources" / "application.yml")
        self._write(
            self._pkg_root("scaffold-common") / "common" / "constant" / "CommonConstants.java",
            "package com.acme.scaffold.common.constant;",
        )

        styles = self.frontend / "src" / "styles"
        for name in ("base.css", "theme-tokens.css", "element-theme.css"):
            self._write(styles / name, "/* ok */")

    def run_checker(self, backend=None, frontend=None):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc = self.mod.main([
                "--backend", str(backend or self.backend),
                "--frontend", str(frontend or self.frontend),
            ])
        return rc, buf.getvalue()

    def assert_violation(self, rule, out):
        self.assertIn(rule, out)
        self.assertIn("未通过", out)

    # ---- 合规 ----

    def test_compliant_repo_passes(self):
        rc, out = self.run_checker()
        self.assertEqual(0, rc)
        self.assertIn("目录约定校验通过", out)

    def test_property_placeholder_version_is_allowed(self):
        self._write(
            self.backend / "scaffold-system" / "pom.xml",
            "<project><dependencies><dependency>"
            "<groupId>com.acme.scaffold</groupId><artifactId>scaffold-common</artifactId>"
            "<version>${project.version}</version>"
            "</dependency></dependencies></project>",
        )
        rc, _ = self.run_checker()
        self.assertEqual(0, rc)

    # ---- D1/D2 包根与启动类 ----

    def test_unregistered_package_root_fails(self):
        (self._pkg_root("scaffold-system") / "legacy").mkdir(parents=True)
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D1 未登记顶层包根", out)

    def test_unregistered_module_fails(self):
        (self.backend / "scaffold-new" / "src" / "main" / "java").mkdir(parents=True)
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D1 模块未登记", out)

    def test_root_level_java_in_non_admin_fails(self):
        self._write(self._pkg_root("scaffold-system") / "Stray.java", "package com.acme.scaffold;")
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D2", out)

    # ---- D3 运行期配置 ----

    def test_runtime_config_outside_admin_fails(self):
        self._write(
            self.backend / "scaffold-system" / "src" / "main" / "resources" / "application-prod.yml",
        )
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D3", out)

    # ---- D4 前端样式 ----

    def test_frontend_root_css_fails(self):
        self._write(self.frontend / "src" / "styles.css", "/* stray */")
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D4 前端 src/ 根级散落 CSS", out)

    def test_missing_style_file_fails(self):
        (self.frontend / "src" / "styles" / "base.css").unlink()
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D4 缺少全局样式文件", out)

    # ---- D5 常量单一事实来源 ----

    def test_missing_common_constants_fails(self):
        (self._pkg_root("scaffold-common") / "common" / "constant" / "CommonConstants.java").unlink()
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D5", out)

    # ---- D6 文档与索引 ----

    def test_missing_doc_fails(self):
        (self.backend / "docs" / "DIRECTORY.md").unlink()
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D6 缺少规范文档", out)

    def test_broken_index_link_fails(self):
        self._write(self.backend / "docs" / "README.md", "[目录](DIRECTORY.md)")
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D6 docs/README.md 索引缺少引用", out)

    # ---- D7 版本管理 ----

    def test_concrete_version_in_child_pom_fails(self):
        self._write(
            self.backend / "scaffold-system" / "pom.xml",
            "<project><dependencies><dependency>"
            "<groupId>com.github.luben</groupId><artifactId>zstd-jni</artifactId>"
            "<version>1.5.5-11</version>"
            "</dependency></dependencies></project>",
        )
        rc, out = self.run_checker()
        self.assertEqual(1, rc)
        self.assert_violation("D7", out)

    # ---- 路径 ----

    def test_missing_backend_returns_2(self):
        rc, _ = self.run_checker(backend=self.backend / "does-not-exist")
        self.assertEqual(2, rc)


if __name__ == "__main__":
    unittest.main(verbosity=2)
