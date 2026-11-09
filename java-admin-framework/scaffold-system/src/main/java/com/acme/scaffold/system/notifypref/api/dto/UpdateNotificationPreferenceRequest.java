package com.acme.scaffold.system.notifypref.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量更新我的通知订阅请求（个人中心「通知订阅」页保存）。
 *
 * <p>矩阵共 4 渠道 × 4 消息类型 = 16 格，单次提交即全量快照式的部分更新集合：
 * 仅提交发生变化的格子，未提交的格子保持原值（局部更新语义，与
 * {@code UpdatePreferenceRequest} 一致，避免整表重写）。</p>
 *
 * @param items 偏好项列表（1～16 项，超出即非法：矩阵总共只有 16 格）
 */
public record UpdateNotificationPreferenceRequest(
        @NotEmpty(message = "{validation.preference.items.notEmpty}")
        @Size(max = 16, message = "{validation.preference.items.size}")
        List<@Valid PreferenceItemRequest> items) {
}
