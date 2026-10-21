#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-conventions.py 的行为回归测试（零依赖，仅标准库）。

覆盖：
- H4 数据类放置（硬规则，V1.0.2 起由 W3 升级）：
  * 违规（DTO/Request/DO 落在无 dto/domain 段的包）-> EXIT 1，且输出含 H4 标识、
    违规类名与应归位子包的修复指引；
  * 合规路径：dto/vo 子包、domain/entity 子包、包路径任意一段命中
    （如 workflow.dto.design 的二层结构）；
  * 边界：类名恰等于后缀（如 ``DO``）不误判。
- H1/H2/H3 硬规则回归：H4 升级不破坏既有硬规则行为。
- W* 警告不阻断：仅 W1/W2 时 EXIT 仍为 0。

运行：
  python3 -m unittest discover -s scripts -p "test_*.py" -v
"""
import contextlib
import importlib.util
import io
import os
import tempfile
import unittest

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CHECKER_PATH = os.path.join(SCRIPT_DIR, "check-conventions.py")


def load_checker():
    """以 importlib 加载连字符文件名的校验器模块（每次返回全新模块，隔离状态）。"""
    spec = importlib.util.spec_from_file_location("check_conventions_under_test", CHECKER_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class CheckerTestCase(unittest.TestCase):
    """公共夹具：构造临时 Java 源树并运行校验器 main(argv)。"""

    @classmethod
    def setUpClass(cls):
        cls.checker = load_checker()

    def run_checker(self, src):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = self.checker.main(["--src", src])
        return code, buf.getvalue()

    def write_java(self, root, fqcn, imports=()):
        """按 FQN 生成最小 Java 文件，返回文件路径。"""
        parts = fqcn.split(".")
        pkg, cls = ".".join(parts[:-1]), parts[-1]
        d = os.path.join(root, *parts[:-1])
        os.makedirs(d, exist_ok=True)
        imp = "".join(f"import {i};\n" for i in imports)
        content = f"package {pkg};\n\n{imp}\npublic class {cls} {{\n}}\n"
        fp = os.path.join(d, cls + ".java")
        with open(fp, "w", encoding="utf-8") as f:
            f.write(content)
        return fp

    def tmp_src(self, td):
        src = os.path.join(td, "src")
        os.makedirs(src, exist_ok=True)
        return src


class TestH4DataClassPlacement(CheckerTestCase):
    """H4 数据类放置：硬规则正/负例。"""

    def test_clean_tree_exit_0(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(src, "com.acme.scaffold.common.api.dto.PageQuery")
            self.write_java(src, "com.acme.scaffold.common.audit.domain.SysOperationLogDO")
            self.write_java(src, "com.acme.scaffold.system.service.UserService")
            self.write_java(src, "com.acme.scaffold.security.jwt.JwtService")
            code, out = self.run_checker(src)
            self.assertEqual(code, 0, out)
            # W2 分层概览为信息性警告、恒存在；干净树的判据是「无硬违规、无 H4」
            self.assertNotIn("硬违规", out)
            self.assertNotIn("H4", out)

    def test_misplaced_dto_exit_1_with_reason(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(src, "com.acme.scaffold.common.PageQuery")
            code, out = self.run_checker(src)
            self.assertEqual(code, 1)
            self.assertIn("H4", out)
            self.assertIn("PageQuery", out)
            self.assertIn("dto", out)  # 修复指引：应移入 dto 子包

    def test_misplaced_do_exit_1_with_reason(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(src, "com.acme.scaffold.common.audit.SysOperationLogDO")
            code, out = self.run_checker(src)
            self.assertEqual(code, 1)
            self.assertIn("H4", out)
            self.assertIn("domain", out)  # 修复指引：应移入 domain 子包

    def test_misplaced_request_exit_1(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            # 复现 V1.0.2 前的历史违规形态：Request 落在特性包根
            self.write_java(src, "com.acme.scaffold.system.notification.SendNotificationRequest")
            code, out = self.run_checker(src)
            self.assertEqual(code, 1)
            self.assertIn("SendNotificationRequest", out)

    def test_any_segment_match_exit_0(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            # dto 为祖先段（二层结构 workflow.dto.design），任意一段命中即合规
            self.write_java(src, "com.acme.scaffold.workflow.dto.design.WorkflowDesignQuery")
            self.write_java(src, "com.acme.scaffold.job.management.dto.JobCreateDTO")
            self.write_java(src, "com.acme.scaffold.monitor.health.ProbeResult")
            code, out = self.run_checker(src)
            self.assertEqual(code, 0, out)

    def test_dto_and_vo_subpackage_exit_0(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(src, "com.acme.scaffold.system.user.dto.UserCreateDTO")
            self.write_java(src, "com.acme.scaffold.system.user.vo.UserVO")
            self.write_java(src, "com.acme.scaffold.system.user.entity.SysUserDO")
            code, out = self.run_checker(src)
            self.assertEqual(code, 0, out)

    def test_bare_suffix_class_not_flagged(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            # 类名恰等于后缀（无前缀语义），不参与数据类判定，避免误伤
            self.write_java(src, "com.acme.scaffold.common.DO")
            self.write_java(src, "com.acme.scaffold.common.DTO")
            code, out = self.run_checker(src)
            self.assertEqual(code, 0, out)
            self.assertNotIn("H4", out)


class TestHardRulesRegression(CheckerTestCase):
    """H1/H2/H3 回归：H4 升级不得破坏既有硬规则。"""

    def test_package_cycle_exit_1(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(
                src, "com.acme.scaffold.a.service.AaService",
                imports=["com.acme.scaffold.b.service.BbService"],
            )
            self.write_java(
                src, "com.acme.scaffold.b.service.BbService",
                imports=["com.acme.scaffold.a.service.AaService"],
            )
            code, out = self.run_checker(src)
            self.assertEqual(code, 1)
            self.assertIn("循环依赖", out)

    def test_duplicate_fqn_exit_1(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            # 同一 package 声明分布在两个目录 -> 重复 FQN
            for sub in ("x", "y"):
                d = os.path.join(src, "com", "acme", "scaffold", "dup", sub)
                os.makedirs(d, exist_ok=True)
                with open(os.path.join(d, "DupDO.java"), "w", encoding="utf-8") as f:
                    f.write("package com.acme.scaffold.dup;\n\npublic class DupDO {}\n")
            code, out = self.run_checker(src)
            self.assertEqual(code, 1)
            self.assertIn("重复", out)

    def test_direction_violation_exit_1(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(
                src, "com.acme.scaffold.x.domain.FooDO",
                imports=["com.acme.scaffold.y.service.BarService"],
            )
            self.write_java(src, "com.acme.scaffold.y.service.BarService")
            code, out = self.run_checker(src)
            self.assertEqual(code, 1)
            self.assertIn("依赖方向", out)


class TestWarningsNonBlocking(CheckerTestCase):
    """W* 警告不阻断：升级 H4 后警告分级行为不变。"""

    def test_misplaced_tier_warns_but_exit_0(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            # *Service 类落在 controller 包 -> W1 警告，但不阻断
            self.write_java(src, "com.acme.scaffold.x.controller.FooService")
            code, out = self.run_checker(src)
            self.assertEqual(code, 0)
            self.assertIn("警告", out)
            self.assertIn("错层", out)

    def test_overview_warns_but_exit_0(self):
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(src, "com.acme.scaffold.plain.RootThing")
            code, out = self.run_checker(src)
            self.assertEqual(code, 0)
            self.assertIn("分层概览", out)


class TestModuleDag(CheckerTestCase):
    """H5 模块级依赖环检测（V1.0.3 多模块拆分新增，硬规则）。

    构造含 scaffold-common / scaffold-a 两个模块的临时反应堆（父 POM + 模块 POM），
    在反应堆根目录下以无参方式运行校验器（自动发现源码根 + H5）。
    """

    def make_reactor(self, td, deps):
        """deps: {模块名: [依赖的模块名]}；返回反应堆根目录。"""
        root = os.path.join(td, "reactor")
        os.makedirs(root, exist_ok=True)
        modules = sorted(deps)
        with open(os.path.join(root, "pom.xml"), "w", encoding="utf-8") as f:
            f.write(
                '<?xml version="1.0" encoding="UTF-8"?>\n'
                '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
                "  <modelVersion>4.0.0</modelVersion>\n"
                "  <groupId>com.acme.scaffold</groupId>\n"
                "  <artifactId>java-admin-framework</artifactId>\n"
                "  <version>1.0.0</version>\n"
                "  <packaging>pom</packaging>\n"
                "  <modules>\n"
                + "".join(f"    <module>{m}</module>\n" for m in modules)
                + "  </modules>\n"
                "</project>\n"
            )
        for m in modules:
            os.makedirs(os.path.join(root, m, "src", "main", "java"), exist_ok=True)
            dep_xml = "".join(
                "      <dependency>\n"
                "        <groupId>com.acme.scaffold</groupId>\n"
                f"        <artifactId>{d}</artifactId>\n"
                "        <version>1.0.0</version>\n"
                "      </dependency>\n"
                for d in deps[m]
            )
            with open(os.path.join(root, m, "pom.xml"), "w", encoding="utf-8") as f:
                f.write(
                    '<?xml version="1.0" encoding="UTF-8"?>\n'
                    '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
                    "  <modelVersion>4.0.0</modelVersion>\n"
                    "  <parent>\n"
                    "    <groupId>com.acme.scaffold</groupId>\n"
                    "    <artifactId>java-admin-framework</artifactId>\n"
                    "    <version>1.0.0</version>\n"
                    "  </parent>\n"
                    f"  <artifactId>{m}</artifactId>\n"
                    "  <dependencies>\n"
                    + dep_xml
                    + "  </dependencies>\n"
                    "</project>\n"
                )
        return root

    def run_checker_at(self, cwd, argv=()):
        old = os.getcwd()
        os.chdir(cwd)
        try:
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                code = self.checker.main(list(argv))
            return code, buf.getvalue()
        finally:
            os.chdir(old)

    def test_acyclic_modules_exit_0(self):
        with tempfile.TemporaryDirectory() as td:
            root = self.make_reactor(td, {
                "scaffold-common": [],
                "scaffold-a": ["scaffold-common"],
            })
            code, out = self.run_checker_at(root)
            self.assertEqual(code, 0, out)
            self.assertIn("模块依赖图", out)
            self.assertNotIn("模块级循环依赖", out)

    def test_module_cycle_exit_1(self):
        with tempfile.TemporaryDirectory() as td:
            root = self.make_reactor(td, {
                "scaffold-common": ["scaffold-a"],
                "scaffold-a": ["scaffold-common"],
            })
            code, out = self.run_checker_at(root)
            self.assertEqual(code, 1)
            self.assertIn("模块级循环依赖", out)
            self.assertIn("H5", out)

    def test_admin_depends_on_all_is_acyclic(self):
        # 形态复刻真实工程：admin 依赖全部模块，其余模块只向下依赖 -> DAG，通过
        with tempfile.TemporaryDirectory() as td:
            root = self.make_reactor(td, {
                "scaffold-common": [],
                "scaffold-framework": ["scaffold-common"],
                "scaffold-system": ["scaffold-common"],
                "scaffold-admin": ["scaffold-common", "scaffold-framework", "scaffold-system"],
            })
            code, out = self.run_checker_at(root)
            self.assertEqual(code, 0, out)

    def test_single_module_mode_skips_h5(self):
        # --src 单目录模式（单模块/回归场景）不应触发 H5，也不应因找不到反应堆根而失败
        with tempfile.TemporaryDirectory() as td:
            src = self.tmp_src(td)
            self.write_java(src, "com.acme.scaffold.common.api.Result")
            code, out = self.run_checker_at(td, ["--src", src])
            self.assertEqual(code, 0, out)
            self.assertNotIn("模块依赖图", out)

    def test_parse_pom_handles_default_namespace(self):
        # 真实 POM 带默认 xmlns，ET 解析须正确剥离命名空间取出 modules/dependencies
        with tempfile.TemporaryDirectory() as td:
            pom = os.path.join(td, "pom.xml")
            with open(pom, "w", encoding="utf-8") as f:
                f.write(
                    '<?xml version="1.0" encoding="UTF-8"?>\n'
                    '<project xmlns="http://maven.apache.org/POM/4.0.0"\n'
                    '         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">\n'
                    "  <modelVersion>4.0.0</modelVersion>\n"
                    "  <modules>\n    <module>scaffold-common</module>\n  </modules>\n"
                    "  <dependencies>\n"
                    "    <dependency>\n"
                    "      <groupId>com.acme.scaffold</groupId>\n"
                    "      <artifactId>scaffold-common</artifactId>\n"
                    "    </dependency>\n"
                    "    <dependency>\n"
                    "      <groupId>org.springframework.boot</groupId>\n"
                    "      <artifactId>spring-boot-starter-web</artifactId>\n"
                    "    </dependency>\n"
                    "  </dependencies>\n"
                    "</project>\n"
                )
            modules, deps = self.checker.parse_pom(pom)
            self.assertEqual(modules, ["scaffold-common"])
            self.assertIn(("com.acme.scaffold", "scaffold-common"), deps)
            self.assertIn(("org.springframework.boot", "spring-boot-starter-web"), deps)


if __name__ == "__main__":
    unittest.main()
