# -*- coding: utf-8 -*-
"""SQL 语句级分类工具：把迁移脚本按语句类型拆分为 DDL / DML 两个参考文件。

规则：
  * 语句切分：字符串 / 行注释 / 块注释 感知，按顶层分号切分，保留原始文本与行号。
  * PREPARE 动态块：SET @var = (...) + PREPARE + EXECUTE + DEALLOCATE 视为一个整体，
    按其内部动态 SQL 的首个关键字归类（CREATE/ALTER/DROP -> DDL，INSERT/UPDATE/DELETE -> DML）。
  * 会话语句（SET NAMES 等）归一：两个文件各保留一条文件头 SET NAMES，不重复罗列。
  * 顺序：DDL / DML 各自按「源文件版本序 + 文件内原始顺序」排列。
"""
import glob
import os
import re
import tempfile
from collections import Counter, defaultdict

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # java-admin-framework
MIG = os.path.join(BASE, 'src/main/resources/db/migration')
REF_DIR = os.path.join(BASE, 'src/main/resources/db/reference')
PERM = os.path.join(os.path.dirname(BASE), 'permission-schema.sql')  # 已并入分类文件，缺失时自动跳过

DDL_KW = {'CREATE', 'ALTER', 'DROP', 'TRUNCATE', 'RENAME'}
DML_KW = {'INSERT', 'UPDATE', 'DELETE', 'REPLACE'}
EQ = '=' * 85


def version_key(path):
    return int(re.match(r'V(\d+)__', os.path.basename(path)).group(1))


def split_sql(text):
    """切分为 comment / stmt 片段，保留行号。"""
    segs = []
    i, n = 0, len(text)

    def ln(pos):
        return text.count('\n', 0, pos) + 1

    def skip_quoted(pos, q):
        pos += 1
        while pos < n:
            if text[pos] == '\\':
                pos += 2
                continue
            if text[pos] == q:
                if pos + 1 < n and text[pos + 1] == q:
                    pos += 2
                    continue
                return pos + 1
            pos += 1
        return pos

    while i < n:
        c = text[i]
        if c in ' \t\r\n':
            i += 1
            continue
        if c == '-' and text.startswith('--', i):
            j = text.find('\n', i)
            j = n if j == -1 else j
            segs.append({'type': 'comment', 'text': text[i:j].strip(), 'start': ln(i), 'end': ln(j)})
            i = j
            continue
        if c == '#':
            j = text.find('\n', i)
            j = n if j == -1 else j
            segs.append({'type': 'comment', 'text': text[i:j].strip(), 'start': ln(i), 'end': ln(j)})
            i = j
            continue
        if c == '/' and text.startswith('/*', i):
            j = text.find('*/', i + 2)
            j = n if j == -1 else j + 2
            segs.append({'type': 'comment', 'text': text[i:j].strip(), 'start': ln(i), 'end': ln(j)})
            i = j
            continue
        start = i
        while i < n:
            c = text[i]
            if c in "'\"":
                i = skip_quoted(i, c)
                continue
            if c == '-' and text.startswith('--', i):
                j = text.find('\n', i)
                i = n if j == -1 else j
                continue
            if c == '#':
                j = text.find('\n', i)
                i = n if j == -1 else j
                continue
            if c == '/' and text.startswith('/*', i):
                j = text.find('*/', i + 2)
                i = n if j == -1 else j + 2
                continue
            if c == ';':
                i += 1
                break
            i += 1
        body = text[start:i].strip()
        if body:
            segs.append({'type': 'stmt', 'text': body, 'start': ln(start), 'end': ln(i)})
    return segs


def first_keyword(text):
    t = re.sub(r'(?m)^\s*--.*$', '', text)
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = t.strip()
    m = re.match(r'[`\(\s]*([A-Za-z]+)', t)
    return m.group(1).upper() if m else ''


def extract_quoted(text):
    """提取单引号字符串内容（处理 '' 与 \\ 转义）。"""
    out, i, n = [], 0, len(text)
    while i < n:
        if text[i] == "'":
            buf, i = [], i + 1
            while i < n:
                if text[i] == '\\':
                    buf.append(text[i:i + 2])
                    i += 2
                    continue
                if text[i] == "'":
                    if i + 1 < n and text[i + 1] == "'":
                        buf.append("'")
                        i += 2
                        continue
                    i += 1
                    break
                buf.append(text[i])
                i += 1
            out.append(''.join(buf))
        else:
            i += 1
    return out


def classify(texts, is_prepare_group):
    if is_prepare_group:
        for t in texts:
            for kw in (first_keyword(s) for s in extract_quoted(t)):
                if kw in DDL_KW:
                    return 'DDL'
                if kw in DML_KW:
                    return 'DML'
        return None
    kw = first_keyword(texts[0])
    if kw in DDL_KW:
        return 'DDL'
    if kw in DML_KW:
        return 'DML'
    return None


def build_units(path, label):
    """返回该源文件的逻辑单元列表：[(kind, [stmt_texts], comments, start, end)]"""
    text = open(path, encoding='utf-8').read()
    segs = split_sql(text)
    units = []
    pending = []
    i = 0
    set_var_re = re.compile(r'^SET\s+@\w+\s*=', re.I | re.S)
    while i < len(segs):
        s = segs[i]
        if s['type'] == 'comment':
            pending.append((s['start'], s['text']))
            i += 1
            continue
        stmts = [s]
        is_group = bool(set_var_re.match(s['text']))
        if is_group:
            j = i + 1
            m = re.match(r'^SET\s+(@\w+)\s*=', s['text'], re.I | re.S)
            var = m.group(1) if m else None
            while j < len(segs):
                nxt = segs[j]
                if nxt['type'] != 'stmt':
                    break
                head = nxt['text'].lstrip()[:40].upper()
                if (var and head.startswith('PREPARE ') and var in nxt['text']) or \
                        head.startswith('EXECUTE ') or head.startswith('DEALLOCATE '):
                    stmts.append(nxt)
                    if head.startswith('DEALLOCATE '):
                        j += 1
                        break
                    j += 1
                    continue
                break
            i = j
        else:
            i += 1
        texts = [x['text'] for x in stmts]
        kind = classify(texts, is_group)
        units.append({
            'kind': kind,
            'texts': texts,
            'comments': pending,
            'start': stmts[0]['start'],
            'end': stmts[-1]['end'],
            'label': label,
            'group': is_group,
        })
        pending = []
    return units


def norm(t):
    return re.sub(r'\s+', ' ', t.strip())


def main():
    os.makedirs(REF_DIR, exist_ok=True)
    migs = [f for f in glob.glob(os.path.join(MIG, 'V*.sql')) if version_key(f) <= 31]
    if not migs:
        # 原件已合并删除：从 V32 按版本横幅还原到临时目录，保证本工具仍可复现
        v32 = os.path.join(MIG, 'V32__consolidated_schema.sql')
        assert os.path.exists(v32), f'既无 V1~V31 原件也无 {v32}'
        banner_re = re.compile(
            r'\n-- ={85}\n-- 来源迁移：(V\d+__[^\n]*)（原样保留，含原始注释）\n-- ={85}\n\n')
        parts = banner_re.split(open(v32, encoding='utf-8').read())
        names, bodies = parts[1::2], parts[2::2]
        tmp = os.path.join(tempfile.gettempdir(), 'orig_sql_restore')
        os.makedirs(tmp, exist_ok=True)
        for old in os.listdir(tmp):
            os.remove(os.path.join(tmp, old))
        for n, b in zip(names, bodies):
            open(os.path.join(tmp, n), 'w', encoding='utf-8', newline='\n').write(b)
        migs = [os.path.join(tmp, n) for n in names]
        print(f'note: V1~V31 原件已合并，从 V32 还原 {len(migs)} 个到 {tmp}')
    migs.sort(key=version_key)
    sources = [(os.path.basename(m), m) for m in migs]
    if os.path.exists(PERM):
        sources.append(('permission-schema.sql', PERM))
    else:
        print('note: permission-schema.sql 不存在（已并入分类文件），跳过')

    all_units = []
    utility = []
    for label, path in sources:
        for u in build_units(path, label):
            if u['kind'] is None:
                utility.append((label, u['start'], u['texts'][0]))
            all_units.append(u)

    ddl = [u for u in all_units if u['kind'] == 'DDL']
    dml = [u for u in all_units if u['kind'] == 'DML']

    # ---------- 完整性校验 ----------
    src_stmts = []
    for u in all_units:
        if u['kind'] is not None:
            src_stmts.extend(u['texts'])
    out_stmts = []
    for u in ddl + dml:
        out_stmts.extend(u['texts'])
    src_ms = Counter(norm(t) for t in src_stmts)
    out_ms = Counter(norm(t) for t in out_stmts)
    missing = sorted((src_ms - out_ms).elements())
    extra = sorted((out_ms - src_ms).elements())

    # PREPARE / DEALLOCATE 配平
    prep = sum(1 for t in out_stmts if t.lstrip().upper().startswith('PREPARE '))
    dealloc = sum(1 for t in out_stmts if t.lstrip().upper().startswith('DEALLOCATE '))

    def render(title, kind, units):
        head = (
            '-- ' + EQ + '\n'
            f'--  {title}\n'
            '-- ' + EQ + '\n'
            '--  生成工具：build_reference.py（语句级分类，字符串 / 注释感知）\n'
            '--  来源文件：db/migration V1__baseline.sql ~ V31__report_menu_restore.sql（31 个）\n'
            '--            + permission-schema.sql（仓库根，权限模块独立建表脚本）\n'
            '--  排列顺序：源文件版本序 + 文件内原始顺序；每条语句前带 [源文件 L行号] 来源注释；\n'
            '--            PREPARE 动态 SQL 块（SET @ddl + PREPARE + EXECUTE + DEALLOCATE）\n'
            '--            按内部动态语句类型整体归类，保持为一个执行单元。\n'
            '--  会话语句：各源文件中的 SET NAMES 等会话指令归一为文件头单条。\n'
            '--\n'
            '--  ⚠️ 本文件为「分类参考件」，不可直接当作数据库迁移执行！\n'
            '--     跨版本依赖例证：V6 删除 sys_user_role / sys_user_org 后，V15 又重建两表并\n'
            '--     从 sys_user.role_id 回填数据，随后 V15 删除 role_id / org_id / primary_org_id 列。\n'
            '--     若把 DDL 与 DML 拆成两份先后执行，DML 中的回填语句会撞上已被删除的列，\n'
            '--     直接抛 ERROR 1054 Unknown column \'role_id\'，任何新库都建不起来。\n'
            '--     可执行的全量迁移请使用：db/migration/V32__consolidated_schema.sql（保持原始交错顺序）。\n'
            '-- ' + EQ + '\n\n'
            'SET NAMES utf8mb4;\n\n'
        )
        parts = [head]
        cur = None
        for u in units:
            if u['label'] != cur:
                cur = u['label']
                parts.append('\n-- ' + EQ + f'\n-- 来源：{cur}\n-- ' + EQ + '\n\n')
            rng = f'L{u["start"]}' if u['start'] == u['end'] else f'L{u["start"]}-{u["end"]}'
            parts.append(f'-- [{cur} {rng}]')
            for _, ctext in u['comments']:
                if ctext:
                    parts.append(ctext)
            for t in u['texts']:
                parts.append(t if t.endswith(';') else t + ';')
            parts.append('')
        return '\n'.join(parts) + '\n'

    ddl_path = os.path.join(REF_DIR, 'schema_ddl.sql')
    dml_path = os.path.join(REF_DIR, 'data_dml.sql')
    open(ddl_path, 'w', encoding='utf-8', newline='\n').write(
        render('schema_ddl.sql —— 全量结构定义（DDL）分类汇总', 'DDL', ddl))
    open(dml_path, 'w', encoding='utf-8', newline='\n').write(
        render('data_dml.sql —— 全量数据操作（DML）分类汇总', 'DDL', dml))

    print(f'DDL 文件 : {ddl_path}  ({len(ddl)} 单元)')
    print(f'DML 文件 : {dml_path}  ({len(dml)} 单元)')
    print(f'语句总数  : DDL={sum(len(u["texts"]) for u in ddl)} DML={sum(len(u["texts"]) for u in dml)}')
    print(f'完整性   : 缺失={len(missing)} 多余={len(extra)}  PREPARE={prep} DEALLOCATE={dealloc}')
    print(f'会话语句  : {len(utility)} 条（已归一）')
    for lab, line, t in utility:
        print(f'   - {lab} L{line}: {norm(t)[:70]}')
    if missing:
        print('MISSING:')
        for m in missing[:10]:
            print('   -', m[:120])
    if extra:
        print('EXTRA:')
        for e in extra[:10]:
            print('   -', e[:120])
    # ---------- 字符流守恒校验：剥离空白后，解析器切出的全部片段应等于源文件 ----------
    strip_ws = lambda s: re.sub(r'\s+', '', s)
    charstream_bad, no_semicolon = [], []
    for label, path in sources:
        raw = open(path, encoding='utf-8').read()
        segs = split_sql(raw)
        joined = strip_ws(''.join(s['text'] for s in segs))
        if joined != strip_ws(raw):
            charstream_bad.append((label, len(joined), len(strip_ws(raw))))
        for u in all_units:
            if u['label'] != label:
                continue
            for t in u['texts']:
                if not t.rstrip().endswith(';'):
                    no_semicolon.append((label, u['start'], norm(t)[:60]))
    print(f'字符流守恒: {"OK" if not charstream_bad else "FAIL"}  (异常文件: {charstream_bad})')
    print(f'终止符检查: {"OK" if not no_semicolon else "FAIL"}  (缺分号: {no_semicolon[:5]})')
    per = defaultdict(Counter)
    for u in all_units:
        if u['kind']:
            per[u['label']][u['kind']] += len(u['texts'])
    print('--- 各源文件语句分布 ---')
    for lab, c in per.items():
        print(f'   {lab:52s} DDL={c["DDL"]:3d} DML={c["DML"]:3d}')


if __name__ == '__main__':
    main()
