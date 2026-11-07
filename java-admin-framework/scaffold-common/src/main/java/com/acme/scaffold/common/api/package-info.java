/**
 * 对外交互契约层：统一响应体、分页入参与结果、游标编解码、深翻页护栏。
 *
 * <p>唯一事实来源是 {@code scripts/api-contract.json}：响应包络为
 * {@code {code, message, data, traceId, timestamp}}，成功码为字符串 {@code "0"}。
 * 若依的 AjaxResult / TableDataInfo / BaseController 与本层语义重叠，但改名或替换会
 * 同时破坏后端 ApiContractConsistencyTest 与前端 result.test.js 的断言，故保留本层命名，
 * 差异只在 {@code docs/RUOYI-ALIGNMENT.md} 说明。</p>
 *
 * <p>本包内的纯函数规则（PagingRules 等）不得抛业务异常、不触 DB，异常出口留在各 Service，
 * 以维持既有错误码与逐字提示文案。</p>
 */
package com.acme.scaffold.common.api;
