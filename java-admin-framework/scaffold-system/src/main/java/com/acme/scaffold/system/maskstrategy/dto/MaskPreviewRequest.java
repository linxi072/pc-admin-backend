package com.acme.scaffold.system.maskstrategy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 脱敏效果预览请求。
 *
 * <p>前端在编辑弹窗中选定脱敏类型并填入示例值后，调用预览端点即时查看掩码结果。
 * 脱敏算法由后端 {@code SensitiveMasker.mask} 单一数据源完成，前端不做任何掩码逻辑，
 * 保证「配置页预览」与「运行时实际脱敏」两套口径完全一致。</p>
 */
public record MaskPreviewRequest(
        @NotBlank(message = "脱敏类型必填") String sensitiveType,
        @Size(max = 512, message = "示例值不超过 512 字符") String sampleValue) {
}
