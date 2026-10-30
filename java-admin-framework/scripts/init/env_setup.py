"""环境变量配置步骤：由 env.template 生成/补全仓库根目录 .env。

设计约束：
- 幂等可重复：.env 已存在时只补缺失键，不覆盖既有值（--force 仅重置 JWT_SECRET）；
- 结果可预期：所有动作先汇总为计划（plan），dry-run 与实跑走同一计划；
- 零第三方依赖，Python 3.9+ 兼容。
"""

from __future__ import annotations

import base64
import os
import secrets

JWT_SECRET_PLACEHOLDER = "__GENERATE_JWT_SECRET__"
JWT_SECRET_BYTES = 32  # 256 位，满足 HS256 最低要求


def generate_jwt_secret() -> str:
    """生成 32 字节随机数的 Base64 串（44 字符）。"""
    return base64.b64encode(secrets.token_bytes(JWT_SECRET_BYTES)).decode("ascii")


def is_strong_secret(value: str) -> bool:
    """校验 JWT_SECRET：非空、Base64 可解码、解码后 ≥32 字节。"""
    if not value or value == JWT_SECRET_PLACEHOLDER:
        return False
    try:
        raw = base64.b64decode(value, validate=True)
    except Exception:
        return False
    return len(raw) >= JWT_SECRET_BYTES


def parse_env_file(path: str) -> list:
    """解析 .env 为 [(key, value)]，保留文件顺序；忽略注释与空行。"""
    pairs = []
    if not os.path.isfile(path):
        return pairs
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or "=" not in stripped:
                continue
            key, _, value = stripped.partition("=")
            pairs.append((key.strip(), value.strip()))
    return pairs


def plan_env_update(target_path: str, template_path: str, force: bool = False) -> dict:
    """汇总 .env 的生成/补全计划（不落盘）。

    返回：
        exists        .env 是否已存在
        created       是否需要新建（模板拷贝）
        added         需补入的 [(key, value)]
        regenerated   JWT_SECRET 是否需重新生成（force 且已存在）
        kept_jwt      既有 JWT_SECRET 是否强密钥且保留
        secret        最终将写入的 JWT_SECRET（新建/补入/保留三种来源）
    """
    plan = {
        "exists": os.path.isfile(target_path),
        "created": False,
        "added": [],
        "regenerated": False,
        "kept_jwt": False,
        "secret": "",
    }
    template_pairs = parse_env_file(template_path)
    if not template_pairs:
        raise ValueError("env 模板为空或不存在: %s" % template_path)
    existing = dict(parse_env_file(target_path)) if plan["exists"] else {}

    if not plan["exists"]:
        plan["created"] = True
        for key, value in template_pairs:
            if key == "JWT_SECRET":
                plan["secret"] = generate_jwt_secret()
                plan["added"].append((key, plan["secret"]))
            else:
                plan["added"].append((key, value))
        return plan

    # 已存在：补缺失键；JWT_SECRET 单独处理
    for key, value in template_pairs:
        if key not in existing:
            if key == "JWT_SECRET":
                plan["secret"] = generate_jwt_secret()
                plan["added"].append((key, plan["secret"]))
            else:
                plan["added"].append((key, value))

    current_secret = existing.get("JWT_SECRET", "")
    if "JWT_SECRET" in existing:
        if force or not is_strong_secret(current_secret):
            plan["secret"] = generate_jwt_secret()
            plan["regenerated"] = True
            plan["added"] = [(k, v) for k, v in plan["added"] if k != "JWT_SECRET"]
            plan["added"].append(("JWT_SECRET", plan["secret"]))
        else:
            plan["kept_jwt"] = True
            plan["secret"] = current_secret
    return plan


def apply_env_update(target_path: str, template_path: str, force: bool = False) -> dict:
    """执行计划（幂等）。返回计划 dict 附加 written 标记。"""
    plan = plan_env_update(target_path, template_path, force=force)
    plan["written"] = False
    if plan["created"]:
        lines = ["# 由 scripts/init/install.py env 生成（模板: scripts/init/env.template）"]
        lines += ["%s=%s" % (k, v) for k, v in plan["added"]]
        with open(target_path, "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")
        plan["written"] = True
        return plan
    if plan["added"]:
        with open(target_path, "a", encoding="utf-8") as fh:
            fh.write("\n# === 由 scripts/init/install.py env 补全（%s）===\n" % _now_tag())
            for key, value in plan["added"]:
                fh.write("%s=%s\n" % (key, value))
        plan["written"] = True
    return plan


def _now_tag() -> str:
    import datetime

    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def cmd_env(args) -> dict:
    """install.py 的 env 子命令入口。返回步骤结果。"""
    target = args.env_file
    template = os.path.join(os.path.dirname(os.path.abspath(__file__)), "env.template")
    if args.dry_run:
        plan = plan_env_update(target, template, force=args.force)
        plan["dry_run"] = True
        plan["target"] = target
        return {"status": "PLANNED", "plan": plan}
    plan = apply_env_update(target, template, force=args.force)
    plan["target"] = target
    return {"status": "PASS", "plan": plan}
