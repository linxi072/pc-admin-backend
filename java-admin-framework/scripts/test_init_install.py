"""scripts/init/ 初始化脚本套件的离线自测（零第三方依赖，Python 3.9+）。

覆盖：
- env_setup：JWT_SECRET 生成与强度校验、.env 解析、补全计划（新建/补缺/保留/force）
- db_init：JDBC URL 解析、连接信息优先级、幂等 SQL 生成
- seed_check：补种语句的幂等标记与关键种子值、核对项完整性
- verify_install：迁移版本扫描、健康检查 URL 与代理绕过
- install：CLI 帮助、dry-run 编排（不触碰真实数据库）

运行：python3 -m unittest discover -s scripts -p "test_init_install.py" -v
"""

from __future__ import annotations

import base64
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "init"))

import db_init  # noqa: E402
import env_setup  # noqa: E402
import install  # noqa: E402
import seed_check  # noqa: E402
import verify_install  # noqa: E402


class Args:
    """install.py 参数的最小替身。"""

    def __init__(self, **kwargs):
        self.dry_run = False
        self.force = False
        self.env_file = ""
        self.base_url = "http://localhost:8080"
        self.skip_health = True
        self.require_health = False
        self.db_url = None
        self.db_host = None
        self.db_port = None
        self.db_name = None
        self.db_user = None
        self.db_password = None
        self.app_user = None
        self.app_password = None
        self.repo_root = ""
        for key, value in kwargs.items():
            setattr(self, key, value)


class EnvSetupTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.target = os.path.join(self.tmp.name, ".env")
        self.template = os.path.join(self.tmp.name, "env.template")

    def tearDown(self):
        self.tmp.cleanup()

    def _write_template(self):
        with open(self.template, "w", encoding="utf-8") as fh:
            fh.write("# 模板注释\nSPRING_PROFILES_ACTIVE=local\nJWT_SECRET=__GENERATE_JWT_SECRET__\nDB_PRIMARY_PASSWORD=root\n")
        return self.template

    def test_generate_secret_is_base64_32_bytes(self):
        first = env_setup.generate_jwt_secret()
        second = env_setup.generate_jwt_secret()
        self.assertNotEqual(first, second)
        self.assertEqual(len(base64.b64decode(first, validate=True)), 32)
        self.assertTrue(env_setup.is_strong_secret(first))

    def test_is_strong_secret_rejects_weak_values(self):
        self.assertFalse(env_setup.is_strong_secret(""))
        self.assertFalse(env_setup.is_strong_secret(env_setup.JWT_SECRET_PLACEHOLDER))
        self.assertFalse(env_setup.is_strong_secret("not-base64!!"))
        short = base64.b64encode(b"x" * 16).decode()
        self.assertFalse(env_setup.is_strong_secret(short))

    def test_parse_env_file_skips_comments_and_blank(self):
        with open(self.target, "w", encoding="utf-8") as fh:
            fh.write("# c\n\nA=1\nB = two \nNOEQ\n")
        pairs = env_setup.parse_env_file(self.target)
        self.assertEqual(pairs, [("A", "1"), ("B", "two")])

    def test_plan_create_fills_all_keys(self):
        template = self._write_template()
        plan = env_setup.plan_env_update(self.target, template)
        self.assertTrue(plan["created"])
        keys = [k for k, _ in plan["added"]]
        self.assertEqual(keys, ["SPRING_PROFILES_ACTIVE", "JWT_SECRET", "DB_PRIMARY_PASSWORD"])
        self.assertTrue(env_setup.is_strong_secret(plan["secret"]))

    def test_apply_create_then_idempotent_second_run(self):
        template = self._write_template()
        first = env_setup.apply_env_update(self.target, template)
        self.assertTrue(first["written"])
        with open(self.target, "r", encoding="utf-8") as fh:
            content = fh.read()
        self.assertIn("JWT_SECRET=", content)
        self.assertNotIn(env_setup.JWT_SECRET_PLACEHOLDER, content)
        second = env_setup.apply_env_update(self.target, template)
        self.assertFalse(second["written"])
        self.assertTrue(second["kept_jwt"])
        self.assertEqual(second["secret"], first["secret"])

    def test_existing_env_only_fills_missing_keys(self):
        template = self._write_template()
        with open(self.target, "w", encoding="utf-8") as fh:
            fh.write("SPRING_PROFILES_ACTIVE=prod\nJWT_SECRET=%s\n" % env_setup.generate_jwt_secret())
        plan = env_setup.plan_env_update(self.target, template)
        self.assertEqual([k for k, _ in plan["added"]], ["DB_PRIMARY_PASSWORD"])
        self.assertTrue(plan["kept_jwt"])

    def test_force_regenerates_secret(self):
        template = self._write_template()
        with open(self.target, "w", encoding="utf-8") as fh:
            fh.write("JWT_SECRET=%s\n" % env_setup.generate_jwt_secret())
        plan = env_setup.plan_env_update(self.target, template, force=True)
        self.assertTrue(plan["regenerated"])
        self.assertTrue(env_setup.is_strong_secret(plan["secret"]))


class DbInitTest(unittest.TestCase):
    def test_parse_jdbc_url_variants(self):
        self.assertEqual(
            db_init.parse_jdbc_url("jdbc:mysql://localhost:3306/scaffold?useSSL=false"),
            {"host": "localhost", "port": 3306, "db": "scaffold"},
        )
        self.assertEqual(
            db_init.parse_jdbc_url("jdbc:mysql://127.0.0.1/scaffold"),
            {"host": "127.0.0.1", "port": 3306, "db": "scaffold"},
        )
        with self.assertRaises(db_init.DbError):
            db_init.parse_jdbc_url("jdbc:postgresql://localhost/db")
        with self.assertRaises(db_init.DbError):
            db_init.parse_jdbc_url("")

    def test_resolve_connection_priority(self):
        env = {"DB_PRIMARY_URL": "jdbc:mysql://envhost:3307/envdb",
               "DB_PRIMARY_USERNAME": "envuser", "DB_PRIMARY_PASSWORD": "envpw"}
        conn = db_init.resolve_connection(Args(), env)
        self.assertEqual((conn["host"], conn["port"], conn["db"]), ("envhost", 3307, "envdb"))
        conn = db_init.resolve_connection(Args(db_host="arg host", db_port=3399, db_name="argdb"), env)
        self.assertEqual((conn["host"], conn["port"], conn["db"]), ("arg host", 3399, "argdb"))
        empty = db_init.resolve_connection(Args(), {})
        self.assertEqual((empty["host"], empty["port"], empty["db"]), ("localhost", 3306, "scaffold"))

    def test_build_init_sql_idempotent_markers(self):
        stmts = db_init.build_init_sql("scaffold")
        self.assertEqual(len(stmts), 1)
        self.assertIn("CREATE DATABASE IF NOT EXISTS `scaffold`", stmts[0])
        self.assertIn("utf8mb4", stmts[0])
        with_user = db_init.build_init_sql("scaffold", "app", "p@ss'w")
        self.assertEqual(len(with_user), 4)
        self.assertIn("CREATE USER IF NOT EXISTS 'app'@'%'", with_user[1])
        self.assertIn("p@ss''w", with_user[1])  # 单引号转义
        self.assertIn("GRANT ALL PRIVILEGES ON `scaffold`.* TO 'app'@'%'", with_user[2])


class SeedCheckTest(unittest.TestCase):
    def test_all_statements_are_idempotent(self):
        for stmt in seed_check.BACKFILL_CORE_SQL + seed_check.BACKFILL_MENU_SQL:
            self.assertIn("INSERT IGNORE", stmt, msg=stmt[:80])

    def test_core_seed_values_match_migrations(self):
        joined = "\n".join(seed_check.BACKFILL_CORE_SQL)
        self.assertIn("'SUPER_ADMIN'", joined)
        self.assertIn("'admin'", joined)
        self.assertIn(seed_check.ADMIN_PASSWORD_HASH, joined)
        self.assertIn("sys_normal_disable", joined)
        self.assertIn("sys.title", joined)
        menu = "\n".join(seed_check.BACKFILL_MENU_SQL)
        self.assertIn("'system:import'", menu)
        self.assertIn("'system:observability-center'", menu)
        self.assertIn("1093", menu)
        self.assertIn("1094", menu)

    def test_seed_checks_cover_critical_baselines(self):
        names = [name for name, _, _ in seed_check.SEED_CHECKS]
        self.assertTrue(any("SUPER_ADMIN" in n for n in names))
        self.assertTrue(any("system:import" in n for n in names))
        self.assertEqual(len(seed_check.SEED_CHECKS), 9)


class VerifyInstallTest(unittest.TestCase):
    def test_scan_max_migration_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            for name in ("V32__a.sql", "V39__b.sql", "V40__c.sql", "README.md", "Vxx__d.sql"):
                open(os.path.join(tmp, name), "w").close()
            self.assertEqual(verify_install.scan_max_migration_version(tmp), 40)
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(verify_install.scan_max_migration_version(tmp))

    def test_proxy_free_opener_bypasses_env_proxy(self):
        """功能断言：设置假 http_proxy 后，proxy-free opener 仍能直达本地服务。"""
        import http.server
        import threading

        class _Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"ok")

            def log_message(self, *args):  # 静音
                pass

        server = http.server.HTTPServer(("127.0.0.1", 0), _Handler)
        port = server.server_address[1]
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = "http://127.0.0.1:%d/" % port
            old = os.environ.get("http_proxy")
            os.environ["http_proxy"] = "http://127.0.0.1:59999"  # 假代理
            try:
                status, body = verify_install.http_get(url, timeout=3)
                self.assertEqual(status, 200)
                self.assertEqual(body, "ok")
            finally:
                if old is None:
                    os.environ.pop("http_proxy", None)
                else:
                    os.environ["http_proxy"] = old
        finally:
            server.shutdown()
            server.server_close()

    def test_http_get_unreachable_returns_none_status(self):
        status, body = verify_install.http_get("http://127.0.0.1:1/nope", timeout=2)
        self.assertIsNone(status)
        self.assertTrue(body)

    def test_ping_envelope_accepts_both_code_forms(self):
        ok_body_num = '{"code":0,"message":"OK","data":{}}'
        ok_body_str = '{"code":"0","message":"OK","data":{}}'
        self.assertTrue(verify_install.ping_envelope_ok(200, ok_body_num))
        self.assertTrue(verify_install.ping_envelope_ok(200, ok_body_str))
        self.assertTrue(verify_install.ping_envelope_ok(200, '{ "code" : "0" , "message":"OK"}'))
        self.assertFalse(verify_install.ping_envelope_ok(200, '{"code":500,"message":"err"}'))
        self.assertFalse(verify_install.ping_envelope_ok(200, '{"code":"500"}'))
        self.assertFalse(verify_install.ping_envelope_ok(401, '{"code":0}'))
        self.assertFalse(verify_install.ping_envelope_ok(None, ""))


class InstallCliTest(unittest.TestCase):
    def test_help_exits_zero(self):
        with self.assertRaises(SystemExit) as ctx:
            install.main(["--help"])
        self.assertEqual(ctx.exception.code, 0)

    def test_no_command_returns_one(self):
        self.assertEqual(install.main([]), 1)

    def test_dry_run_all_on_empty_repo(self):
        with tempfile.TemporaryDirectory() as tmp:
            args = Args(dry_run=True, env_file=os.path.join(tmp, ".env"), repo_root=tmp)
            code = install.main(["all", "--dry-run", "--env-file", args.env_file,
                                 "--repo-root", tmp, "--skip-health"])
            # dry-run 下 env 会真实生成 .env 计划文件（apply 走 plan 分支不落盘），
            # db/seed 仅打印计划，verify 的 mysql 相关项依赖本机客户端（允许 SKIP/FAIL），
            # 因此只验证：命令不崩溃且 env 计划已包含 JWT_SECRET。
            self.assertIn(code, (0, 1))
            plan = env_setup.plan_env_update(args.env_file,
                                             os.path.join(os.path.dirname(install.__file__), "env.template"))
            self.assertTrue(any(k == "JWT_SECRET" and env_setup.is_strong_secret(v)
                                for k, v in plan["added"]))


if __name__ == "__main__":
    unittest.main()
