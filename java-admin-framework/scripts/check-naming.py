#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-naming.py — Java 标识符命名约定校验器（零依赖，仅用标准库）

目标：在不引入任何第三方依赖（兼容离线 `mvn -o` / CI 环境）的前提下，对
Java 源码做**纯标识符命名约定**的静态校验，与 ``check-conventions.py``（架构 /
层 / 目录结构约定）职责互补、互不重叠：

  * check-conventions.py 负责：包级环、重复 FQN、依赖方向、数据类放置、模块 DAG。
  * check-naming.py     负责：标识符大小写风格（类型 / 常量 / 方法 / 字段）。

校验项（命名风格，符合 Java 通用约定）：
  N1（硬）. 类型（class / interface / enum / record）名称必须为 PascalCase
            —— 首字母大写，仅含字母数字，不得含下划线。
  N2（硬）. ``static final`` 常量名称必须为 UPPER_SNAKE
            —— 全大写 + 下划线（``serialVersionUID`` 等序列化惯例名豁免）。
  N3（警）. 方法名称必须为 camelCase（首字母小写）。
  N4（警）. 字段 / 局部变量 / 参数名称必须为 camelCase。

退出码：发现任何 N1 / N2 硬违规 -> 1；仅 N3 / N4 警告或无问题 -> 0。

设计取舍：
  * 不追踪花括号嵌套，改用「声明上下文」启发式，规避方法调用 / 链式调用 / 控制流
    关键字（return、throw、new、obj.method()）被误判为方法或字段声明。
  * 方法声明要求返回类型为「首字母大写类型 / 已知基本类型 / String / void」，从而
    与「小写开头的调用方（obj.foo()、return foo()）」自然区分。
  * 字段声明要求标识符后紧跟 `= ; , )` 之一，从而与「方法名后紧跟 (」区分。
  * 构造函数名 == 类型名（首字母大写），本就不满足方法名「首字母小写」，故被天然排除。

用法：
  python3 scripts/check-naming.py                       # 自动发现 scaffold-*/src/main/java
  python3 scripts/check-naming.py --src src/main/java   # 指定单目录（单模块 / 回归测试）
  python3 scripts/check-naming.py --src a b --quiet     # 多目录 + 仅报告摘要
"""
import argparse
import os
import re
import sys

ROOT_PKG = "com.acme.scaffold"
MODULE_PREFIX = "scaffold-"

# ---- 复用 check-conventions.py 的包 / 类型识别思路 ----
PKG_RE = re.compile(r"^package\s+([\w\.]+);", re.M)
TYPE_RE = re.compile(
    r"(?:public\s+|abstract\s+|final\s+|strictfp\s+|@\w+(?:\([^)]*\))?\s*)*"
    r"(?:class|interface|enum|record)\s+(\w+)"
)

# 声明上下文下的类型 token（首字母大写 / 基本类型 / String / void）
TYPE_HEAD = (
    r"(?:[A-Z][A-Za-z0-9_]*(?:\s*<\s*[^>]*\s*>)?(?:\s*\[\s*\])?"
    r"|void|int|long|boolean|double|float|char|byte|short|String)\b"
)
# 标识符
IDENT = r"[A-Za-z_]\w*"
# 修饰符（可选，非捕获）
MODS = (
    r"(?:public|protected|private|static|final|abstract|native|synchronized|"
    r"transient|volatile|default|strictfp|@\w+(?:\([^)]*\))?\s*)*?"
)
# 方法声明：返回类型 + 方法名 + (；方法名允许任意字母开头，后续通过
# 「方法名 == 返回类型简单名」排除构造函数（如 public Foo() 中 Foo==Foo）。
METHOD_RE = re.compile(
    r"\b(?P<ret>" + TYPE_HEAD + r")\s+(?P<name>[A-Za-z_]\w*)\s*\("
)
# 字段 / 局部 / 参数声明：类型 + 名称 + 紧随其后的 = ; , )
FIELD_RE = re.compile(
    r"\b" + MODS + r"(?P<type>" + TYPE_HEAD + r")\s+(?P<name>" + IDENT + r")"
    r"\s*(?:=|;|,|\)|\bthis\b)"
)
# 常量：显式 static final
CONST_RE = re.compile(r"\bstatic\s+final\s+(?P<decl>[^\n;=]+?)\s*(?:=|;)")

# 豁免：序列化惯例常量（Java 序列化规范要求此名，属合法 camelCase 例外）
CONST_ALLOWLIST = {"serialVersionUID"}

JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new", "package",
    "private", "protected", "public", "return", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient",
    "try", "void", "volatile", "while", "var", "record",
}


def is_pascal(name):
    return re.match(r"^[A-Z][A-Za-z0-9]*$", name) is not None


def is_camel(name):
    return re.match(r"^[a-z][A-Za-z0-9]*$", name) is not None


def is_upper_snake(name):
    # 全大写 + 下划线（允许纯大写单字，如 MAX）；含小写即不合规
    return re.match(r"^[A-Z][A-Z0-9_]*$", name) is not None


def strip_java_comments_keep_lines(src):
    """剥离 Java 注释与字符串字面量，保留换行以维持行号对齐。

    注释中的 ``class Foo`` / ``someMethod()`` 等文本会被误判为声明，须先行剥离；
    字符串字面量中的代码片段同理。
    """
    lines = src.split("\n")
    out = []
    in_block = False
    str_re = re.compile(r'"([^"\\]|\\.)*"')
    for line in lines:
        res = []
        i = 0
        if in_block:
            end = line.find("*/")
            if end == -1:
                res.append(" " * len(line))
                out.append("".join(res))
                continue
            res.append(" " * (end + 2))
            line = line[end + 2:]
        j = 0
        while j < len(line):
            two = line[j:j + 2]
            if two == "//":
                res.append(" " * (len(line) - j))
                break
            if two == "/*":
                end = line.find("*/", j + 2)
                if end == -1:
                    res.append(" " * (len(line) - j))
                    in_block = True
                    break
                res.append(" " * (end + 2 - j))
                j = end + 2
                continue
            if line[j] == '"':
                m = str_re.match(line, j)
                if m:
                    res.append('""')
                    j = m.end()
                    continue
            res.append(line[j])
            j += 1
        out.append("".join(res))
    return out


def iter_java(src):
    for dp, _, fs in os.walk(src):
        for f in fs:
            if f.endswith(".java"):
                yield os.path.join(dp, f)


def find_base(start_dirs):
    for start in start_dirs:
        d = os.path.abspath(start)
        while True:
            if os.path.isfile(os.path.join(d, "scaffold-common", "pom.xml")):
                return d
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
    return None


def discover_src_roots(base):
    roots = []
    if not os.path.isdir(base):
        return roots
    for name in sorted(os.listdir(base)):
        candidate = os.path.join(base, name, "src", "main", "java")
        if name.startswith(MODULE_PREFIX) and os.path.isdir(candidate):
            roots.append(candidate)
    return roots


class Finding:
    __slots__ = ("rule", "line", "name", "msg")

    def __init__(self, rule, line, name, msg):
        self.rule = rule
        self.line = line
        self.name = name
        self.msg = msg


def line_of(text, pos):
    return text.count("\n", 0, pos) + 1


def check_text(path, text, findings):
    """对单文件（已读为完整字符串）做命名扫描。"""
    lines = strip_java_comments_keep_lines(text)
    stripped_text = "\n".join(lines)

    # ---- N1：类型 PascalCase（全文件 finditer，按位置算行号）----
    for m in TYPE_RE.finditer(stripped_text):
        name = m.group(1)
        if not is_pascal(name):
            findings.append(Finding(
                "N1", line_of(stripped_text, m.start()),
                name, f"类型名应为 PascalCase（首字母大写、无下划线），实际：{name}"))

    # ---- N2：常量 UPPER_SNAKE + N3/N4：方法 / 字段 camelCase ----
    for idx, line in enumerate(lines, 1):
        if not line.strip():
            continue

        # 常量优先：显式 static final
        cm = CONST_RE.search(line)
        if cm:
            decl = cm.group("decl")
            toks = re.findall(r"[A-Za-z_]\w*", decl)
            # 去掉前导修饰符，取最后一个标识符即变量名
            name = None
            for t in reversed(toks):
                if t not in JAVA_KEYWORDS:
                    name = t
                    break
            if name and name not in CONST_ALLOWLIST and not is_upper_snake(name):
                findings.append(Finding(
                    "N2", idx, name,
                    f"static final 常量应为 UPPER_SNAKE（全大写 + 下划线），实际：{name}"))
            # 常量不重复做 camelCase 检查
            continue

        # 方法声明
        for mm in METHOD_RE.finditer(line):
            name = mm.group("name")
            if name in JAVA_KEYWORDS:
                continue
            # 排除构造函数：方法名与返回类型简单名相同（如 public Foo()）
            ret = mm.group("ret")
            simple = ret.split("<")[0].split("[")[0].strip()
            if simple == name:
                continue
            if not is_camel(name):
                findings.append(Finding(
                    "N3", idx, name,
                    f"方法名应为 camelCase（首字母小写），实际：{name}"))

        # 字段 / 局部 / 参数声明
        for fm in FIELD_RE.finditer(line):
            name = fm.group("name")
            if name in JAVA_KEYWORDS:
                continue
            # 跳过与方法声明重叠（方法名后紧跟 (，已被 FIELD_RE 的终结符集合排除，
            # 但参数列表中的类型+名称会被命中——参数同样应 camelCase，故保留检查）
            if not is_camel(name):
                findings.append(Finding(
                    "N4", idx, name,
                    f"字段/局部变量/参数名应为 camelCase（首字母小写），实际：{name}"))


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", action="append", default=None,
                    help="Java 源码目录（可重复传入多个）；默认自动发现 <base>/scaffold-*/src/main/java")
    ap.add_argument("--quiet", action="store_true",
                    help="仅打印摘要（违规汇总），不逐条列出")
    args = ap.parse_args(argv)

    script_base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if args.src:
        roots = args.src
    else:
        base = find_base([os.getcwd(), script_base])
        if base:
            roots = discover_src_roots(base)
            if not roots:
                print(f"[ERROR] 工程根 {base} 下未发现 {MODULE_PREFIX}*/src/main/java",
                      file=sys.stderr)
                return 2
        else:
            roots = ["src/main/java"]

    missing = [r for r in roots if not os.path.isdir(r)]
    if missing:
        print(f"[ERROR] 源码目录不存在: {', '.join(missing)}", file=sys.stderr)
        return 2

    files = []
    for r in roots:
        files.extend(iter_java(r))
    print(f"扫描源码根: {len(roots)} 个")
    print(f"扫描 Java 文件: {len(files)}")

    all_findings = []  # (path, Finding)
    for fp in files:
        try:
            with open(fp, encoding="utf-8", errors="ignore") as fh:
                text = fh.read()
        except OSError as exc:
            print(f"[WARN] 读取失败: {fp} ({exc})", file=sys.stderr)
            continue
        fs = []
        check_text(fp, text, fs)
        for f in fs:
            all_findings.append((fp, f))

    # 汇总
    hard = [x for x in all_findings if x[1].rule in ("N1", "N2")]
    warn = [x for x in all_findings if x[1].rule in ("N3", "N4")]
    by_rule = {}
    for _, f in all_findings:
        by_rule[f.rule] = by_rule.get(f.rule, 0) + 1

    print("=" * 64)
    print("命名约定校验结果")
    print("=" * 64)
    if not all_findings:
        print("✅ 全部通过，无命名违规。")
    else:
        summary = "  ".join(f"{r}={by_rule[r]}" for r in sorted(by_rule))
        print(f"违规摘要：{summary}（共 {len(all_findings)} 条；"
              f"硬 N1/N2={len(hard)}，警告 N3/N4={len(warn)}）")
        if not args.quiet:
            # 按规则分组、同规则内按文件排序，输出 file:line:rule: 名称
            order = {"N1": 0, "N2": 1, "N3": 2, "N4": 3}
            grouped = sorted(
                all_findings,
                key=lambda x: (order.get(x[1].rule, 9), x[0], x[1].line))
            cur = None
            for fp, f in grouped:
                if f.rule != cur:
                    cur = f.rule
                    print(f"\n[{f.rule}] {'硬' if f.rule in ('N1','N2') else '警告'}"
                          f" — {'类型 PascalCase' if f.rule=='N1' else
                                '常量 UPPER_SNAKE' if f.rule=='N2' else
                                '方法 camelCase' if f.rule=='N3' else '字段/局部/参数 camelCase'}")
                rel = os.path.relpath(fp)
                print(f"  {rel}:{f.line}:{f.rule}: {f.msg}")
    print("=" * 64)

    return 1 if hard else 0


if __name__ == "__main__":
    sys.exit(main())
