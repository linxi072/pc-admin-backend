# -*- coding: utf-8 -*-
"""V32 合并迁移生成器：V1~V31 按版本序原样拼接 + 版本横幅，字节级等价校验。

⚠️ 归档说明（2026-10-21）：本脚本为 V32 合并迁移（2026-10-15）的历史复现工具。
其输入 V1~V31 原件已按「原件删除」策略移除，且迁移目录随后移至
scaffold-admin/src/main/resources/db/migration（V1.0.3 模块拆分），本脚本
已不可直接重跑；保留以供追溯 V32 的生成方式与等价性校验口径。
可执行迁移链的当前位置：scaffold-admin/src/main/resources/db/migration/。
"""
import glob
import os
import re
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # java-admin-framework
MIG = os.path.join(BASE, 'src/main/resources/db/migration')
BANNER_EQ = '=' * 85


def version_key(path):
    m = re.match(r'V(\d+)__', os.path.basename(path))
    return int(m.group(1))


def main():
    # 只取 V1 ~ V31（排除本脚本自产出的 V32，保证可重复执行）
    files = [f for f in glob.glob(os.path.join(MIG, 'V*.sql')) if version_key(f) <= 31]
    files.sort(key=version_key)
    assert len(files) == 31, f'expect 31 migration files, got {len(files)}'
    versions = [version_key(f) for f in files]
    assert versions == list(range(1, 32)), f'version gap: {versions}'

    header = (
        '-- ' + BANNER_EQ + '\n'
        '--  V32__consolidated_schema：全量结构合并迁移（consolidated schema）\n'
        '-- ' + BANNER_EQ + '\n'
        '--  背景：原 V1 ~ V31 共 31 个版本化迁移按「类型分类 + 原件删除」策略重整，\n'
        '--       本文件为其逐版本原样合并（语句级保持原始执行顺序），供 Flyway 在\n'
        '--       全新环境一次性建库；既有环境请勿重复执行（见 README §34 的标记步骤）。\n'
        '--  内容：以下各「来源迁移」块均为原始文件完整拷贝（含原始注释），未改一字。\n'
        '--  顺序：V1__baseline.sql -> V31__report_menu_restore.sql（版本升序）。\n'
        '-- ' + BANNER_EQ + '\n\n'
    )

    parts = [header]
    originals = []
    for f in files:
        name = os.path.basename(f)
        raw = open(f, 'rb').read()
        originals.append(raw)
        body = raw.decode('utf-8')
        if not body.endswith('\n'):
            body += '\n'
        banner = (
            '\n-- ' + BANNER_EQ + '\n'
            f'-- 来源迁移：{name}（原样保留，含原始注释）\n'
            '-- ' + BANNER_EQ + '\n\n'
        )
        parts.append(banner)
        parts.append(body)

    out = ''.join(parts)
    out_path = os.path.join(MIG, 'V32__consolidated_schema.sql')
    with open(out_path, 'w', encoding='utf-8', newline='\n') as w:
        w.write(out)

    # ---- 字节级等价校验：剥离全部横幅后应与 31 个原件拼接完全一致 ----
    banner_re = re.compile(
        r'\n-- ' + '=' * 85 + r'\n-- 来源迁移：V\d+__[^\n]*（原样保留，含原始注释）\n-- ' + '=' * 85 + r'\n\n'
    )
    body_only = banner_re.sub('', out)
    # 去掉文件头说明块（第一个版本横幅之前的内容）
    first = banner_re.search(out)
    body_only = banner_re.sub('', out[first.start():])
    expected = b''.join(r if r.endswith(b'\n') else r + b'\n' for r in originals)
    ok = body_only.encode('utf-8') == expected
    banner_count = len(banner_re.findall(out))
    print(f'generated : {out_path}')
    print(f'size      : {len(out.encode("utf-8"))} bytes, {out.count(chr(10))} lines')
    print(f'banners   : {banner_count} (expect 31)')
    print(f'byte-equal: {ok}')
    if not ok:
        # 定位首个差异
        a, b = body_only.encode('utf-8'), expected
        for i in range(min(len(a), len(b))):
            if a[i] != b[i]:
                print(f'first diff at byte {i}: out={a[max(0,i-40):i+40]!r} exp={b[max(0,i-40):i+40]!r}')
                break
        else:
            print(f'length diff: out={len(a)} exp={len(b)}')
        sys.exit(1)


if __name__ == '__main__':
    main()
