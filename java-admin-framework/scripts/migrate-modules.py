#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
migrate-modules.py — V1.0.3 模块拆分一次性迁移脚本（幂等，可重复执行）。

将单模块 java-admin-framework 按功能维度拆为 11 个 Maven 子模块
（方案见 docs/manual/module-split.md）。包名保持 com.acme.scaffold.* 不变，
仅做物理目录迁移 + 少量解环重构（依赖倒置）。
"""
import os
import re
import shutil
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # java-admin-framework/（脚本位于 scripts/ 下）
SRC = os.path.join(BASE, "src")

MODULES = {
    "common": "scaffold-common",
    "framework": "scaffold-framework",
    "system": "scaffold-system",
    "workflow": "scaffold-workflow",
    "job": "scaffold-job",
    "monitor": "scaffold-monitor",
    "workbench": "scaffold-workbench",
    "codegen": "scaffold-codegen",
    "kafka": "scaffold-kafka",
    "realtime": "scaffold-realtime",
    "admin": "scaffold-admin",
}


def log(msg):
    print(msg)


def move_tree(src_dir, dst_dir):
    """移动目录（已存在则跳过，保证幂等）。"""
    if not os.path.isdir(src_dir):
        return False
    os.makedirs(os.path.dirname(dst_dir), exist_ok=True)
    if os.path.exists(dst_dir):
        # 目标已存在：逐文件合并（幂等重跑）
        for root, _, files in os.walk(src_dir):
            for f in files:
                s = os.path.join(root, f)
                rel = os.path.relpath(s, src_dir)
                d = os.path.join(dst_dir, rel)
                os.makedirs(os.path.dirname(d), exist_ok=True)
                shutil.move(s, d)
        shutil.rmtree(src_dir)
    else:
        shutil.move(src_dir, dst_dir)
    return True


def move_file(src_file, dst_file):
    if not os.path.isfile(src_file):
        return False
    os.makedirs(os.path.dirname(dst_file), exist_ok=True)
    shutil.move(src_file, dst_file)
    return True


def rewrite_package(path, new_pkg):
    with open(path, encoding="utf-8") as f:
        txt = f.read()
    txt = re.sub(r"^package\s+[\w\.]+;", f"package {new_pkg};", txt, count=1, flags=re.M)
    with open(path, "w", encoding="utf-8") as f:
        f.write(txt)


def rewrite_imports(path, mapping):
    """mapping: [(old_prefix, new_prefix)]，按前缀替换 import 行。"""
    with open(path, encoding="utf-8") as f:
        txt = f.read()
    orig = txt
    for old, new in mapping:
        txt = re.sub(rf"^import\s+{re.escape(old)}([\w\.]*)\s*;",
                     lambda m: f"import {new}{m.group(1)};", txt, flags=re.M)
    if txt != orig:
        with open(path, "w", encoding="utf-8") as f:
            f.write(txt)
        return True
    return False


def iter_java(root):
    for dp, _, fs in os.walk(root):
        for f in fs:
            if f.endswith(".java"):
                yield os.path.join(dp, f)


def main():
    mj = os.path.join(SRC, "main", "java", "com", "acme", "scaffold")
    tj = os.path.join(SRC, "test", "java", "com", "acme", "scaffold")
    mr = os.path.join(SRC, "main", "resources")
    tr = os.path.join(SRC, "test", "resources")

    # ---------- 1. 主源码整包迁移 ----------
    # scaffold-common: common/* + jooq/*
    move_tree(os.path.join(mj, "common"), os.path.join(BASE, "scaffold-common", "src", "main", "java", "com", "acme", "scaffold", "common"))
    move_tree(os.path.join(mj, "jooq"), os.path.join(BASE, "scaffold-common", "src", "main", "java", "com", "acme", "scaffold", "jooq"))
    # scaffold-framework: config/{datasource,jooq,web,cache,redis,observability} + observability/* + monitor/detection
    for sub in ("datasource", "jooq", "web", "cache", "redis", "observability"):
        move_tree(os.path.join(mj, "config", sub),
                  os.path.join(BASE, "scaffold-framework", "src", "main", "java", "com", "acme", "scaffold", "config", sub))
    move_tree(os.path.join(mj, "observability"), os.path.join(BASE, "scaffold-framework", "src", "main", "java", "com", "acme", "scaffold", "observability"))
    move_tree(os.path.join(mj, "monitor", "detection"), os.path.join(BASE, "scaffold-framework", "src", "main", "java", "com", "acme", "scaffold", "monitor", "detection"))
    # scaffold-monitor: monitor 其余子包
    for sub in ("dto", "config", "alert", "health", "controller", "service", "domain"):
        move_tree(os.path.join(mj, "monitor", sub),
                  os.path.join(BASE, "scaffold-monitor", "src", "main", "java", "com", "acme", "scaffold", "monitor", sub))
    # scaffold-system: system/* + security/*
    move_tree(os.path.join(mj, "system"), os.path.join(BASE, "scaffold-system", "src", "main", "java", "com", "acme", "scaffold", "system"))
    move_tree(os.path.join(mj, "security"), os.path.join(BASE, "scaffold-system", "src", "main", "java", "com", "acme", "scaffold", "security"))
    # 其余特性模块整包迁移
    for feat, mod in (("workflow", "workflow"), ("job", "job"), ("workbench", "workbench"),
                      ("codegen", "codegen"), ("kafka", "kafka"), ("realtime", "realtime")):
        move_tree(os.path.join(mj, feat), os.path.join(BASE, f"scaffold-{mod}", "src", "main", "java", "com", "acme", "scaffold", feat))
    # scaffold-admin: 启动类
    move_file(os.path.join(mj, "FrameworkApplication.java"),
              os.path.join(BASE, "scaffold-admin", "src", "main", "java", "com", "acme", "scaffold", "FrameworkApplication.java"))

    # ---------- 2. 解环重构的包迁移（先搬家再改 package） ----------
    # 2a. security.context 的接口/记录 -> common.context（依赖倒置：端口留在公共层）
    ctx_dst = os.path.join(BASE, "scaffold-common", "src", "main", "java", "com", "acme", "scaffold", "common", "context")
    for cls in ("SecurityContextFacade", "CurrentPrincipal"):
        src_f = os.path.join(BASE, "scaffold-system", "src", "main", "java", "com", "acme", "scaffold", "security", "context", cls + ".java")
        if move_file(src_f, os.path.join(ctx_dst, cls + ".java")):
            rewrite_package(os.path.join(ctx_dst, cls + ".java"), "com.acme.scaffold.common.context")
    # 2b. config.web.ClientIpResolver -> common.web（审计/限流/认证共用，公共层）
    cip_dst = os.path.join(BASE, "scaffold-common", "src", "main", "java", "com", "acme", "scaffold", "common", "web")
    cip_src = os.path.join(BASE, "scaffold-framework", "src", "main", "java", "com", "acme", "scaffold", "config", "web", "ClientIpResolver.java")
    if move_file(cip_src, os.path.join(cip_dst, "ClientIpResolver.java")):
        rewrite_package(os.path.join(cip_dst, "ClientIpResolver.java"), "com.acme.scaffold.common.web")
    # 2c. config.jobrunr.JobRunrConfig -> job.config（周期任务注册归调度模块）
    jr_dst = os.path.join(BASE, "scaffold-job", "src", "main", "java", "com", "acme", "scaffold", "job", "config")
    jr_src = os.path.join(BASE, "scaffold-framework", "src", "main", "java", "com", "acme", "scaffold", "config", "jobrunr", "JobRunrConfig.java")
    if move_file(jr_src, os.path.join(jr_dst, "JobRunrConfig.java")):
        rewrite_package(os.path.join(jr_dst, "JobRunrConfig.java"), "com.acme.scaffold.job.config")

    # ---------- 3. 测试迁移 ----------
    # 3a. 常规：按顶层包镜像
    test_map = {
        "common": "scaffold-common", "jooq": "scaffold-common",
        "config": "scaffold-framework", "observability": "scaffold-framework",
        "monitor": None,  # 特殊处理（detection -> framework）
        "security": "scaffold-system", "system": "scaffold-system",
        "workflow": "scaffold-workflow", "job": "scaffold-job", "workbench": "scaffold-workbench",
        "codegen": "scaffold-codegen", "kafka": "scaffold-kafka", "realtime": "scaffold-realtime",
        "e2e": "scaffold-admin", "integration": "scaffold-admin",
        "perf": "scaffold-admin", "tools": "scaffold-admin",
    }
    for feat, mod in test_map.items():
        if mod is None:
            continue
        move_tree(os.path.join(tj, feat), os.path.join(BASE, mod, "src", "test", "java", "com", "acme", "scaffold", feat))
    # monitor 测试拆分
    move_tree(os.path.join(tj, "monitor", "detection"), os.path.join(BASE, "scaffold-framework", "src", "test", "java", "com", "acme", "scaffold", "monitor", "detection"))
    for sub in ("alert", "health", "service"):
        move_tree(os.path.join(tj, "monitor", sub), os.path.join(BASE, "scaffold-monitor", "src", "test", "java", "com", "acme", "scaffold", "monitor", sub))
    # 3b. 跨模块守护测试 -> scaffold-admin/consistency（重命名包）
    cons = os.path.join(BASE, "scaffold-admin", "src", "test", "java", "com", "acme", "scaffold", "consistency")
    for src_f, cls in (
        (os.path.join(BASE, "scaffold-common", "src", "test", "java", "com", "acme", "scaffold", "common", "exception", "BusinessExceptionMessageKeyingTest.java"), "BusinessExceptionMessageKeyingTest"),
        (os.path.join(BASE, "scaffold-framework", "src", "test", "java", "com", "acme", "scaffold", "observability", "export", "ObservabilityPropertiesTest.java"), "ObservabilityPropertiesTest"),
    ):
        if move_file(src_f, os.path.join(cons, cls + ".java")):
            rewrite_package(os.path.join(cons, cls + ".java"), "com.acme.scaffold.consistency")
    # 3c. 集成/端到端散落 IT -> scaffold-admin（保留原包）
    for pkg, cls in (("security", "AuthFlowIT"), ("workflow", "LeaveApprovalWorkflowIT")):
        src_f = os.path.join(BASE, "scaffold-system" if pkg == "security" else "scaffold-workflow",
                             "src", "test", "java", "com", "acme", "scaffold", pkg, cls + ".java")
        move_file(src_f, os.path.join(BASE, "scaffold-admin", "src", "test", "java", "com", "acme", "scaffold", pkg, cls + ".java"))
    # 3d. 基类与 ClientIpResolverTest
    move_file(os.path.join(tj, "BaseIntegrationTest.java"),
              os.path.join(BASE, "scaffold-admin", "src", "test", "java", "com", "acme", "scaffold", "BaseIntegrationTest.java"))
    cip_t = os.path.join(BASE, "scaffold-framework", "src", "test", "java", "com", "acme", "scaffold", "config", "web", "ClientIpResolverTest.java")
    if move_file(cip_t, os.path.join(BASE, "scaffold-common", "src", "test", "java", "com", "acme", "scaffold", "common", "web", "ClientIpResolverTest.java")):
        rewrite_package(os.path.join(BASE, "scaffold-common", "src", "test", "java", "com", "acme", "scaffold", "common", "web", "ClientIpResolverTest.java"), "com.acme.scaffold.common.web")
    # 3e. 测试资源 -> scaffold-admin
    move_tree(tr, os.path.join(BASE, "scaffold-admin", "src", "test", "resources"))

    # ---------- 4. 资源迁移 ----------
    move_tree(os.path.join(mr, "i18n"), os.path.join(BASE, "scaffold-common", "src", "main", "resources", "i18n"))
    move_tree(os.path.join(mr, "db", "codegen"), os.path.join(BASE, "scaffold-common", "src", "main", "resources", "db", "codegen"))
    move_file(os.path.join(mr, "jooq-codegen.xml"), os.path.join(BASE, "scaffold-common", "src", "main", "resources", "jooq-codegen.xml"))
    move_tree(os.path.join(mr, "db", "migration"), os.path.join(BASE, "scaffold-admin", "src", "main", "resources", "db", "migration"))
    move_tree(os.path.join(mr, "db", "reference"), os.path.join(BASE, "scaffold-admin", "src", "main", "resources", "db", "reference"))
    for f in ("application.yml", "application-local.yml", "application-prod.yml", "logback-spring.xml"):
        move_file(os.path.join(mr, f), os.path.join(BASE, "scaffold-admin", "src", "main", "resources", f))

    # ---------- 5. 全局 import 重写 ----------
    mapping = [
        ("com.acme.scaffold.security.context.", "com.acme.scaffold.common.context."),
        ("com.acme.scaffold.config.web.ClientIpResolver", "com.acme.scaffold.common.web.ClientIpResolver"),
    ]
    changed = 0
    for mod_dir in ("scaffold-common", "scaffold-framework", "scaffold-system", "scaffold-workflow",
                    "scaffold-job", "scaffold-monitor", "scaffold-workbench", "scaffold-codegen",
                    "scaffold-kafka", "scaffold-realtime", "scaffold-admin"):
        for jroot in (os.path.join(BASE, mod_dir, "src", "main", "java"), os.path.join(BASE, mod_dir, "src", "test", "java")):
            if not os.path.isdir(jroot):
                continue
            for fp in iter_java(jroot):
                if rewrite_imports(fp, mapping):
                    changed += 1
    log(f"import 重写完成：{changed} 个文件")

    # ---------- 6. 清理空目录 ----------
    if os.path.isdir(SRC):
        shutil.rmtree(SRC)
        log("已清理原 src/ 目录")
    log("迁移完成")


if __name__ == "__main__":
    sys.exit(main())
