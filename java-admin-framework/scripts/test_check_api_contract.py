#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-api-contract.py 的行为回归测试（零依赖，仅标准库）。

通过进程内加载真实脚本 + subprocess 调用 ``main()``，对纯函数与临时夹具做断言，
覆盖契约对称性检查的全部关键分支：

  * normalize：查询串 / 片段 / 合并斜杠 / 尾斜杠 / 小写 / 格式化串 / 空路径
  * param_key：{x}、${x}、UUID 等参数段归一化，方法与路径拼接
  * first_quoted：value=/path= 优先、回退首字面量、无引号
  * quoted_paths / 多路径别名：@RequestMapping({"/old","/new"}) 新旧双挂的笛卡尔积
  * backend_source_roots：仅认 ``scaffold-*/src/main/java``
  * scan_backend：类级 @RequestMapping 基路径、动词注解、方法级 @RequestMapping
  * scan_frontend：request({url,method}) / axios.* / 动态拼接 / 目录不存在
  * main：--json 三分类、--strict 退出码、默认信息性不阻断

夹具采用「纯增量」设计：只创建目录与文件、不删除。本仓沙箱禁止 unlink，
依赖删除回滚的夹具会连锁失败；每个用例使用独立临时目录互不影响。
"""
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(SCRIPT_DIR, "check-api-contract.py")

_spec = importlib.util.spec_from_file_location("check_api_contract_mod", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

JAVA_OK = """\
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    @GetMapping("/list")
    public void list() {}
    @PostMapping
    public void create() {}
    @RequestMapping(value = "/sync", method = RequestMethod.PUT)
    public void sync() {}
}
"""

JS_OK = """\
request({ url: '/api/demo/list', method: 'get' })
request({ url: '/api/demo/' + id, method: 'post' })
axios.delete('/api/demo/42')
"""


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def make_fixture(java_text=JAVA_OK, js_text=JS_OK, module="scaffold-demo",
                 extra_roots=()):
    """构造临时后端仓根 + 前端 src/api 目录；返回 (backend_root, api_dir)。"""
    base = tempfile.mkdtemp(prefix="apichk_")
    backend = os.path.join(base, "backend")
    api_dir = os.path.join(base, "frontend", "src", "api")
    src = os.path.join(backend, module, "src", "main", "java")
    os.makedirs(src)
    os.makedirs(api_dir)
    if java_text is not None:
        write(os.path.join(src, "DemoController.java"), java_text)
    if js_text is not None:
        write(os.path.join(api_dir, "demo.js"), js_text)
    for root in extra_roots:
        os.makedirs(os.path.join(backend, root), exist_ok=True)
    return backend, api_dir


def run(backend, api_dir, *extra):
    """返回 ``(returncode, stdout, stderr)``，刻意不合并两路输出。

    受限沙箱会在 Python 启动期把 ``sitecustomize`` 的临时目录探测警告写入
    stderr；若把 stderr 拼进 stdout，``--json`` 的纯净 JSON 会被追加噪声，
    ``json.loads`` 报 ``Extra data``。CI / 脚本消费的契约也只应认 stdout。
    """
    proc = subprocess.run(
        [sys.executable, SCRIPT, "--backend-root", backend,
         "--frontend-api", api_dir, *extra],
        capture_output=True, text=True,
    )
    return proc.returncode, proc.stdout, proc.stderr


class NormalizeTest(unittest.TestCase):

    def test_strip_query_and_fragment(self):
        self.assertEqual(mod.normalize("/API/User?a=1#frag"), "/api/user")

    def test_collapse_slashes_and_trailing_slash(self):
        self.assertEqual(mod.normalize("//api///user/"), "/api/user")

    def test_empty_path_becomes_root(self):
        self.assertEqual(mod.normalize(""), "/")
        self.assertEqual(mod.normalize("///"), "/")

    def test_lowercase(self):
        self.assertEqual(mod.normalize("/Api/USER/Detail"), "/api/user/detail")

    def test_format_placeholder_becomes_wildcard(self):
        self.assertEqual(mod.normalize("/api/%4$s/edit"), "/api/*/edit")


class ParamKeyTest(unittest.TestCase):

    def test_brace_and_dollar_placeholders(self):
        self.assertEqual(mod.param_key("GET", "/api/user/{id}"), "GET /api/user/*")
        self.assertEqual(mod.param_key("GET", "/api/user/${id}"), "GET /api/user/*")

    def test_uuid_like_segment(self):
        self.assertEqual(mod.param_key("put", "/api/user/550e8400-e29b-41d4-a716-446655440000"),
                         "PUT /api/user/*")

    def test_verb_uppercased_and_normal_segments_kept(self):
        self.assertEqual(mod.param_key("delete", "/API/User/Detail"),
                         "DELETE /api/user/detail")

    def test_short_numeric_segment_is_not_a_parameter(self):
        """纯数字短段不满足 8+ 位十六进制判据，按原样保留（既定行为）。"""
        self.assertEqual(mod.param_key("GET", "/api/user/42"), "GET /api/user/42")


class FirstQuotedTest(unittest.TestCase):

    def test_value_and_path_take_priority(self):
        self.assertEqual(mod.first_quoted('name = "x", value = "/api/a"'), "/api/a")
        self.assertEqual(mod.first_quoted('path = "/api/b", other = "z"'), "/api/b")

    def test_fallback_to_first_literal(self):
        self.assertEqual(mod.first_quoted('"/api/plain", method = RequestMethod.POST'),
                         "/api/plain")

    def test_no_quoted_literal_returns_empty(self):
        self.assertEqual(mod.first_quoted(""), "")
        self.assertEqual(mod.first_quoted("method = RequestMethod.GET"), "")


class MultiPathAliasTest(unittest.TestCase):
    """P2-1 前置：@RequestMapping 多路径别名 / 新旧路径双挂。"""

    def test_brace_array_returns_all_paths(self):
        self.assertEqual(mod.quoted_paths('{"/api/old", "/api/new"}'),
                         ["/api/old", "/api/new"])

    def test_named_array_and_non_path_attributes(self):
        self.assertEqual(
            mod.quoted_paths('value = {"/api/old", "/api/new"}, '
                             'produces = "application/json"'),
            ["/api/old", "/api/new"])

    def test_unknown_named_attribute_is_not_a_route(self):
        self.assertEqual(mod.quoted_paths('path = "/api/b", other = "z"'), ["/api/b"])

    def test_no_path_means_inherit_class_base(self):
        self.assertEqual(mod.quoted_paths(""), [""])
        self.assertEqual(mod.quoted_paths("method = RequestMethod.GET"), [""])

    def test_first_quoted_is_backward_compatible(self):
        self.assertEqual(mod.first_quoted('{"/api/old", "/api/new"}'), "/api/old")

    def test_scan_backend_builds_cartesian_product(self):
        java = ('@RequestMapping({"/api/old", "/api/new"})\n'
                'class C {\n'
                '  @GetMapping("/list") void list() {}\n'
                '  @PostMapping void create() {}\n'
                '}\n')
        backend, _ = make_fixture(java_text=java)
        keys = mod.scan_backend([os.path.join(backend, "scaffold-demo", "src", "main", "java")])
        self.assertEqual(keys, {
            "GET /api/old/list", "GET /api/new/list",
            "POST /api/old", "POST /api/new",
        })

    def test_method_level_request_mapping_honours_aliases(self):
        java = ('@RequestMapping({"/api/old", "/api/new"})\n'
                'class C {\n'
                '  @RequestMapping(value = "/sync", method = RequestMethod.PUT)\n'
                '  void sync() {}\n'
                '}\n')
        backend, _ = make_fixture(java_text=java)
        keys = mod.scan_backend([os.path.join(backend, "scaffold-demo", "src", "main", "java")])
        self.assertIn("PUT /api/old/sync", keys)
        self.assertIn("PUT /api/new/sync", keys)

    def test_cli_strict_accepts_frontend_on_either_alias(self):
        java = ('@RequestMapping({"/api/old", "/api/new"})\n'
                'class C { @GetMapping("/list") void list() {} }\n')
        js = ("request({ url: '/api/old/list', method: 'get' })\n"
              "request({ url: '/api/new/list', method: 'get' })\n")
        backend, api_dir = make_fixture(java_text=java, js_text=js)
        rc, out, _ = run(backend, api_dir, "--strict")
        self.assertEqual(rc, 0, out)
        self.assertIn("未发现前端→后端契约漂移", out)


class SourceRootsTest(unittest.TestCase):

    def test_only_scaffold_module_roots_are_discovered(self):
        backend, _ = make_fixture(extra_roots=("not-a-module/src/main/java",))
        roots = mod.backend_source_roots(backend)
        self.assertEqual(len(roots), 1)
        self.assertTrue(str(roots[0]).replace("\\", "/").endswith(
            "scaffold-demo/src/main/java"))

    def test_empty_root_returns_no_sources(self):
        base = tempfile.mkdtemp(prefix="apichk_empty_")
        self.assertEqual(mod.backend_source_roots(base), [])


class ScanBackendTest(unittest.TestCase):

    def test_class_base_and_verb_annotations(self):
        backend, _ = make_fixture()
        keys = mod.scan_backend([os.path.join(backend, "scaffold-demo", "src", "main", "java")])
        self.assertEqual(keys, {
            "GET /api/demo/list",
            "POST /api/demo",
            "PUT /api/demo/sync",
        })

    def test_method_level_bare_request_mapping(self):
        """方法级无参 @RequestMapping 等价 GET 落在类基路径。"""
        java = ('@RequestMapping("/api/plain")\n'
                'class Plain {\n'
                '    @RequestMapping\n'
                '    void root() {}\n'
                '}\n')
        backend, _ = make_fixture(java_text=java)
        keys = mod.scan_backend([os.path.join(backend, "scaffold-demo", "src", "main", "java")])
        self.assertEqual(keys, {"GET /api/plain"})

    def test_multiple_request_methods(self):
        java = ('@RequestMapping("/api/m")\n'
                'class M {\n'
                '    @RequestMapping(value = "/go", '
                'method = {RequestMethod.POST, RequestMethod.PUT})\n'
                '    void go() {}\n'
                '}\n')
        backend, _ = make_fixture(java_text=java)
        keys = mod.scan_backend([os.path.join(backend, "scaffold-demo", "src", "main", "java")])
        self.assertIn("POST /api/m/go", keys)
        self.assertIn("PUT /api/m/go", keys)

    def test_missing_source_root_is_skipped(self):
        self.assertEqual(mod.scan_backend(["/no/such/source/root"]), set())


class ScanFrontendTest(unittest.TestCase):

    def test_request_axios_and_dynamic_concat(self):
        _, api_dir = make_fixture()
        keys = mod.scan_frontend(api_dir)
        self.assertEqual(keys, {
            "GET /api/demo/list",
            "POST /api/demo/*",
            "DELETE /api/demo/42",
        })

    def test_missing_api_dir_returns_empty(self):
        self.assertEqual(mod.scan_frontend("/no/such/api/dir"), set())


class MainCliTest(unittest.TestCase):

    def test_json_three_way_classification(self):
        backend, api_dir = make_fixture(
            java_text='@RequestMapping("/api")\nclass C { @GetMapping("/ok") void ok() {} }\n',
            js_text=("request({ url: '/api/ok', method: 'get' })\n"
                     "request({ url: '/api/missing', method: 'get' })\n"))
        rc, out, _ = run(backend, api_dir, "--json")
        self.assertEqual(rc, 0, out)
        payload = json.loads(out)
        self.assertEqual(payload["matched"], ["GET /api/ok"])
        self.assertEqual(payload["backend_only"], [])
        self.assertEqual(payload["frontend_only"], ["GET /api/missing"])

    def test_strict_fails_on_frontend_only(self):
        backend, api_dir = make_fixture(
            java_text='@RequestMapping("/api")\nclass C { @GetMapping("/ok") void ok() {} }\n',
            js_text=("request({ url: '/api/ok', method: 'get' })\n"
                     "request({ url: '/api/missing', method: 'get' })\n"))
        rc, out, _ = run(backend, api_dir, "--strict")
        self.assertEqual(rc, 1, out)
        self.assertIn("[风险] 前端调用但后端无路由", out)

    def test_default_mode_is_informational(self):
        """默认（无 --strict）发现漂移仍返回 0，仅作风险提示。"""
        backend, api_dir = make_fixture(
            java_text='@RequestMapping("/api")\nclass C { @GetMapping("/ok") void ok() {} }\n',
            js_text="request({ url: '/api/missing', method: 'get' })\n")
        rc, out, _ = run(backend, api_dir)
        self.assertEqual(rc, 0, out)
        self.assertIn("[风险] 前端调用但后端无路由", out)

    def test_clean_contract_reports_no_drift(self):
        backend, api_dir = make_fixture(
            java_text='@RequestMapping("/api")\nclass C { @GetMapping("/ok") void ok() {} }\n',
            js_text="request({ url: '/api/ok', method: 'get' })\n")
        rc, out, _ = run(backend, api_dir, "--strict")
        self.assertEqual(rc, 0, out)
        self.assertIn("未发现前端→后端契约漂移", out)

    def test_backend_only_is_reported_as_reference(self):
        backend, api_dir = make_fixture(
            java_text='@RequestMapping("/api")\nclass C { @GetMapping("/ok") void ok() {} }\n',
            js_text="// no calls\n")
        rc, out, _ = run(backend, api_dir)
        self.assertEqual(rc, 0, out)
        self.assertIn("[参考] 后端暴露但前端未调用", out)


if __name__ == "__main__":
    unittest.main()
