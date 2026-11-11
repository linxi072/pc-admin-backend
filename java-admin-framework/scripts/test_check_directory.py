#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-directory.py 的行为回归测试（零依赖，仅标准库）。

通过 subprocess 调用真实脚本 + 进程内直接调用 ``main()`` / 各 ``check_*`` 函数，
对临时构造的「最小合规工程」夹具做端到端断言，覆盖 D1–D13 全部硬性规则
（D12 属 check-cluster-layout.py，不在本脚本范围）：

  D1 顶层包根 / 模块登记      D2 启动类位置例外
  D3 运行期配置归属            D4 前端全局样式
  D5 跨模块常量单一来源        D6 规范文档与索引
  D7 子模块 POM 版本收敛       D8 main 源码根白名单
  D9 测试包根白名单            D10 文档索引与死链
  D11 测试期运行配置归属       D13 数据访问层单一写法
                              以及 CLI 路径不存在 / 多违规累积

夹具采用「纯增量」设计：只创建目录与文件、不删除。本仓沙箱禁止 unlink，
依赖删除回滚的夹具会连锁失败；每个用例使用独立临时目录互不影响。
最小合规工程默认包含 D13 要求的 ``SqlTables.java``，可用
``make_repo(with_sql_tables=False)`` 构造违规场景。
"""
import importlib.util
import io
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(SCRIPT_DIR, "check-directory.py")

# 进程内加载（文件名含连字符，不能直接 import），用于 main() / check_* 混合测试
_spec = importlib.util.spec_from_file_location("check_directory_mod", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

STYLE_FILES = ("base.css", "theme-tokens.css", "element-theme.css")
ALL_DOCS = ("DIRECTORY.md", "RUOYI-ALIGNMENT.md", "MODULES.md")
PKG = os.path.join("src", "main", "java", "com", "acme", "scaffold")


def write(path, text=""):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def make_repo(with_constants=True, docs=ALL_DOCS, index_text=None,
              styles=STYLE_FILES, with_index=True, with_sql_tables=True):
    """构造最小合规工程：两个已登记模块 + 三份规范文档 + 完整前端样式 + DSL 入口。"""
    base = tempfile.mkdtemp(prefix="dirchk_")
    backend = os.path.join(base, "backend")
    frontend = os.path.join(base, "frontend")
    os.makedirs(backend)
    os.makedirs(frontend)

    for m, roots in (("scaffold-admin", ("admin", "config")),
                     ("scaffold-common", ("common", "mybatis", "plugin"))):
        for r in roots:
            os.makedirs(os.path.join(backend, m, PKG, r), exist_ok=True)

    if with_constants:
        write(os.path.join(backend, "scaffold-common", PKG, "common", "constant",
                           "CommonConstants.java"), "package x;\nclass CommonConstants {}\n")
    if with_sql_tables:
        # D13：数据访问层单一入口（表/列常量），默认存在以保持基线合规。
        write(os.path.join(backend, "scaffold-common", PKG, "mybatis", "dsl",
                           "SqlTables.java"), "package x;\nclass SqlTables {}\n")
    for d in docs:
        write(os.path.join(backend, "docs", d), "# doc\n")
    if index_text is None:
        index_text = "# 文档索引\n" + "".join(f"- [{d}]({d})\n" for d in docs)
    if with_index:
        write(os.path.join(backend, "docs", "README.md"), index_text)
    for f in styles:
        write(os.path.join(frontend, "src", "styles", f), "/* css */\n")
    return backend, frontend


def run(backend, frontend, *extra):
    proc = subprocess.run(
        [sys.executable, SCRIPT, "--backend", backend, "--frontend", frontend, *extra],
        capture_output=True, text=True,
    )
    return proc.returncode, proc.stdout + proc.stderr


class CheckDirectoryTest(unittest.TestCase):

    # ---- 基线 ----
    def test_clean_repo(self):
        """最小合规工程 -> EXIT=0。"""
        backend, frontend = make_repo()
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 0, out)
        self.assertIn("目录约定校验通过", out)

    # ---- D1 ----
    def test_d1_unregistered_module(self):
        """新增 scaffold-* 模块未登记白名单 -> D1。"""
        backend, frontend = make_repo()
        os.makedirs(os.path.join(backend, "scaffold-ghost"))
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D1 模块未登记: scaffold-ghost", out)

    def test_d1_unregistered_package_root(self):
        """已登记模块出现额外顶层包根 -> D1。"""
        backend, frontend = make_repo()
        os.makedirs(os.path.join(backend, "scaffold-admin", PKG, "ghost"))
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D1 未登记顶层包根: scaffold-admin", out)

    # ---- D2 ----
    def test_d2_root_java_in_non_admin_module(self):
        """非启动模块在 com.acme.scaffold 根级放 Java -> D2。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", PKG, "Stray.java"), "class Stray {}\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D2 非启动模块", out)
        self.assertIn("Stray.java", out)

    def test_d2_admin_root_java_is_allowed(self):
        """scaffold-admin 在包根放启动类属登记例外 -> 不报 D2。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-admin", PKG, "AdminApplication.java"),
              "class AdminApplication {}\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 0, out)
        self.assertNotIn("D2", out)

    # ---- D3 ----
    def test_d3_runtime_config_in_non_admin_module(self):
        """非启动模块持有 application.yml -> D3。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "src", "main", "resources",
                           "application.yml"), "server:\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D3 非启动模块持有运行期配置", out)
        self.assertIn("application.yml", out)

    # ---- D4 ----
    def test_d4_css_in_frontend_src_root(self):
        """前端 src/ 根级散落 CSS -> D4。"""
        backend, frontend = make_repo()
        write(os.path.join(frontend, "src", "stray.css"), "/* stray */\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D4 前端 src/ 根级散落 CSS", out)

    def test_d4_missing_style_file(self):
        """缺任一全局样式文件 -> D4。"""
        backend, frontend = make_repo(styles=STYLE_FILES[:2])
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D4 缺少全局样式文件: src/styles/element-theme.css", out)

    def test_d4_missing_src_dir(self):
        """前端 src/ 不存在 -> D4 且不再检查样式文件。"""
        base = tempfile.mkdtemp(prefix="dirchk_nosrc_")
        backend = os.path.join(base, "backend")
        frontend = os.path.join(base, "frontend")
        os.makedirs(backend)
        os.makedirs(frontend)
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D4 前端 src/ 目录不存在", out)

    # ---- D5 ----
    def test_d5_missing_common_constants(self):
        """删除 CommonConstants.java -> D5。"""
        backend, frontend = make_repo(with_constants=False)
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D5 缺少 common/constant/CommonConstants.java", out)

    # ---- D6 ----
    def test_d6_missing_required_doc(self):
        """缺任一规范文档 -> D6。"""
        backend, frontend = make_repo(docs=("DIRECTORY.md", "RUOYI-ALIGNMENT.md"))
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D6 缺少规范文档: docs/MODULES.md", out)

    def test_d6_index_missing_required_link(self):
        """docs/README.md 未引用 DIRECTORY.md -> D6。"""
        backend, frontend = make_repo(index_text="# 索引\n- [模块](MODULES.md)\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D6 docs/README.md 索引缺少引用: DIRECTORY.md", out)

    def test_d6_missing_index(self):
        """docs/README.md 缺失 -> D6。"""
        backend, frontend = make_repo(with_index=False)
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D6 缺少文档索引: docs/README.md", out)

    # ---- D7 ----
    def test_d7_hardcoded_dependency_version(self):
        """子模块 POM 写死第三方版本 -> D7。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "pom.xml"),
              "<project><dependencies><dependency>"
              "<groupId>org.foo</groupId><artifactId>bar</artifactId>"
              "<version>1.2.3</version></dependency></dependencies></project>")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D7 子模块写死依赖版本: scaffold-common", out)
        self.assertIn("1.2.3", out)

    def test_d7_broken_pom_xml(self):
        """POM 无法解析 -> D7 解析失败（不可静默跳过）。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "pom.xml"), "<project><broken>")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D7 子模块 POM 解析失败: scaffold-common", out)

    def test_d7_property_version_is_allowed(self):
        """${...} 属性占位版本由父 POM 收敛 -> 合规。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "pom.xml"),
              "<project><dependencies><dependency>"
              "<groupId>org.foo</groupId><artifactId>bar</artifactId>"
              "<version>${bar.version}</version></dependency></dependencies></project>")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 0, out)

    # ---- D8 ----
    def test_d8_unregistered_main_java_root(self):
        """src/main/java 出现白名单外一级目录 -> D8。"""
        backend, frontend = make_repo()
        os.makedirs(os.path.join(backend, "scaffold-common", "src", "main", "java", "ghost"))
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D8 未登记 src/main/java 一级目录: scaffold-common -> ghost", out)

    def test_d8_admin_db_root_exception(self):
        """admin 的 db 一级目录是登记例外 -> 不报 D8。"""
        backend, frontend = make_repo()
        os.makedirs(os.path.join(backend, "scaffold-admin", "src", "main", "java", "db"))
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 0, out)
        self.assertNotIn("D8", out)

    def test_d8_stray_java_in_main_root(self):
        """src/main/java 根级散落 Java -> D8。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "src", "main", "java", "Loose.java"),
              "class Loose {}\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D8 src/main/java 根级散落 Java", out)

    # ---- D9 ----
    def test_d9_unregistered_test_package_root(self):
        """测试树新增未登记包根 -> D9。"""
        backend, frontend = make_repo()
        os.makedirs(os.path.join(backend, "scaffold-common", "src", "test", "java",
                                "com", "acme", "scaffold", "ghost"))
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D9 未登记测试包根: scaffold-common", out)

    def test_d9_stray_java_in_test_root(self):
        """测试源码包根散落 Java（非登记例外） -> D9。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "src", "test", "java",
                           "com", "acme", "scaffold", "Stray.java"), "class Stray {}\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D9 测试源码根级散落 Java", out)

    # ---- D10 ----
    def test_d10_doc_not_in_index(self):
        """docs/ 下新增文档未挂索引 -> D10。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "docs", "EXTRA.md"), "# extra\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D10 docs/README.md 索引漏挂文档: EXTRA.md", out)

    def test_d10_index_dead_link(self):
        """索引里相对链接目标不存在 -> D10 死链。"""
        backend, frontend = make_repo(
            index_text="# 索引\n"
                       "- [目录](DIRECTORY.md)\n"
                       "- [若依](RUOYI-ALIGNMENT.md)\n"
                       "- [模块](MODULES.md)\n"
                       "- [失效](NOT_EXIST.md)\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D10 docs/README.md 索引死链: NOT_EXIST.md", out)

    def test_d10_external_links_are_skipped(self):
        """http(s)/mailto/锚点链接不参与死链判定。"""
        backend, frontend = make_repo(
            index_text="# 索引\n"
                       "- [目录](DIRECTORY.md#section)\n"
                       "- [若依](RUOYI-ALIGNMENT.md)\n"
                       "- [模块](MODULES.md)\n"
                       "- [外部](https://example.com/x)\n"
                       "- [邮件](mailto:a@b.com)\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 0, out)

    # ---- D11 ----
    def test_d11_test_resources_in_non_admin_module(self):
        """非启动模块持有测试期运行配置 -> D11。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "src", "test", "resources",
                           "application-test.yml"), "server:\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D11 非启动模块持有测试期运行配置", out)

    # ---- D13 ----
    def test_d13_mapper_xml_in_resources(self):
        """resources 下出现 *Mapper.xml -> D13（不必放在 mapper/ 目录里）。"""
        backend, frontend = make_repo()
        write(os.path.join(backend, "scaffold-common", "src", "main", "resources",
                           "UserMapper.xml"), "<mapper/>\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D13 禁止新增 Mapper XML", out)
        self.assertIn("UserMapper.xml", out)

    def test_d13_mapper_directory_in_resources(self):
        """resources 下新增空 mapper/ 目录也阻断（目录形态本身即第二套写法）。"""
        backend, frontend = make_repo()
        os.makedirs(os.path.join(backend, "scaffold-common", "src", "main",
                                "resources", "mapper", "system"), exist_ok=True)
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D13 禁止新增 Mapper XML", out)
        self.assertIn("目录 scaffold-common/src/main/resources/mapper", out)

    def test_d13_missing_sql_tables(self):
        """SqlTables.java 被删/改名 -> D13（DSL 单一入口不得缺失）。"""
        backend, frontend = make_repo(with_sql_tables=False)
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D13 缺少 scaffold-common/src/main/java/com/acme/scaffold/mybatis/dsl/SqlTables.java", out)

    def test_d13_legal_mapper_java_names_are_allowed(self):
        """合法 *Mapper.java（DSL 内部类 / 类型映射）不得被文件名匹配误伤。"""
        backend, frontend = make_repo()
        for name in ("RecordMapper", "DynamicMapper", "SnakeRecordMapper", "JavaTypeMapper"):
            write(os.path.join(backend, "scaffold-common", PKG, "common", "mapper",
                               name + ".java"), f"package x;\nclass {name} {{}}\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 0, out)
        self.assertNotIn("D13 禁止新增", out)
        self.assertNotIn("D13 缺少", out)

    # ---- CLI / 混合模式 ----
    def test_cli_missing_path_returns_2(self):
        """工程路径不存在 -> EXIT=2 且打印 [ERROR]。"""
        missing = os.path.join(tempfile.gettempdir(), "__dirchk_no_such_path__")
        rc, out = run(missing, missing)
        self.assertEqual(rc, 2, out)
        self.assertIn("[ERROR] 工程路径不存在", out)

    def test_main_in_process(self):
        """进程内直接调用 main(argv) 亦可完成校验（不启子进程）。"""
        backend, frontend = make_repo()
        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = mod.main(["--backend", backend, "--frontend", frontend])
        self.assertEqual(rc, 0, buf.getvalue())
        self.assertIn("目录约定校验通过", buf.getvalue())

    def test_check_frontend_in_process(self):
        """进程内直调 check_frontend 可精确收集 D4 错误。"""
        _, frontend = make_repo()
        write(os.path.join(frontend, "src", "stray.css"), "")
        errors = []
        mod.check_frontend(frontend, errors)
        self.assertTrue(any(e.startswith("D4") for e in errors), errors)

    def test_multiple_violations_are_all_reported(self):
        """多项违规同时存在时逐条列出，且返回 EXIT=1。"""
        backend, frontend = make_repo(with_constants=False)
        write(os.path.join(frontend, "src", "stray.css"), "")
        write(os.path.join(backend, "scaffold-common", "src", "main", "resources",
                           "application.yml"), "server:\n")
        rc, out = run(backend, frontend)
        self.assertEqual(rc, 1, out)
        self.assertIn("D3", out)
        self.assertIn("D4", out)
        self.assertIn("D5", out)
        self.assertIn("共 3 项硬性违规", out)


if __name__ == "__main__":
    unittest.main()
