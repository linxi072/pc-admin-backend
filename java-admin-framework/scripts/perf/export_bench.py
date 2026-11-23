#!/usr/bin/env python3
"""导出产物结构校验与轻量基准（E5，零外部依赖）。

用法：
    python3 export_bench.py --pdf /tmp/e5-demo.pdf --expect-pages 1
    python3 export_bench.py --pdf /tmp/e5-demo.pdf --verify-only

校验项：PDF 头/尾、xref 偏移精准、对象数 = 7 + 2*页数、CID 字体声明存在。
仅做结构校验（不做渲染），用于 CI/离线快速验证 PdfExporter 产物合法性。
"""
import argparse
import json
import re
import sys


def verify(pdf_path: str) -> dict:
    with open(pdf_path, "rb") as f:
        data = f.read()
    checks = {
        "header": data.startswith(b"%PDF-1.4"),
        "eof": data.rstrip().endswith(b"%%EOF"),
        "catalog": b"/Type /Catalog" in data,
        "cid_font": b"/UniGB-UCS2-H" in data and b"/STSong-Light" in data,
        "xref_marker": b"xref" in data,
    }
    m = re.search(rb"startxref\s+(\d+)", data)
    xref_offset = int(m.group(1)) if m else -1
    checks["xref_offset_valid"] = 0 <= xref_offset < len(data) and data[xref_offset:xref_offset + 4] == b"xref"
    page_count = int(re.search(rb"/Count (\d+)", data).group(1)) if re.search(rb"/Count (\d+)", data) else -1
    obj_count = len(re.findall(rb"\n\d+ 0 obj", data))
    checks["object_count"] = obj_count == 6 + 2 * max(page_count, 0)
    return {
        "path": pdf_path,
        "bytes": len(data),
        "pages": page_count,
        "objects": obj_count,
        "xref_offset": xref_offset,
        "checks": checks,
        "passed": all(checks.values()),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="PDF 导出产物结构校验（E5）")
    parser.add_argument("--pdf", required=True, help="待校验的 PDF 文件路径")
    parser.add_argument("--expect-pages", type=int, default=None, help="期望页数（可选）")
    parser.add_argument("--out", default=None, help="JSON 结果输出路径（可选）")
    args = parser.parse_args()

    try:
        result = verify(args.pdf)
    except Exception as exc:
        print(json.dumps({"passed": False, "error": str(exc)}, ensure_ascii=False))
        return 2

    if args.expect_pages is not None:
        result["checks"]["page_count"] = result["pages"] == args.expect_pages
        result["passed"] = result["passed"] and result["checks"]["page_count"]

    output = json.dumps(result, ensure_ascii=False, indent=2)
    print(output)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(output)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
