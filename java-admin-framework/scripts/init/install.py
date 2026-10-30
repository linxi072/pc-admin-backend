"""系统初始化安装脚本（总入口）。

用法：
    python3 scripts/init/install.py env                     # 生成/补全 .env（含 JWT_SECRET）
    python3 scripts/init/install.py db                      # 建库（+ 可选业务账号授权）
    python3 scripts/init/install.py seed                    # 基础数据核对 + 幂等补种
    python3 scripts/init/install.py verify                  # 前置条件/迁移/种子/健康检查
    python3 scripts/init/install.py all                     # 串行执行以上四步
    python3 scripts/init/install.py all --dry-run           # 预演：只打印计划不执行
    python3 scripts/init/install.py verify --base-url http://localhost:8080

约定：
- 幂等可重复：任一步骤可反复执行，结果一致；
- 结果可预期：退出码 0=无 FAIL（PENDING/SKIP 不算失败），1=存在 FAIL；
- dry-run 与实跑共用同一计划，先 --dry-run 确认再实跑；
- Python 3.9+，零第三方依赖。
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # 支持直接以脚本方式运行

import db_init  # noqa: E402
import env_setup  # noqa: E402
import seed_check  # noqa: E402
import verify_install  # noqa: E402

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

STATUS_ICON = {
    "PASS": "[ OK ]",
    "FAIL": "[FAIL]",
    "SKIP": "[SKIP]",
    "PENDING": "[WAIT]",
    "PLANNED": "[PLAN]",
}


def load_env_file(path: str) -> dict:
    """把 .env 内容读成环境字典（供步骤解析连接信息）。"""
    env = {}
    for key, value in env_setup.parse_env_file(path):
        env[key] = value
    return env


def print_line(status: str, text: str) -> None:
    print("  %s %s" % (STATUS_ICON.get(status, "[ ?? ]"), text))


def report_env(result: dict) -> None:
    plan = result["plan"]
    target = plan.get("target", args_env_target())
    if result["status"] == "PLANNED":
        print_line("PLANNED", "将%s .env: %s" % ("新建" if plan["created"] else "补全", target))
        for key, value in plan["added"]:
            shown = "(已生成 32 字节 Base64)" if key == "JWT_SECRET" else value
            print("         + %s=%s" % (key, shown))
        return
    if plan["created"]:
        print_line("PASS", "已创建 .env（%d 项，JWT_SECRET 已自动生成）" % len(plan["added"]))
    elif plan["written"]:
        print_line("PASS", "已补全 .env 缺失项 %d 个" % len(plan["added"]))
    elif plan["kept_jwt"]:
        print_line("PASS", ".env 已就绪（JWT_SECRET 为强密钥，予以保留）")
    else:
        print_line("PASS", ".env 已就绪（无需变更）")
    if plan["regenerated"]:
        print_line("PASS", "JWT_SECRET 已按 --force 重新生成（旧密钥立即失效，需重新登录）")


def args_env_target() -> str:
    return os.path.join(REPO_ROOT, ".env")


def report_db(result: dict) -> None:
    if result["status"] == "PLANNED":
        print_line("PLANNED", "将对 %s@%s:%s/%s 执行:" % (
            result["conn"]["user"], result["conn"]["host"], result["conn"]["port"], result["conn"]["db"]))
        for stmt in result["statements"]:
            print("         %s" % stmt)
        return
    for stmt in result["statements"]:
        print("         %s" % stmt)
    if result.get("verified"):
        print_line("PASS", "数据库 %s 已就绪（information_schema 核对通过）" % result["conn"]["db"])
    else:
        print_line("FAIL", "数据库核对未通过")


def report_seed(result: dict) -> None:
    if result["status"] == "PLANNED":
        print_line("PLANNED", "将核对基础数据并对缺失项执行 %d 条幂等补种语句" % len(result["statements"]))
        for stmt in result["statements"]:
            print("         %s" % (stmt[:100] + ("..." if len(stmt) > 100 else "")))
        return
    if result["status"] == "PENDING":
        print_line("PENDING", result["message"])
        return
    for name, ok, detail in result["after"]:
        print_line("PASS" if ok else "FAIL", "%s（当前值 %s）" % (name, detail))
    if result["repaired_statements"]:
        print_line("PASS", "已执行 %d 条幂等补种语句（INSERT IGNORE，重复执行安全）"
                    % result["repaired_statements"])


def report_verify(result: dict) -> None:
    for name, status, detail in result["items"]:
        suffix = " —— %s" % detail if detail else ""
        print_line(status, name + suffix)


def cmd_all(args, env: dict) -> int:
    """串行执行 env → db → seed → verify；返回退出码。"""
    print("== 1/4 环境变量配置 ==")
    try:
        report_env(env_setup.cmd_env(args))
    except Exception as exc:  # noqa: BLE001
        print_line("FAIL", "env 步骤异常: %s" % exc)
        return 1

    print("== 2/4 数据库初始化 ==")
    try:
        report_db(db_init.cmd_db(args, env))
    except db_init.DbError as exc:
        print_line("FAIL", "db 步骤失败: %s" % exc)
        return 1

    print("== 3/4 基础数据核对与补种 ==")
    try:
        report_seed(seed_check.cmd_seed(args, env))
    except (db_init.DbError, seed_check.SeedPendingError) as exc:
        print_line("PENDING" if isinstance(exc, seed_check.SeedPendingError) else "FAIL", str(exc))

    print("== 4/4 安装校验 ==")
    try:
        result = verify_install.cmd_verify(args, env)
        report_verify(result)
        if args.require_health and any(s == "FAIL" and "GET" in n for n, s, _ in result["items"]):
            return 1
        return 0 if result["status"] == "PASS" else 1
    except Exception as exc:  # noqa: BLE001
        print_line("FAIL", "verify 步骤异常: %s" % exc)
        return 1


def build_parser() -> argparse.ArgumentParser:
    # 共享选项同时挂在主 parser 与各子命令上，两种位置（前/后）均可传：
    #   install.py --dry-run all   与   install.py all --dry-run
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--repo-root", default=REPO_ROOT, help="后端仓库根目录（默认按脚本位置推断）")
    common.add_argument("--env-file", default=os.path.join(REPO_ROOT, ".env"), help=".env 路径")
    common.add_argument("--dry-run", action="store_true", help="只打印计划，不执行任何写操作")
    common.add_argument("--force", action="store_true", help="env 步骤强制重新生成 JWT_SECRET")
    common.add_argument("--base-url", default="http://localhost:8080", help="健康检查基础地址")
    common.add_argument("--skip-health", action="store_true", help="跳过健康检查（应用未启动时）")
    common.add_argument("--require-health", action="store_true", help="健康检查失败即退出码 1")
    common.add_argument("--db-url", help="覆盖 DB_PRIMARY_URL")
    common.add_argument("--db-host", help="覆盖主机")
    common.add_argument("--db-port", type=int, help="覆盖端口")
    common.add_argument("--db-name", help="覆盖库名")
    common.add_argument("--db-user", help="覆盖用户名")
    common.add_argument("--db-password", help="覆盖口令")
    common.add_argument("--app-user", help="可选：创建业务账号（默认仅用管理员账号建库）")
    common.add_argument("--app-password", help="业务账号口令（与 --app-user 配对）")

    parser = argparse.ArgumentParser(
        prog="install.py",
        parents=[common],
        description="java-admin-framework 系统初始化安装脚本（幂等、可重复、dry-run 预演）",
    )
    sub = parser.add_subparsers(dest="command")
    for name in ("env", "db", "seed", "verify", "all"):
        sub.add_parser(name, parents=[common], help="%s 步骤" % name)
    return parser


def main(argv=None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if not args.command:
        parser.print_help()
        return 1
    env = load_env_file(args.env_file)

    print("java-admin-framework 初始化安装（repo=%s, dry_run=%s）" % (args.repo_root, args.dry_run))
    if args.command == "env":
        try:
            report_env(env_setup.cmd_env(args))
        except Exception as exc:  # noqa: BLE001
            print_line("FAIL", str(exc))
            return 1
        return 0
    if args.command == "db":
        try:
            result = db_init.cmd_db(args, env)
            report_db(result)
            return 0 if result["status"] in ("PASS", "PLANNED") else 1
        except db_init.DbError as exc:
            print_line("FAIL", str(exc))
            return 1
    if args.command == "seed":
        try:
            result = seed_check.cmd_seed(args, env)
            report_seed(result)
            return 0 if result["status"] in ("PASS", "PENDING", "PLANNED") else 1
        except (db_init.DbError, seed_check.SeedPendingError) as exc:
            print_line("FAIL", str(exc))
            return 1
    if args.command == "verify":
        try:
            result = verify_install.cmd_verify(args, env)
            report_verify(result)
            return 0 if result["status"] == "PASS" else 1
        except Exception as exc:  # noqa: BLE001
            print_line("FAIL", str(exc))
            return 1
    if args.command == "all":
        return cmd_all(args, env)
    return 1


if __name__ == "__main__":
    sys.exit(main())
