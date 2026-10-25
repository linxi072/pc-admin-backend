package com.acme.scaffold.system.maskstrategy.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新脱敏策略请求（部分更新：仅传入的非空字段参与）。
 *
 * <p>所有字段可选，Service 层以「合并后的完整值」做结构校验，保证部分更新后仍满足策略契约；
 * 编码变更时再做唯一性校验。</p>
 */
public record UpdateMaskStrategyRequest(
        String strategyCode,
        String strategyName,
        String fieldKey,
        String sensitiveType,
        String applyScope,
        @Size(max = 512, message = "豁免角色列表不超过 512 字符") String exemptRoles,
        Integer priority,
        Boolean enabled,
        String remark) {
}
