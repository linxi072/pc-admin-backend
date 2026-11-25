#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-no-procedure.py 的门禁行为回归测试（仅标准库 unittest）。

覆盖：四类违规命中、注释/字符串/文本块误报抑制、触发器与文档豁免、当前仓库干净态。
可直接执行：python3 scripts/tests/test_check_no_procedure.py
"""
from __future__ import annotations

import importlib.util
import os
import sys
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, SCRIPTS_DIR)

# 门禁脚本按仓库约定使用连字符文件名，需按路径加载而非普通 import。
_spec = importlib.util.spec_from_file_location(
    "check_no_procedure", os.path.join(SCRIPTS_DIR, "check-no-procedure.py")
)
_gate = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_gate)
scan_text = _gate.scan_text
strip_sql_noncode = _gate.strip_sql_noncode

from gate_lib import REPO_ROOT, read_text, walk_files  # noqa: E402


def rules(path: str, text: str) -> set[str]:
    return {rule for _, rule, _ in scan_text(path, text)}


class ProcedureGateTests(unittest.TestCase):
    def test_sql_create_procedure_detected(self):
        v = scan_text("db/init/ddl.sql", "CREATE PROCEDURE p() BEGIN SELECT 1; END;")
        self.assertIn("NP1", {r for _, r, _ in v})
        self.assertEqual(v[0][0], 1)

    def test_sql_create_function_with_definer_detected(self):
        text = "CREATE DEFINER=`root`@`%` FUNCTION f() RETURNS INT RETURN 1;"
        self.assertIn("NP1", rules("db/init/x.sql", text))

    def test_sql_call_detected(self):
        self.assertIn("NP2", rules("db/init/dml.sql", "CALL scaffold_backfill_tenant_id();"))

    def test_java_jdbc_escape_call_detected(self):
        text = 'CallableStatement cs = c.prepareCall("{call p(?)}");'
        self.assertEqual(rules("X.java", text), {"NP3", "NP4"})

    def test_java_procedure_annotation_detected(self):
        self.assertIn("NP4", rules("X.java", '@Procedure(storedProcedureName = "p")'))

    def test_xml_mapper_call_detected(self):
        self.assertIn("NP3", rules("XMapper.xml", "<select id=\"x\">{call p(?)}</select>"))

    def test_sql_comments_and_strings_ignored(self):
        text = (
            "-- CREATE PROCEDURE p()\n"
            "/* CALL p(); */\n"
            "INSERT INTO t(name) VALUES ('CALL p(); CREATE FUNCTION f()');\n"
        )
        self.assertEqual(scan_text("x.sql", text), [])

    def test_java_comments_and_textblock_ignored(self):
        text = (
            "// CallableStatement {call p()}\n"
            "/* @Procedure */\n"
            "String s = \"\"\"\n{call p(?)} SimpleJdbcCall\n\"\"\";\n"
            "int x = 1;\n"
        )
        self.assertEqual(scan_text("X.java", text), [])

    def test_xml_comment_ignored(self):
        self.assertEqual(scan_text("X.xml", "<!-- {call p()} -->\n<mapper/>"), [])

    def test_trigger_allowed(self):
        text = "CREATE TRIGGER trg BEFORE INSERT ON t FOR EACH ROW SET NEW.x = 1;"
        self.assertEqual(scan_text("x.sql", text), [])
        self.assertIn("TRIGGER", text)
        self.assertNotEqual(strip_sql_noncode(text), "")

    def test_markdown_not_scanned(self):
        text = "CREATE PROCEDURE p(); CALL p(); CallableStatement {call p()}"
        self.assertEqual(scan_text("README.md", text), [])

    def test_non_procedure_apis_not_flagged(self):
        self.assertEqual(scan_text("X.java", "redis.call('GET', key);"), [])
        self.assertEqual(scan_text("X.java", "future.call();"), [])

    def test_current_repo_has_no_violations(self):
        violations = []
        for path in walk_files(REPO_ROOT, (".sql", ".java", ".xml")):
            for line, rule, snippet in scan_text(path, read_text(path)):
                violations.append((path, line, rule, snippet))
        self.assertEqual(violations, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
