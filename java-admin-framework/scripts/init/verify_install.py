"""校验步骤：前置条件 → 数据库与迁移版本 → 种子摘要 → 健康检查。

四项内容：
1. 前置条件：JDK（≥17，推荐 21）/ mysql 客户端 / redis-cli / node / mvn（可缺）；
2. 迁移核对：本地迁移链最大版本 vs flyway_schema_history 已应用最大版本；
3. 种子摘要：复用 seed_check 的核对项（不补种）；
4. 健康检查：/livez、/readyz、/api/connectivity/ping（urllib，绕过代理）。

网络访问一律使用 ProxyHandler({})  opener，避免 http_proxy 环境变量导致误判。
"""

from __future__ import annotations

import glob
import os
import re
import shutil
import subprocess
import urllib.error
import urllib.request

import db_init
import seed_check

EXPECTED_JAVA_MAJOR = 17
RECOMMENDED_JAVA_MAJOR = 21
EXPECTED_NODE_MAJOR = 18


class VerifyItem:
    """单条校验结果。"""

    def __init__(self, name: str, status: str, detail: str = ""):
        self.name = name
        self.status = status  # PASS / FAIL / SKIP / PENDING
        self.detail = detail

    def as_tuple(self):
        return (self.name, self.status, self.detail)


def _run_version(cmd: list) -> str:
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return (result.stdout or "") + (result.stderr or "")
    except Exception:
        return ""


def check_java() -> VerifyItem:
    out = _run_version(["java", "-version"])
    match = re.search(r'version "(\d+)[."]', out)
    if not match:
        return VerifyItem("JDK 可用且 ≥%d" % EXPECTED_JAVA_MAJOR, "FAIL", "未检测到 java（教程 §1.3）")
    major = int(match.group(1))
    if major < EXPECTED_JAVA_MAJOR:
        return VerifyItem("JDK 版本", "FAIL", "当前 %d，要求 ≥%d" % (major, EXPECTED_JAVA_MAJOR))
    note = "当前 %d" % major
    if major != RECOMMENDED_JAVA_MAJOR:
        note += "（推荐 %d）" % RECOMMENDED_JAVA_MAJOR
    return VerifyItem("JDK 可用且 ≥%d" % EXPECTED_JAVA_MAJOR, "PASS", note)


def check_maven() -> VerifyItem:
    if shutil.which("mvn") is None:
        return VerifyItem("Maven", "SKIP", "PATH 中无 mvn；仓库无 mvnw，需本机安装（教程 §1.4）")
    out = _run_version(["mvn", "-v"])
    match = re.search(r"Apache Maven (\d+\.\d+)", out)
    return VerifyItem("Maven", "PASS", ("Apache Maven %s" % match.group(1)) if match else "已安装")


def check_mysql_client() -> VerifyItem:
    if not db_init.mysql_available():
        return VerifyItem("mysql 客户端", "FAIL", "未找到 mysql（教程 §2.1）")
    out = _run_version(["mysql", "--version"])
    return VerifyItem("mysql 客户端", "PASS", out.strip().splitlines()[0] if out.strip() else "已安装")


def check_redis() -> VerifyItem:
    if shutil.which("redis-cli") is None:
        return VerifyItem("Redis 连通", "SKIP", "未找到 redis-cli（教程 §2.2）")
    try:
        result = subprocess.run(["redis-cli", "ping"], capture_output=True, text=True, timeout=10)
    except Exception as exc:
        return VerifyItem("Redis 连通", "FAIL", str(exc))
    if result.stdout.strip() == "PONG":
        return VerifyItem("Redis 连通", "PASS", "PONG")
    return VerifyItem("Redis 连通", "FAIL", "redis-cli ping 未返回 PONG：Redis 未启动？")


def check_node() -> VerifyItem:
    if shutil.which("node") is None:
        return VerifyItem("Node ≥%d" % EXPECTED_NODE_MAJOR, "SKIP", "未找到 node（前端开发才需要，教程 §2.3）")
    out = _run_version(["node", "-v"]).strip()
    match = re.search(r"v(\d+)\.", out)
    major = int(match.group(1)) if match else 0
    if major < EXPECTED_NODE_MAJOR:
        return VerifyItem("Node ≥%d" % EXPECTED_NODE_MAJOR, "FAIL", "当前 %s" % out)
    return VerifyItem("Node ≥%d" % EXPECTED_NODE_MAJOR, "PASS", out)


def scan_max_migration_version(migration_dir: str):
    """扫描 db/migration 下 V*.sql 的最大版本号；无文件返回 None。"""
    versions = []
    for path in glob.glob(os.path.join(migration_dir, "V*.sql")):
        match = re.match(r"V(\d+)__", os.path.basename(path))
        if match:
            versions.append(int(match.group(1)))
    return max(versions) if versions else None


def check_migrations(conn: dict, migration_dir: str) -> VerifyItem:
    local_max = scan_max_migration_version(migration_dir)
    if local_max is None:
        return VerifyItem("迁移链扫描", "FAIL", "未找到 V*.sql: %s" % migration_dir)
    try:
        out = db_init.run_sql(
            conn,
            "SELECT MAX(CAST(version AS UNSIGNED)), "
            "SUM(CASE WHEN success=1 THEN 0 ELSE 1 END) FROM flyway_schema_history;",
            database=conn["db"],
        )
    except db_init.DbError:
        return VerifyItem("迁移版本", "PENDING", "flyway_schema_history 不存在（应用未首次启动）")
    if out.returncode != 0:
        return VerifyItem("迁移版本", "PENDING", "flyway_schema_history 不存在（应用未首次启动）")
    parts = [p for p in out.stdout.strip().split("\t") if p != ""]
    if len(parts) < 2 or parts[0] in ("NULL", ""):
        return VerifyItem("迁移版本", "PENDING", "尚无已应用迁移（应用未首次启动）")
    applied_max, failed = int(parts[0]), int(parts[1] or 0)
    if failed:
        return VerifyItem("迁移版本", "FAIL", "存在 %d 条失败记录（success=0）" % failed)
    if applied_max < local_max:
        return VerifyItem(
            "迁移版本",
            "PENDING",
            "已应用 v%d < 本地链 v%d：需启动应用执行 Flyway（教程 §4.3）" % (applied_max, local_max),
        )
    return VerifyItem("迁移版本", "PASS", "已应用 v%d（本地链 v%d）" % (applied_max, local_max))


def build_proxy_free_opener():
    """绕过 http_proxy/HTTP_PROXY 的 opener（沙箱/办公网必备）。"""
    return urllib.request.build_opener(urllib.request.ProxyHandler({}))


def http_get(url: str, timeout: float = 5.0):
    """GET 并返回 (status, body)；网络错误返回 (None, 错误信息)。"""
    try:
        with build_proxy_free_opener().open(url, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")
    except Exception as exc:
        return None, str(exc)


def ping_envelope_ok(status, body: str) -> bool:
    """判定 /api/connectivity/ping 包络：HTTP 200 且 code 为 0（数字或字符串形态）。"""
    if status != 200:
        return False
    compact = body.replace(" ", "")
    return '"code":0' in compact or '"code":"0"' in compact


def check_health(base_url: str) -> list:
    """健康三连：/livez、/readyz、/api/connectivity/ping。"""
    items = []
    base = base_url.rstrip("/")
    for path in ("/livez", "/readyz"):
        status, body = http_get(base + path)
        if status == 200 and '"UP"' in body:
            items.append(VerifyItem("GET %s" % path, "PASS", "200 UP"))
        elif status is None:
            items.append(VerifyItem("GET %s" % path, "FAIL", "不可达：%s（后端未启动？教程 §4.3）" % body))
        else:
            items.append(VerifyItem("GET %s" % path, "FAIL", "HTTP %s %s" % (status, body[:120])))
    status, body = http_get(base + "/api/connectivity/ping")
    if ping_envelope_ok(status, body):
        items.append(VerifyItem("GET /api/connectivity/ping", "PASS", "200 code=0"))
    elif status is None:
        items.append(
            VerifyItem("GET /api/connectivity/ping", "FAIL", "不可达：%s（后端未启动？教程 §4.3）" % body)
        )
    else:
        items.append(
            VerifyItem("GET /api/connectivity/ping", "FAIL", "HTTP %s %s" % (status, body[:120]))
        )
    return items


def cmd_verify(args, env: dict) -> dict:
    """install.py 的 verify 子命令入口。"""
    items = [check_java(), check_maven(), check_mysql_client(), check_redis(), check_node()]
    conn = None
    if db_init.mysql_available():
        conn = db_init.resolve_connection(args, env)
        try:
            ok = db_init.database_exists(conn, conn["db"])
            items.append(
                VerifyItem(
                    "数据库 %s 存在" % conn["db"],
                    "PASS" if ok else "FAIL",
                    "%s:%s" % (conn["host"], conn["port"]) if ok else "库不存在：先执行 db 步骤",
                )
            )
            if ok:
                items.append(
                    check_migrations(conn, os.path.join(args.repo_root, "scaffold-admin/src/main/resources/db/migration"))
                )
                try:
                    results = seed_check.run_checks(conn)
                    failed = [name for name, ok_, _ in results if not ok_]
                    items.append(
                        VerifyItem(
                            "基础数据核对（%d 项）" % len(results),
                            "PASS" if not failed else "FAIL",
                            "全部通过" if not failed else "缺失: %s" % "; ".join(failed),
                        )
                    )
                except seed_check.SeedPendingError as exc:
                    items.append(VerifyItem("基础数据核对", "PENDING", str(exc)))
        except db_init.DbError as exc:
            items.append(VerifyItem("数据库连接", "FAIL", str(exc)))
    else:
        items.append(VerifyItem("数据库连接", "SKIP", "无 mysql 客户端"))

    if not args.skip_health:
        items.extend(check_health(args.base_url))
    else:
        items.append(VerifyItem("健康检查", "SKIP", "--skip-health 指定跳过"))

    failed = [i for i in items if i.status == "FAIL"]
    return {
        "status": "FAIL" if failed else "PASS",
        "items": [i.as_tuple() for i in items],
    }
