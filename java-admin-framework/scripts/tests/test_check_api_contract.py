#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-api-contract.py 的扫描行为回归测试（仅标准库 unittest）。

锁定：引号字面量与反引号模板双通道扫描、${expr} → {} 归一、单测夹具排除，
request/requestRaw 对象字面量、axios sugar 与本地 POST helper 的方法解析，
以及真实仓库下路径与方法级差异均为 0、backend_only 大幅下降的修复效果。
可直接执行：python3 scripts/tests/test_check_api_contract.py
"""
from __future__ import annotations

import importlib.util
import os
import sys
import unittest

SCRIPTS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, SCRIPTS_DIR)

# 门禁脚本按仓库约定使用连字符文件名，需按路径加载而非普通 import。
_spec = importlib.util.spec_from_file_location(
    "check_api_contract", os.path.join(SCRIPTS_DIR, "check-api-contract.py")
)
_contract = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_contract)

FRONT_URL_RE = _contract.FRONT_URL_RE
FRONT_TEMPLATE_RE = _contract.FRONT_TEMPLATE_RE
norm = _contract.norm
norm_frontend_url = _contract.norm_frontend_url
frontend_urls = _contract.frontend_urls
frontend_calls = _contract.frontend_calls
frontend_calls_from_text = _contract.frontend_calls_from_text
backend_routes = _contract.backend_routes


class ContractScannerTests(unittest.TestCase):
    def test_quoted_literal_still_matched(self):
        found = [m.group(1) for m in FRONT_URL_RE.finditer("url: '/api/system/users'")]
        self.assertEqual(found, ["/api/system/users"])

    def test_backtick_template_matched(self):
        found = [m.group(1) for m in FRONT_TEMPLATE_RE.finditer("url: `/api/system/users/${id}`")]
        self.assertEqual(found, ["/api/system/users/${id}"])

    def test_template_expr_normalized_to_placeholder(self):
        self.assertEqual(norm_frontend_url("/api/system/users/${id}"), "/api/system/users/{}")
        self.assertEqual(
            norm_frontend_url("/api/observability/traces/${encodeURIComponent(traceId)}"),
            "/api/observability/traces/{}",
        )

    def test_template_aligned_with_backend_path_variable(self):
        self.assertEqual(
            norm_frontend_url("/api/system/users/${id}"),
            norm("/api/system/users/{id}"),
        )

    def test_trailing_slash_normalized(self):
        self.assertEqual(norm_frontend_url("/api/system/users/"), "/api/system/users")

    def test_repo_scan_includes_template_routes(self):
        urls = set(frontend_urls())
        self.assertIn("/api/system/users/{}", urls)
        self.assertIn("/api/observability/traces/{}", urls)
        self.assertIn("/api/workflow/monitor/definitions/{}/suspend", urls)

    def test_repo_scan_excludes_test_fixtures(self):
        urls = set(frontend_urls())
        self.assertNotIn("/api/monitoring/alert-rules/r-1", urls)
        self.assertNotIn("/api/workflow/admin/instances/pi-1/migrate", urls)

    def test_repo_contract_is_clean(self):
        backend = {path for _, path in backend_routes()}
        frontend = set(frontend_urls())
        self.assertEqual(frontend - backend, set())
        # 修复前 backend_only=144；模板串纳管后应大幅收敛（剩余为 OAuth2/探针/未接入端点）。
        self.assertLess(len(backend - frontend), 60)

    def test_object_literal_method_with_nested_braces_and_template(self):
        sample = """
export const copy = (id) => request({
  headers: { 'X-Test': '1' },
  method: 'POST',
  url: `/api/system/users/${id}/copy`
})
"""
        self.assertEqual(
            set(frontend_calls_from_text(sample)),
            {("POST", "/api/system/users/{}/copy")},
        )

    def test_axios_sugar_method_is_captured(self):
        sample = "return refreshAxios.post('/api/auth/refresh', { refreshToken })"
        self.assertEqual(set(frontend_calls_from_text(sample)), {("POST", "/api/auth/refresh")})

    def test_local_post_helper_method_is_captured_at_call_site(self):
        sample = """
function postWithIdempotencyKey(url, body) {
  return request({ method: 'POST', url, headers: { 'Idempotency-Key': body.operationId } })
}
export const approve = (body) => postWithIdempotencyKey('/api/approval/actions/approve', body)
"""
        self.assertEqual(
            set(frontend_calls_from_text(sample)),
            {("POST", "/api/approval/actions/approve")},
        )

    def test_comments_and_missing_method_are_ignored(self):
        sample = """
// request({ method: 'POST', url: '/api/fake/comment' })
export const bad = () => request({ url: '/api/fake/no-method' })
"""
        self.assertEqual(frontend_calls_from_text(sample), {})

    def test_trailing_slash_concatenation_keeps_method(self):
        sample = "request({ method: 'GET', url: '/api/system/files/download/' + fileId })"
        self.assertEqual(
            set(frontend_calls_from_text(sample)),
            {("GET", "/api/system/files/download/{}")},
        )

    def test_repo_method_level_contract_is_clean(self):
        backend = set(backend_routes())
        calls = set(frontend_calls())
        self.assertEqual(calls - backend, set())
        self.assertIn(("POST", "/api/auth/refresh"), calls)
        self.assertIn(("POST", "/api/approval/actions/approve"), calls)
        self.assertIn(("GET", "/api/system/users/{}"), calls)


if __name__ == "__main__":
    unittest.main(verbosity=2)
