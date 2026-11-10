#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-cluster-layout.py 的行为回归测试（零依赖，仅标准库）。

通过 subprocess 调用真实脚本，对临时构造的「规范文档 + 子域目录树」夹具做端到端断言，
覆盖 §4 簇表的五类校验（C1 文件数 / C2 层集合 / C3 子目录集合 / C4 DO 落位 / C5 扁平包）、
「子特性子簇不被误判」这一豁免路径，以及多模块簇表的解析与错误归属。
"""
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-cluster-layout.py")

DOC_TEMPLATE = """# 目录结构规范

## 4. `scaffold-system` 子域分层骨架

子域簇一览（括号内为 Java 文件数）：

| 簇 | 簇内分层 | 职责 |
|---|---|---|
| `demo`(4) | api/application/domain{model}/dto | 演示簇 |
"""

# 多模块夹具：一张旧式无模块名表（默认 system）+ 一张显式标注 cms 的表。
MULTI_DOC_TEMPLATE = """# 目录结构规范

## 4. 子域分层骨架

子域簇一览（`scaffold-system`，括号内为 Java 文件数）：

| 簇 | 簇内分层 | 职责 |
|---|---|---|
| `demo`(4) | api/application/domain{model}/dto | 演示簇 |

子域簇一览（`scaffold-cms`，括号内为 Java 文件数）：

| 簇 | 簇内分层 | 职责 |
|---|---|---|
| `article`(3) | api/domain{model}/dto | 文章 |
| `support`(1) | （平铺） | 共享枚举 |
"""

SYSTEM_REL = os.path.join(
    "scaffold-system", "src", "main", "java", "com", "acme", "scaffold", "system")
CMS_REL = os.path.join(
    "scaffold-cms", "src", "main", "java", "com", "acme", "scaffold", "cms")
MODULE_RELS = {"scaffold-system": SYSTEM_REL, "scaffold-cms": CMS_REL}


def build_fixture(doc_text, files):
    """构造临时后端仓：docs/DIRECTORY.md + scaffold-system 子域树。files = [(相对路径, 内容)]"""
    return build_fixture_multi(doc_text, {"scaffold-system": files})


def build_fixture_multi(doc_text, per_module):
    """构造多模块临时后端仓。per_module = {模块名: [(相对路径, 内容)]}"""
    root = tempfile.mkdtemp()
    docs = os.path.join(root, "docs")
    os.makedirs(docs)
    with open(os.path.join(docs, "DIRECTORY.md"), "w", encoding="utf-8") as fh:
        fh.write(doc_text)
    for module, files in per_module.items():
        module_rel = MODULE_RELS[module]
        os.makedirs(os.path.join(root, module_rel), exist_ok=True)
        for rel, content in files:
            p = os.path.join(root, module_rel, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "w", encoding="utf-8") as fh:
                fh.write(content)
    return root


def run(root):
    proc = subprocess.run(
        [sys.executable, SCRIPT, "--backend", root,
         "--doc", os.path.join(root, "docs", "DIRECTORY.md")],
        capture_output=True, text=True)
    return proc.returncode, proc.stdout + proc.stderr


CLEAN_FILES = [
    ("demo/api/DemoController.java", "package a;\nclass DemoController {}\n"),
    ("demo/application/DemoService.java", "package b;\nclass DemoService {}\n"),
    ("demo/domain/model/DemoDO.java", "package c;\nclass DemoDO {}\n"),
    ("demo/dto/DemoView.java", "package d;\nclass DemoView {}\n"),
]

CMS_CLEAN_FILES = [
    ("article/api/ArticleController.java", "package e;\nclass ArticleController {}\n"),
    ("article/domain/model/ArticleDO.java", "package f;\nclass ArticleDO {}\n"),
    ("article/dto/ArticleView.java", "package g;\nclass ArticleView {}\n"),
    ("support/CmsStatus.java", "package h;\nenum CmsStatus {}\n"),
]


class CheckClusterLayoutTest(unittest.TestCase):
    def test_clean(self):
        rc, out = run(build_fixture(DOC_TEMPLATE, CLEAN_FILES))
        self.assertEqual(rc, 0, out)
        self.assertIn("子域簇结构校验通过", out)

    def test_file_count_mismatch(self):
        files = CLEAN_FILES[:3]
        rc, out = run(build_fixture(DOC_TEMPLATE, files))
        self.assertEqual(rc, 1)
        self.assertIn("C1 文件数", out)

    def test_missing_layer(self):
        # 磁盘缺 dto/ 层
        files = [f for f in CLEAN_FILES if "/dto/" not in f[0]]
        rc, out = run(build_fixture(DOC_TEMPLATE, files))
        self.assertEqual(rc, 1)
        self.assertIn("C2 缺层", out)

    def test_extra_layer(self):
        files = CLEAN_FILES + [("demo/config/DemoConfig.java", "package e;\nclass DemoConfig {}\n")]
        rc, out = run(build_fixture(DOC_TEMPLATE, files))
        self.assertEqual(rc, 1)
        self.assertIn("C2 多出层", out)

    def test_missing_subdirectory(self):
        # 声明了 domain{model}，磁盘只有 domain/ 下的直挂文件
        files = [f for f in CLEAN_FILES if "/domain/model/" not in f[0]]
        files.append(("demo/domain/DemoDO.java", "package c;\nclass DemoDO {}\n"))
        rc, out = run(build_fixture(DOC_TEMPLATE, files))
        self.assertEqual(rc, 1)
        self.assertIn("C4 DO 落位", out)
        self.assertIn("C3 缺子目录", out)

    def test_extra_subdirectory(self):
        files = CLEAN_FILES + [("demo/domain/rule/DemoRules.java", "package f;\nclass DemoRules {}\n")]
        rc, out = run(build_fixture(DOC_TEMPLATE, files))
        self.assertEqual(rc, 1)
        self.assertIn("C3 多出子目录", out)

    def test_do_directly_under_domain(self):
        # DO 直挂 domain/ -> C4
        files = [f for f in CLEAN_FILES if "/domain/model/" not in f[0]]
        files.append(("demo/domain/DemoDO.java", "package c;\nclass DemoDO {}\n"))
        rc, out = run(build_fixture(DOC_TEMPLATE, files))
        self.assertEqual(rc, 1)
        self.assertIn("C4 DO 落位", out)

    def test_flat_package_forbidden(self):
        files = CLEAN_FILES + [("demo/service/DemoService2.java", "package g;\nclass DemoService2 {}\n")]
        rc, out = run(build_fixture(DOC_TEMPLATE, files))
        self.assertEqual(rc, 1)
        self.assertIn("C5 扁平包", out)

    def test_subcluster_exempt(self):
        """`unsubscribe/` 子特性自带分层骨架，不应被判为未声明层 / 未声明子目录。"""
        doc = DOC_TEMPLATE.replace(
            "| `demo`(4) | api/application/domain{model}/dto | 演示簇 |",
            "| `demo`(6) | api/application/domain{model}/dto + `unsubscribe/` 子特性 | 演示簇 |")
        files = CLEAN_FILES + [
            ("demo/unsubscribe/api/UnsubController.java", "package h;\nclass UnsubController {}\n"),
            ("demo/unsubscribe/application/UnsubService.java", "package i;\nclass UnsubService {}\n"),
        ]
        rc, out = run(build_fixture(doc, files))
        self.assertEqual(rc, 0, out)

    def test_missing_cluster(self):
        rc, out = run(build_fixture(DOC_TEMPLATE, []))
        self.assertEqual(rc, 1)
        self.assertIn("缺失簇", out)

    def test_bad_backend_path(self):
        proc = subprocess.run(
            [sys.executable, SCRIPT, "--backend", os.path.join(tempfile.gettempdir(), "__no_such__")],
            capture_output=True, text=True)
        self.assertEqual(proc.returncode, 2)

    # ---------------- 多模块簇表（P2-3 前置） ----------------

    def test_multi_module_tables_pass(self):
        """两张簇表分别归属 system / cms，各自独立校验且都通过。"""
        root = build_fixture_multi(MULTI_DOC_TEMPLATE, {
            "scaffold-system": CLEAN_FILES,
            "scaffold-cms": CMS_CLEAN_FILES,
        })
        rc, out = run(root)
        self.assertEqual(rc, 0, out)
        self.assertIn("子域簇结构校验通过", out)
        self.assertIn("scaffold-cms", out)
        self.assertIn("3 个子域簇 / 2 个模块", out)

    def test_multi_module_error_attributed(self):
        """cms 簇文件数不符时，错误须带模块名以便定位，且不影响 system 簇。"""
        cms_files = [f for f in CMS_CLEAN_FILES if "article/dto/" not in f[0]]
        root = build_fixture_multi(MULTI_DOC_TEMPLATE, {
            "scaffold-system": CLEAN_FILES,
            "scaffold-cms": cms_files,
        })
        rc, out = run(root)
        self.assertEqual(rc, 1)
        self.assertIn("[scaffold-cms]", out)
        self.assertIn("C1 文件数", out)
        self.assertIn("article", out)

    def test_multi_module_missing_root_returns_2(self):
        """文档声明了模块但磁盘包根不存在 → 路径级错误 2，而非静默跳过。"""
        doc = MULTI_DOC_TEMPLATE.replace(
            "子域簇一览（`scaffold-cms`，括号内为 Java 文件数）：",
            "子域簇一览（`scaffold-nosuch`，括号内为 Java 文件数）：")
        root = build_fixture_multi(doc, {
            "scaffold-system": CLEAN_FILES,
            "scaffold-cms": CMS_CLEAN_FILES,
        })
        rc, out = run(root)
        self.assertEqual(rc, 2)
        self.assertIn("工程路径不存在", out)


if __name__ == "__main__":
    unittest.main()
