package com.acme.scaffold.system.notifydigest.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知聚合摘要实体（对应 {@code sys_notification_digest}，DDL 见 V45__notification_digest.sql）。
 *
 * <p><b>待投递缓冲，非档案</b>：聚合行只承载「尚未合并投递」的消息；摘要一旦投递
 * （无论成功/跳过/失败）即物理删行，故本表无 {@code status} / {@code deleted} 列，
 * 审计由通知发送记录（{@code sys_notification_record}，M12）承担——摘要消息本身
 * 也走 {@code NotificationService.dispatch} 全链路留痕。</p>
 *
 * <p><b>合并语义</b>：同一用户同一 (渠道, 消息类型) 至多一行（唯一键
 * {@code uk_user_channel_type}），新消息到达即合并进现存行：{@code item_count}
 * 累加（计全量）、{@code itemsJson} 保留最近 N 条单行摘要（渲染用）、
 * {@code lastAt} 前推（兜底扫描的时间基准）。{@code firstAt} 仅在插入时落值，
 * 标记本批次聚合的起点。</p>
 *
 * <p><b>时间口径</b>：{@code firstAt} / {@code lastAt} 为 DATETIME(3)，服务端显式赋值
 * （{@code JooqWriters.insert} 只自动填 created_at / updated_at，不填业务时间列）。</p>
 */
@Data
public class SysNotificationDigestDO {

    private Long id;

    private Long tenantId = 0L;

    /** 聚合归属用户（按接收者维度，与 M13/M14 同口径；广播无个人投递箱，不落行）。 */
    private Long userId;

    /** 聚合渠道（存 {@code Channel} 枚举大写：IN_APP / EMAIL / SMS / WEBSOCKET）。 */
    private String channel;

    /** 被聚合的消息类型（归一后白名单值，如 NOTICE；摘要消息自身的 DIGEST 不落行）。 */
    private String msgType;

    /** 已聚合消息全量条数（超出 itemsJson 保留条数后继续累加，用于标题「您有 N 条新通知」）。 */
    private Integer itemCount;

    /** 单行摘要数组的 JSON 序列化（标题优先、正文回退、截断；保留最近 maxItems 条）。 */
    private String itemsJson;

    /** 本批次聚合起点（首条消息到达时间，插入时赋值后不再更新）。 */
    private LocalDateTime firstAt;

    /** 最近一条消息到达时间（兜底扫描基准：last_at 超 max-wait-minutes 即强制刷新）。 */
    private LocalDateTime lastAt;
}
