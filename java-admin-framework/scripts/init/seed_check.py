"""基础数据核对与幂等补种步骤。

定位说明（重要）：
- 标准路径下，全部基础数据由 Flyway 迁移链 V32~V39 在应用首次启动时导入；
- 本步骤面向两类场景做「核对 + 幂等补种」：
    1) DBA 手工管控场景（关闭 Flyway 自动执行，按脚本导入）；
    2) 历史缺陷修复兜底（如 V35/V36 菜单 id 碰撞被 INSERT IGNORE 静默丢行）。
- 补种语句与迁移中的种子逐字等价（INSERT IGNORE 幂等），重复执行安全；
- 若库中表尚不存在（应用未首次启动），返回 PENDING 并指引先启动应用。
"""

from __future__ import annotations

import db_init

# V4 段修正后的默认口令 bcrypt hash（明文 admin123，仅占位值场景写入）
ADMIN_PASSWORD_HASH = "{bcrypt}$2a$10$HXuNze85xFb7XsvExix/mO/s49oO6K7L7NbFDKGqdXhGa1IsDnvXO"

# V39 修复的两条关键菜单（system:import 原 1091 与 V36 碰撞被静默丢弃，改 1093 重播种）
BACKFILL_MENU_SQL = [
    "INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, "
    "route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, "
    "created_at, updated_at) VALUES (1093, 0, 1002, 'system:import', '导入中心', 'MENU', "
    "'/system/import', 'system/ImportCenter', 'system:import:read', 'Upload', 1, 0, 101, "
    "'ACTIVE', NOW(3), NOW(3));",
    "INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, "
    "route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, "
    "created_at, updated_at) VALUES (1094, 0, 1002, 'system:observability-center', '可观测中心', "
    "'MENU', '/system/observability', 'system/ObservabilityCenter', 'system:monitor:read', "
    "'DataLine', 1, 0, 103, 'ACTIVE', NOW(3), NOW(3));",
    "INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) "
    "SELECT 0, 1, id FROM sys_menu WHERE menu_code = 'system:import';",
    "INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) "
    "SELECT 0, 1, id FROM sys_menu WHERE menu_code = 'system:observability-center';",
]

# 与 V32 种子逐字等价的最小基线补种（角色/管理员/绑定/字典/配置）
BACKFILL_CORE_SQL = [
    "INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, sort_no, "
    "created_at, updated_at) VALUES (1, 0, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ACTIVE', 1, "
    "NOW(3), NOW(3));",
    "INSERT IGNORE INTO sys_user (id, tenant_id, username, password_hash, display_name, status, "
    "token_version, version, created_at, updated_at) VALUES (1, 0, 'admin', '%s', '超级管理员', "
    "'ACTIVE', 1, 0, NOW(3), NOW(3));" % ADMIN_PASSWORD_HASH,
    "INSERT IGNORE INTO sys_user_role (id, tenant_id, user_id, role_id, scope_org_id, created_at) "
    "VALUES (1, 0, 1, 1, 0, NOW(3));",
    "INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, sort_no, remark, "
    "created_at, updated_at) VALUES (1, 0, 'sys_normal_disable', '系统开关', 'ACTIVE', 1, "
    "'正常 / 停用', NOW(3), NOW(3)), (2, 0, 'sys_user_sex', '用户性别', 'ACTIVE', 2, '性别字典', "
    "NOW(3), NOW(3)), (3, 0, 'sys_yes_no', '是否标识', 'ACTIVE', 3, '是否字典', NOW(3), NOW(3));",
    "INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_code, dict_label, dict_value, "
    "dict_sort, status, remark, created_at, updated_at) VALUES (1, 0, 'sys_normal_disable', '正常', "
    "'0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)), (2, 0, 'sys_normal_disable', '停用', '1', 2, "
    "'ACTIVE', NULL, NOW(3), NOW(3)), (3, 0, 'sys_user_sex', '男', '0', 1, 'ACTIVE', NULL, "
    "NOW(3), NOW(3)), (4, 0, 'sys_user_sex', '女', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)), "
    "(5, 0, 'sys_yes_no', '是', 'Y', 1, 'ACTIVE', NULL, NOW(3), NOW(3)), (6, 0, 'sys_yes_no', "
    "'否', 'N', 2, 'ACTIVE', NULL, NOW(3), NOW(3));",
    "INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, "
    "config_type, remark, status, created_at, updated_at) VALUES (1, 0, 'sys.title', '系统标题', "
    "'管理框架', 'STRING', '前端展示标题', 'ACTIVE', NOW(3), NOW(3)), (2, 0, 'sys.max.login.fail', "
    "'最大登录失败次数', '5', 'INT', '超过则锁定账户', 'ACTIVE', NOW(3), NOW(3)), (3, 0, "
    "'sys.captcha.enabled', '是否启用验证码', 'true', 'BOOLEAN', '登录验证码开关', 'ACTIVE', "
    "NOW(3), NOW(3));",
]

# 核对项：(名称, SQL, 判定函数)
def _at_least(n):
    return lambda out: int(out.strip() or 0) >= n


SEED_CHECKS = [
    ("sys_role 存在 SUPER_ADMIN", "SELECT COUNT(*) FROM sys_role WHERE tenant_id=0 AND role_code='SUPER_ADMIN';", _at_least(1)),
    ("sys_user 存在 admin", "SELECT COUNT(*) FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted=0;", _at_least(1)),
    ("admin 绑定 SUPER_ADMIN", "SELECT COUNT(*) FROM sys_user_role WHERE tenant_id=0 AND user_id=1 AND role_id=1;", _at_least(1)),
    ("sys_dict_type ≥3", "SELECT COUNT(*) FROM sys_dict_type;", _at_least(3)),
    ("sys_config ≥3", "SELECT COUNT(*) FROM sys_config;", _at_least(3)),
    ("sys_api_resource ≥30", "SELECT COUNT(*) FROM sys_api_resource;", _at_least(30)),
    ("sys_menu ≥10", "SELECT COUNT(*) FROM sys_menu WHERE deleted=0;", _at_least(10)),
    ("菜单 system:import 存在", "SELECT COUNT(*) FROM sys_menu WHERE menu_code='system:import';", _at_least(1)),
    ("菜单 system:observability-center 存在", "SELECT COUNT(*) FROM sys_menu WHERE menu_code='system:observability-center';", _at_least(1)),
]


def _query_scalar(conn: dict, sql: str) -> str:
    result = db_init.run_sql(conn, sql, database=conn["db"])
    if result.returncode != 0:
        message = (result.stderr or "").strip()
        if "doesn't exist" in message or "Unknown table" in message:
            raise SeedPendingError("基础表不存在（应用尚未首次启动执行 Flyway）")
        raise db_init.DbError("查询失败: %s\n  %s" % (sql, message))
    return result.stdout.strip()


class SeedPendingError(Exception):
    """库为空（表未建）：等待应用首次启动。"""


def run_checks(conn: dict) -> list:
    """执行全部核对，返回 [(name, ok, detail)]。"""
    results = []
    for name, sql, predicate in SEED_CHECKS:
        out = _query_scalar(conn, sql)
        results.append((name, predicate(out), out))
    return results


def backfill(conn: dict) -> list:
    """对缺失的最小基线做幂等补种，返回实际执行的语句数。"""
    executed = 0
    for stmt in BACKFILL_CORE_SQL + BACKFILL_MENU_SQL:
        result = db_init.run_sql(conn, stmt, database=conn["db"])
        if result.returncode != 0:
            raise db_init.DbError("补种失败: %s\n  %s" % (stmt, (result.stderr or "").strip()))
        executed += 1
    return executed


def cmd_seed(args, env: dict) -> dict:
    """install.py 的 seed 子命令入口：核对 →（可选）补种 → 复核。"""
    conn = db_init.resolve_connection(args, env)
    if args.dry_run:
        return {
            "status": "PLANNED",
            "conn": {k: v for k, v in conn.items() if k != "password"},
            "statements": BACKFILL_CORE_SQL + BACKFILL_MENU_SQL,
        }
    try:
        before = run_checks(conn)
    except SeedPendingError as exc:
        return {
            "status": "PENDING",
            "conn": {k: v for k, v in conn.items() if k != "password"},
            "message": str(exc) + "。请先启动后端（教程 §4.3）完成 Flyway 迁移，再重跑本步骤。",
        }
    failed = [name for name, ok, _ in before if not ok]
    repaired = 0
    if failed:
        repaired = backfill(conn)
    after = run_checks(conn)
    still_failed = [name for name, ok, _ in after if not ok]
    return {
        "status": "PASS" if not still_failed else "FAIL",
        "conn": {k: v for k, v in conn.items() if k != "password"},
        "before": before,
        "repaired_statements": repaired,
        "after": after,
        "failed": still_failed,
    }
