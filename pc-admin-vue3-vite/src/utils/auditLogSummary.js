/**
 * 操作日志分页与统计摘要的并行加载编排。
 *
 * <p>OFFSET 分页遇到后端「偏移过大，请改用游标分页」护栏时会自动降级到 keyset 端点。
 * 降级前摘要请求已经随首屏并行发出并完成结算；若直接丢弃，再在 keyset 模式重复请求，
 * 不仅多一次查询，还可能在第二次摘要失败时把首次成功结果清掉。</p>
 *
 * <p>这里把「预取摘要是否可复用」抽成纯函数：预取结果为 fulfilled 时直接复用且不再调用
 * fetchSummary；其余情况（无预取或预取失败）保持在游标模式下重试一次的原有语义。</p>
 *
 * @param {Promise} pagePromise 当前模式下的分页请求
 * @param {() => Promise} fetchSummary 摘要请求工厂，仅在需要时调用
 * @param {PromiseSettledResult|null} prefetchedSummaryResult 已结算的首屏摘要结果
 * @returns {Promise<{pageResult: PromiseSettledResult, summaryResult: PromiseSettledResult}>}
 */
export async function settlePageWithSummary(pagePromise, fetchSummary, prefetchedSummaryResult = null) {
  const reuse = prefetchedSummaryResult?.status === 'fulfilled'
  const tasks = [Promise.resolve(pagePromise)]
  if (!reuse) {
    tasks.push(Promise.resolve().then(fetchSummary))
  }
  const [pageResult, summaryResult] = await Promise.allSettled(tasks)
  return {
    pageResult,
    summaryResult: reuse ? prefetchedSummaryResult : summaryResult
  }
}
