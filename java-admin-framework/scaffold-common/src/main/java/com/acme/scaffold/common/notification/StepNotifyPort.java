package com.acme.scaffold.common.notification;

import java.util.List;

/**
 * 工作流步骤通知出口（防腐层 Port）。
 *
 * <p>工作流模块（{@code scaffold-workflow}）在 CC / SERVICE 节点需向用户发送站内信抄送，
 * 但不应直连 {@code system.service.MessageService}——跨模块直连实现违反模块边界
 * （见 refactoring-plan B2）。改为依赖本 Port 契约，由 system 模块提供适配器委托
 * {@code MessageService#sendCcNotification} 落地。
 *
 * <p>本接口刻意保持极简、聚焦「显式接收人列表」的抄送语义，不与渠道化
 * {@link NotificationPort}（按 {@code Channel} 路由的通用通知契约）混淆。
 */
public interface StepNotifyPort {

    /**
     * 向一组用户发送抄送 / 步骤完成站内信。
     *
     * @param userIds 接收人用户 ID 列表（空列表或 null 应视为无操作，返回 0）
     * @param title   站内信标题
     * @param content 站内信正文
     * @return 实际投递的接收人数量
     */
    int sendCc(List<Long> userIds, String title, String content);
}
