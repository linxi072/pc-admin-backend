#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-permission.py 的行为回归测试（零依赖，仅标准库）。

通过 subprocess 调用真实脚本，向 ``--sql-dir`` / ``--src`` 注入临时夹具，
覆盖 P1「菜单权限码 ⊆ 接口权限码」的全部关键分支：

  * 干净 / 漂移 / 资源表种子来源 / 内置豁免 / 命令行豁免
  * UPDATE sys_menu SET permission_code 被采集
  * Java 与 SQL 注释不参与判定（否则 javadoc 示例会洗白漂移）
  * 两段式 menu_code 不被误采为权限码
  * --json 结构化输出、路径不存在、空 SQL 目录

夹具采用「纯增量」设计：只创建目录与文件、不删除。本仓沙箱禁止 unlink，
依赖删除回滚的夹具会连锁失败；每个用例使用独立临时目录互不影响。
"""
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-permission.py")

MENU_OK = """\
INSERT IGNORE INTO sys_menu (id, menu_code, permission_code)
VALUES (1, 'system:user', 'system:user:read');
"""

JAVA_OK = """\
class C {
    @PreAuthorize("hasAuthority('system:user:read')")
    void list() {}
}
"""


def write(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def make_fixture(sql_text, java_text=JAVA_OK, files=None):
    base = tempfile.mkdtemp(prefix="perm_test_")
    sql_dir = os.path.join(base, "sql")
    src = os.path.join(base, "src")
    os.makedirs(sql_dir)
    os.makedirs(src)
    write(os.path.join(sql_dir, "V1__seed.sql"), sql_text)
    write(os.path.join(src, "C.java"), java_text)
    for name, text in (files or {}).items():
        write(os.path.join(sql_dir, name), text)
    return sql_dir, src


def run(sql_dir, src, *extra):
    """返回 ``(returncode, stdout, stderr)``，刻意不合并两路输出。

    受限沙箱会在 Python 启动期把 ``sitecustomize`` 的临时目录探测警告写入
    stderr；若把 stderr 拼进 stdout，``--json`` 的纯净 JSON 会被追加噪声。
    注意 ``[ERROR]`` 走 stderr（见 check-permission.main），故单独保留一路。
    """
    proc = subprocess.run(
        [sys.executable, SCRIPT, "--sql-dir", sql_dir, "--src", src, *extra],
        capture_output=True, text=True,
    )
    return proc.returncode, proc.stdout, proc.stderr


class PermissionCheckTest(unittest.TestCase):

    def test_clean(self):
        """菜单码已在 @PreAuthorize 登记 -> EXIT=0。"""
        sql, src = make_fixture(MENU_OK)
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 0, out)
        self.assertIn("P1 权限码一致性校验通过", out)

    def test_violation(self):
        """菜单码无任何接口来源 -> EXIT=1 且报出 P1 与具体码。"""
        sql, src = make_fixture(MENU_OK, "// no controller\n")
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 1, out)
        self.assertIn("[P1]", out)
        self.assertIn("system:user:read", out)

    def test_api_resource_seed_is_valid_source(self):
        """仅登记在 sys_api_resource 种子（无注解）也算接口来源 -> EXIT=0。"""
        seed = ("INSERT IGNORE INTO sys_api_resource (id, permission_code)\n"
                "VALUES (1, 'system:user:read');\n")
        sql, src = make_fixture(MENU_OK, "// no controller\n")
        write(os.path.join(sql, "V2__api.sql"), seed)
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 0, out)

    def test_default_exempt(self):
        """内置豁免 system:api-docs:read（V72 明示预留）-> EXIT=0。"""
        sql, src = make_fixture(
            "INSERT IGNORE INTO sys_menu (id, menu_code, permission_code)\n"
            "VALUES (1, 'system:api-docs', 'system:api-docs:read');\n",
            "// springdoc 端点无 Controller\n")
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 0, out)
        self.assertIn("system:api-docs:read", out)

    def test_cli_exempt(self):
        """--exempt 追加豁免 -> EXIT=0。"""
        sql, src = make_fixture(MENU_OK, "// no controller\n")
        rc, out, _ = run(sql, src, "--exempt", "system:user:read")
        self.assertEqual(rc, 0, out)

    def test_update_set_is_menu_source(self):
        """UPDATE sys_menu SET permission_code 的新值须受校验 -> EXIT=1。"""
        sql, src = make_fixture(
            MENU_OK + "UPDATE sys_menu SET permission_code = 'system:ghost:read' WHERE id = 1;\n",
            "// no matching controller\n")
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 1, out)
        self.assertIn("system:ghost:read", out)

    def test_java_comment_not_counted(self):
        """javadoc 里的 hasAuthority 示例不得洗白漂移 -> EXIT=1。"""
        javadoc = ("class C {\n"
                   "    /** 示例 {@code hasAuthority('system:user:read')} */\n"
                   "    void list() {}\n"
                   "}\n")
        sql, src = make_fixture(MENU_OK, javadoc)
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 1, out)

    def test_sql_comment_not_counted(self):
        """SQL 行注释里的权限码字面量不得被采为菜单种子 -> EXIT=0。"""
        sql, src = make_fixture(
            "-- 历史值 'system:gone:read' 仅作说明，不得参与判定\n" + MENU_OK)
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 0, out)

    def test_two_segment_menu_code_ignored(self):
        """两段式 menu_code（system:user）不是权限码 -> 不产生违规。"""
        sql, src = make_fixture(
            "INSERT IGNORE INTO sys_menu (id, menu_code, permission_code)\n"
            "VALUES (1, 'system:user', NULL);\n",
            "class C { @PreAuthorize(\"hasAuthority('system:user:read')\") void f() {} }\n")
        rc, out, _ = run(sql, src)
        self.assertEqual(rc, 0, out)

    def test_json_output(self):
        """--json 输出结构化结果且含 passed 字段（stdout 为纯净 JSON）。"""
        import json
        sql, src = make_fixture(MENU_OK)
        rc, out, _ = run(sql, src, "--json")
        self.assertEqual(rc, 0, out)
        payload = json.loads(out)
        self.assertTrue(payload["passed"])
        self.assertEqual(payload["menu_codes"], 1)

    def test_bad_path(self):
        """SQL 目录不存在 -> EXIT=2，且 [ERROR] 诊断走 stderr。"""
        rc, out, err = run(os.path.join(tempfile.gettempdir(), "__perm_no_such_sql__"),
                           os.path.join(tempfile.gettempdir(), "__perm_no_such_src__"))
        self.assertEqual(rc, 2, out + err)
        self.assertIn("[ERROR]", err)

    def test_empty_sql_dir(self):
        """SQL 目录存在但无 .sql -> EXIT=2（防止静默假绿）。"""
        base = tempfile.mkdtemp(prefix="perm_empty_")
        sql_dir = os.path.join(base, "sql")
        src = os.path.join(base, "src")
        os.makedirs(sql_dir)
        os.makedirs(src)
        write(os.path.join(src, "C.java"), JAVA_OK)
        rc, out, _ = run(sql_dir, src)
        self.assertEqual(rc, 2, out)


if __name__ == "__main__":
    unittest.main()
