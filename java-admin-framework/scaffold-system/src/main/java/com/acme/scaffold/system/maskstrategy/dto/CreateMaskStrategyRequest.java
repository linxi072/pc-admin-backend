package com.acme.scaffold.system.maskstrategy.dto;

import com.acme.scaffold.system.maskstrategy.rules.MaskStrategyRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建脱敏策略请求。
 *
 * <p>结构校验由 {@link MaskStrategyRules#validate} 兜底（编码/名称/字段键长度、脱敏类型/作用范围合法性），
 * 此处以 Bean Validation 做入参层面的快速失败（非空与长度上限）。</p>
 */
public record CreateMaskStrategyRequest(
        @NotBlank @Size(max = 64, message = "策略编码不超过 64 字符") String strategyCode,
        @NotBlank @Size(max = 128, message = "策略名称不超过 128 字符") String strategyName,
        @NotBlank @Size(max = 128, message = "字段键不超过 128 字符") String fieldKey,
        @NotBlank(message = "脱敏类型必填") String sensitiveType,
        String applyScope,
        @Size(max = 512, message = "豁免角色列表不超过 512 字符") String exemptRoles,
        Integer priority,
        Boolean enabled,
        @Size(max = 255, message = "备注不超过 255 字符") String remark) {
}
