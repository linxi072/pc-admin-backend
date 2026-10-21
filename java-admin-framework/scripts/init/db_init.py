"""数据库初始化步骤：建库、（可选）建业务账号授权、连通性核对。

设计约束：
- 幂等可重复：CREATE DATABASE/USER IF NOT EXISTS + GRANT，重复执行结果一致；
- 口令不走命令行（避免 ps 泄露与 "insecure" 警告），经 MYSQL_PWD 环境变量传递；
- 连接信息解析顺序：命令行参数 > 环境变量（DB_PRIMARY_*）> local 默认值。
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess

DEFAULT_DB_NAME = "scaffold"
DEFAULT_HOST = "localhost"
DEFAULT_PORT = 3306
DEFAULT_USER = "root"
DEFAULT_PASSWORD = "root"

JDBC_RE = re.compile(r"^jdbc:mysql://(?P<host>[^:/?]+)(?::(?P<port>\d+))?/(?P<db>[^?]+)")


class DbError(Exception):
    """数据库步骤可预期错误（客户端缺失、连接失败等）。"""


def parse_jdbc_url(url: str) -> dict:
    """解析 JDBC URL → {host, port, db}；无法解析时抛出 DbError。"""
    if not url:
        raise DbError("JDBC URL 为空")
    match = JDBC_RE.match(url.strip())
    if not match:
        raise DbError("无法解析 JDBC URL: %s" % url)
    return {
        "host": match.group("host") or DEFAULT_HOST,
        "port": int(match.group("port") or DEFAULT_PORT),
        "db": match.group("db"),
    }


def resolve_connection(args, env: dict) -> dict:
    """汇总连接信息：CLI > 环境变量 > local 默认。"""
    url = getattr(args, "db_url", None) or env.get("DB_PRIMARY_URL", "")
    if url:
        parsed = parse_jdbc_url(url)
        host, port, db = parsed["host"], parsed["port"], parsed["db"]
    else:
        host, port, db = DEFAULT_HOST, DEFAULT_PORT, DEFAULT_DB_NAME
    return {
        "host": getattr(args, "db_host", None) or host,
        "port": int(getattr(args, "db_port", None) or port),
        "db": getattr(args, "db_name", None) or db,
        "user": getattr(args, "db_user", None) or env.get("DB_PRIMARY_USERNAME") or DEFAULT_USER,
        "password": getattr(args, "db_password", None)
        or env.get("DB_PRIMARY_PASSWORD")
        or DEFAULT_PASSWORD,
    }


def mysql_available() -> bool:
    return shutil.which("mysql") is not None


def build_mysql_argv(conn: dict, database: str = None, batch: bool = True) -> list:
    """构造 mysql 客户端参数；口令经 MYSQL_PWD 传递（调用方负责注入环境）。"""
    argv = ["mysql", "-h", str(conn["host"]), "-P", str(conn["port"]), "-u", str(conn["user"])]
    if database:
        argv.append(database)
    if batch:
        argv.append("--batch")
        argv.append("--skip-column-names")
    return argv


def run_sql(conn: dict, sql: str, database: str = None) -> subprocess.CompletedProcess:
    """执行一段 SQL（mysql 客户端，stdin 传入）。"""
    if not mysql_available():
        raise DbError("未找到 mysql 客户端：请先安装 MySQL 8 并确保 mysql 在 PATH 中（教程 §2.1）")
    child_env = dict(os.environ)
    child_env["MYSQL_PWD"] = str(conn["password"])
    return subprocess.run(
        build_mysql_argv(conn, database=database),
        input=sql,
        text=True,
        capture_output=True,
        env=child_env,
        timeout=60,
    )


def build_init_sql(db_name: str, app_user: str = None, app_password: str = None) -> list:
    """生成幂等初始化语句列表（建库 [+ 建业务账号并授权]）。"""
    statements = [
        "CREATE DATABASE IF NOT EXISTS `%s` "
        "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" % db_name,
    ]
    if app_user:
        escaped = app_password.replace("'", "''") if app_password else ""
        statements.append(
            "CREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';"
            % (app_user, escaped)
        )
        statements.append(
            "GRANT ALL PRIVILEGES ON `%s`.* TO '%s'@'%%';" % (db_name, app_user)
        )
        statements.append("FLUSH PRIVILEGES;")
    return statements


def database_exists(conn: dict, db_name: str) -> bool:
    """information_schema 核对库存在。"""
    result = run_sql(
        conn,
        "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '%s';" % db_name,
    )
    if result.returncode != 0:
        raise DbError("连接数据库失败: %s" % (result.stderr.strip() or result.stdout.strip()))
    return db_name in result.stdout


def cmd_db(args, env: dict) -> dict:
    """install.py 的 db 子命令入口。"""
    conn = resolve_connection(args, env)
    app_user = getattr(args, "app_user", None)
    app_password = getattr(args, "app_password", None)
    statements = build_init_sql(conn["db"], app_user, app_password)
    if args.dry_run:
        return {
            "status": "PLANNED",
            "conn": {k: v for k, v in conn.items() if k != "password"},
            "statements": statements,
        }
    if not mysql_available():
        raise DbError("未找到 mysql 客户端：请先安装 MySQL 8 并确保 mysql 在 PATH 中（教程 §2.1）")
    for stmt in statements:
        result = run_sql(conn, stmt)
        if result.returncode != 0:
            raise DbError("执行失败: %s\n  %s" % (stmt, (result.stderr or "").strip()))
    exists = database_exists(conn, conn["db"])
    return {
        "status": "PASS" if exists else "FAIL",
        "conn": {k: v for k, v in conn.items() if k != "password"},
        "statements": statements,
        "verified": exists,
    }
