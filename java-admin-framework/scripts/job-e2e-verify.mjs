/**
 * 定时任务模块端到端验证（修复雪花 ID 精度丢失后）。
 * 流程：登录(验证码数学题) → 元信息 → 创建 → 列表 → 启动 → 详情 → 日志 → 暂停 → 删除。
 * 断言：创建/列表返回的 id 必须为字符串；后续按该 id 的操作全部成功（不再出现「定时任务不存在」）。
 */
const BASE = 'http://localhost:8080'

const fail = (msg) => { console.error('FAIL: ' + msg); process.exitCode = 1 }
const ok = (msg) => console.log('PASS: ' + msg)

async function main() {
  // 1. 验证码（数学题，question 对外暴露）
  const cap = await (await fetch(BASE + '/api/auth/captcha')).json()
  const capData = cap?.data
  if (!capData?.token || !capData?.question) { fail('验证码获取失败: ' + JSON.stringify(cap)); return }
  const m = capData.question.match(/(\d+)\s*\+\s*(\d+)/)
  if (!m) { fail('无法解析验证码题目: ' + capData.question); return }
  const answer = String(Number(m[1]) + Number(m[2]))

  // 2. 登录
  const loginResp = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123', captchaToken: capData.token, captchaAnswer: answer })
  })
  const loginBody = await loginResp.json()
  const token = loginBody?.data?.token || loginBody?.data?.accessToken
  if (!token) { fail('登录失败: ' + JSON.stringify(loginBody)); return }
  ok('登录成功')

  const auth = { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }
  const j = async (r) => ({ status: r.status, body: await r.json().catch(() => null) })

  // 3. 元信息
  const meta = await j(await fetch(BASE + '/api/jobs/meta', { headers: auth }))
  if (meta.status !== 200) fail('meta 状态 ' + meta.status); else ok('meta: ' + JSON.stringify(meta.body?.data))

  // 4. 创建（CRON，默认暂停）
  const createPayload = {
    jobName: 'E2E验证任务-' + Date.now(),
    jobGroup: 'e2e',
    taskKey: 'demo-echo',
    triggerType: 'CRON',
    cronExpression: '0 0 3 * * ?',
    jobParams: '{"msg":"hello"}',
    remark: 'e2e verify'
  }
  const created = await j(await fetch(BASE + '/api/jobs', { method: 'POST', headers: auth, body: JSON.stringify(createPayload) }))
  if (created.status !== 200) { fail('创建失败: ' + JSON.stringify(created.body)); return }
  const createdId = created.body?.data
  if (typeof createdId !== 'string') { fail('创建响应 id 不是字符串(精度丢失风险): ' + JSON.stringify(createdId)); return }
  ok('创建成功，id=' + createdId + '（字符串，长度 ' + createdId.length + '）')
  if (!/^\d{17,19}$/.test(createdId)) fail('id 形态异常: ' + createdId)

  // 5. 列表：id 必须为字符串
  const list = await j(await fetch(BASE + '/api/jobs?jobName=' + encodeURIComponent(createPayload.jobName), { headers: auth }))
  const row = list.body?.data?.records?.find((x) => String(x.id) === createdId)
  if (!row) { fail('列表中找不到新建任务: ' + JSON.stringify(list.body)); return }
  if (typeof row.id !== 'string') { fail('列表 id 不是字符串: ' + JSON.stringify(row.id)); return }
  ok('列表命中，id=' + row.id + '（字符串），status=' + row.status)

  // 6. 启动（核心回归点：修复前此处报「定时任务不存在」）
  const start = await j(await fetch(BASE + '/api/jobs/' + createdId + '/start', { method: 'POST', headers: auth }))
  if (start.status !== 200) fail('启动失败: ' + JSON.stringify(start.body)); else ok('启动成功')

  // 7. 详情
  const detail = await j(await fetch(BASE + '/api/jobs/' + createdId, { headers: auth }))
  if (detail.status !== 200 || String(detail.body?.data?.id) !== createdId) fail('详情失败: ' + JSON.stringify(detail.body)); else ok('详情成功，status=' + detail.body.data.status)

  // 8. 日志
  const logs = await j(await fetch(BASE + '/api/jobs/' + createdId + '/logs?page=1&size=5', { headers: auth }))
  if (logs.status !== 200) fail('日志查询失败: ' + JSON.stringify(logs.body)); else ok('日志查询成功（total=' + (logs.body?.data?.total ?? 0) + '）')

  // 9. 暂停
  const pause = await j(await fetch(BASE + '/api/jobs/' + createdId + '/pause', { method: 'POST', headers: auth }))
  if (pause.status !== 200) fail('暂停失败: ' + JSON.stringify(pause.body)); else ok('暂停成功')

  // 10. 删除
  const del = await j(await fetch(BASE + '/api/jobs/' + createdId, { method: 'DELETE', headers: auth }))
  if (del.status !== 200) fail('删除失败: ' + JSON.stringify(del.body)); else ok('删除成功')

  // 11. 删除后按 id 再操作应 404（证明 id 语义精确）
  const after = await j(await fetch(BASE + '/api/jobs/' + createdId + '/start', { method: 'POST', headers: auth }))
  if (after.status === 200) fail('删除后启动仍成功，不符合预期'); else ok('删除后启动返回 ' + after.status + '（符合预期）')

  console.log(process.exitCode ? '\n== 存在失败项 ==' : '\n== 全部通过 ==')
}

main().catch((e) => { console.error('脚本异常: ' + e.message); process.exitCode = 1 })
