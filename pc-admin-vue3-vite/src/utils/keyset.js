// 游标（keyset）分页前端策略（纯函数，零依赖，可 node:test 直接验证）。
//
// 背景（功能梳理 F-11）：后端为 6 个大数据列表提供了 keyset 端点
// （audit-logs/keyset、users/page-keyset、messages/mine-keyset、messages/sent-keyset、
//  files/keyset、login-logs/keyset），返回 KeysetPageResult{size, records, nextCursor}，
// 不随偏移量退化、也不返回 total。同时 UserService / MessageService 对 OFFSET 深翻页
// 设了 5000 行护栏，超限即抛 VALIDATION_ERROR 并提示「请改用游标分页接口 /page-keyset」。
// 但前端此前 6 个端点零消费：用户深翻页只会撞到错误，而错误提示还指向不存在的 UI。
//
// 本模块把「何时切 keyset」「如何识别后端护栏错误」「游标页栈（上一页/下一页）」
// 抽成纯函数，供 OperationLogView.vue 等页面复用，并被离线单测覆盖。

/**
 * OFFSET 软阈值（行），与后端 MAX_USER_LIST_OFFSET / MAX_MESSAGE_OFFSET 对齐。
 * 超过即不再用 OFFSET 分页，避免线性退化与后端护栏报错。
 */
export const OFFSET_SOFT_LIMIT = 5000

/** 计算 OFFSET 分页的偏移量（行）；非法入参按 page=1 / size=10 兜底。 */
export function offsetOf(page, size) {
  const p = Number.isFinite(page) && page >= 1 ? Math.floor(page) : 1
  const s = Number.isFinite(size) && size >= 1 ? Math.floor(size) : 10
  return (p - 1) * s
}

/** 是否应该从 OFFSET 分页切换到 keyset 游标分页。 */
export function shouldUseKeyset(page, size, limit = OFFSET_SOFT_LIMIT) {
  return offsetOf(page, size) > limit
}

/**
 * 识别后端「偏移过大，请改用游标分页接口」类错误。
 * 兼容中文提示、英文提示与端点路径片段（page-keyset / mine-keyset / /keyset）。
 * @param {*} err Error 对象或消息字符串
 */
export function isOffsetLimitError(err) {
  const msg = String((err && err.message) || err || '')
  return /偏移过大|游标分页|page-keyset|mine-keyset|sent-keyset|\/keyset|offset.{0,20}(limit|large|exceed)/i.test(msg)
}

/**
 * 创建游标页栈。
 * - cursors[i] 为「第 i+1 页」请求时携带的 cursor，cursors[0] 恒为 null（首页不带游标）；
 * - index 为当前页下标（0-based）；
 * - nextCursor 为当前页响应返回的、供「下一页」请求使用的游标。
 */
export function createCursorStack() {
  return { cursors: [null], index: 0, nextCursor: '' }
}

/**
 * 页栈入参归一：非法/缺省入参回落为全新首页栈，且合法栈原样返回（保持对象身份，
 * 让「无下一页 / 已在首页」的原样返回语义不被破坏）。
 * 页面在异步竞态、旧缓存反序列化或异常分支下可能传入 undefined/null，
 * 此前 4 个 stack 函数会直接解引用抛错，导致整页加载中断。
 */
function normalizeStack(stack) {
  if (!stack || !Array.isArray(stack.cursors) || stack.cursors.length === 0) {
    return createCursorStack()
  }
  const validIndex = Number.isInteger(stack.index)
    && stack.index >= 0 && stack.index < stack.cursors.length
  const validNext = typeof stack.nextCursor === 'string'
  if (validIndex && validNext) return stack
  return {
    cursors: stack.cursors,
    index: validIndex ? stack.index : 0,
    nextCursor: validNext ? stack.nextCursor : ''
  }
}

/** 当前页请求应携带的 cursor（首页为 null）。 */
export function currentCursor(stack) {
  const s = normalizeStack(stack)
  return s.cursors[s.index] ?? null
}

/** 记录本页响应返回的下一页游标；空值表示已到末页。 */
export function setNextCursor(stack, nextCursor) {
  const s = normalizeStack(stack)
  return { ...s, nextCursor: nextCursor || '' }
}

/** 进入下一页：把 nextCursor 压栈作为新页起始游标；无下一页时原样返回。 */
export function goNext(stack) {
  const s = normalizeStack(stack)
  if (!s.nextCursor) return s
  const cursors = s.cursors.slice(0, s.index + 1)
  cursors.push(s.nextCursor)
  return { cursors, index: s.index + 1, nextCursor: '' }
}

/** 回到上一页：仅回退 index，cursors 保留以便再次前进。 */
export function goPrev(stack) {
  const s = normalizeStack(stack)
  if (s.index <= 0) return s
  return { ...s, index: s.index - 1, nextCursor: '' }
}

export function canGoNext(stack) {
  return Boolean(normalizeStack(stack).nextCursor)
}

export function canGoPrev(stack) {
  return normalizeStack(stack).index > 0
}

/** 当前人类可读页码（1-based）。 */
export function pageNumber(stack) {
  return normalizeStack(stack).index + 1
}
