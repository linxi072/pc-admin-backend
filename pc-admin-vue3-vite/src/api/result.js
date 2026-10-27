// 统一响应体 Result<T> 拆包工具（零依赖，供前端各 API 模块与测试共用）。
//
// 契约来源：../../java-admin-framework/scripts/api-contract.json
//   - 成功码兼容字符串 "0" 与数字 0（历史/跨语言兼容，详见 successCodes）。
//   - 失败为稳定的错误码（如 COMMON_404），并携带可选 data（如字段错误 Map）。
//
// 早期 http.js 仅判 `body.code === '0'`，会把数字 0 误判为业务错误；此处统一以
// isSuccessResult 兼容两种成功码，杜绝前后端契约不一致。

/**
 * 判断响应体是否为成功。兼容字符串 "0" 与数字 0。
 * @param {*} body 后端 Result<T> 响应体
 * @returns {boolean}
 */
export function isSuccessResult(body) {
  if (body == null || typeof body !== 'object') return false
  const c = body.code
  return c === '0' || c === 0
}

/**
 * 拆包结果错误：携带业务错误码与可选错误数据，供调用方 catch 后精细处理。
 */
export class ResultError extends Error {
  constructor(message, code, data) {
    super(message)
    this.name = 'ResultError'
    this.code = code
    this.data = data
  }
}

/**
 * 拆包 Result<T>：成功返回 data，失败抛出 ResultError。
 * @param {*} body 后端 Result<T> 响应体
 * @returns {*} body.data
 * @throws {ResultError}
 */
export function unwrapResult(body) {
  if (isSuccessResult(body)) return body.data
  const msg = body?.message || '请求失败'
  throw new ResultError(msg, body?.code, body?.data)
}
