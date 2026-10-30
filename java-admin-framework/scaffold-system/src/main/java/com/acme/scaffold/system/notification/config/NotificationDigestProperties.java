package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知聚合摘要配置（M17）：{@code app.notifications.digest.*}。
 *
 * <ul>
 *   <li>{@code enabled}：是否启用聚合（默认 false，缺省关闭——聚合改变投递时机与
 *       报文形态，须显式开启）；关闭时 dispatch 挂点直通，行为与 M16 及以前一致；</li>
 *   <li>{@code msg-types}：命中聚合的消息类型白名单（默认 {@code [NOTICE]}，
 *       大小写不敏感；摘要消息自身的 {@code DIGEST} 刻意不在其内，防递归挂起）；</li>
 *   <li>{@code threshold}：合并阈值（默认 5——单用户单 (渠道, 类型) 聚合达该条数
 *       立即刷新投递，先到先投防止摘要无限囤积）；</li>
 *   <li>{@code max-items}：摘要正文保留的条目上限（默认 50——超出丢弃最旧，
 *       全量条数由 item_count 在标题与尾注补偿表达）；</li>
 *   <li>{@code max-wait-minutes}：最大等待时长（默认 60 分钟——低流量用户的兜底：
 *       由定时任务扫描 last_at 超期的聚合行强制刷新，防「永远差一条」）；</li>
 *   <li>{@code scan-interval-ms}：兜底扫描周期（默认 60 秒）；</li>
 *   <li>{@code title-template} / {@code content-template}：摘要标题/正文模板，
 *       支持 {@code ${count}}（全量条数）、{@code ${items}}（条目列表）、
 *       {@code ${date}}（统计截止时刻），复用 M10 {@code ${var}} 渲染机制。</li>
 * </ul>
 *
 * <p><b>聚合范围</b>：仅聚合有明确 userId 的定向消息（IN_APP/WEBSOCKET 的
 * receiverIds、EMAIL/SMS 反查 sys_user 全部命中的地址——全映射才聚合，
 * 防部分投递丢消息）；广播与 WEBHOOK 无个人投递箱语义，不聚合
 * （与 M13/M14 广播不过滤口径一致，属文档化限制）。</p>
 */
@ConfigurationProperties(prefix = "app.notifications.digest")
public class NotificationDigestProperties {

    private boolean enabled = false;
    private List<String> msgTypes = new ArrayList<>(List.of("NOTICE"));
    private int threshold = 5;
    private int maxItems = 50;
    private long maxWaitMinutes = 60;
    private long scanIntervalMs = 60000;
    private String titleTemplate = "您有 ${count} 条新通知";
    private String contentTemplate = "以下是最近的通知摘要：\n${items}\n（统计截至 ${date}）";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getMsgTypes() {
        return msgTypes;
    }

    public void setMsgTypes(List<String> msgTypes) {
        this.msgTypes = msgTypes == null ? new ArrayList<>() : msgTypes;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public long getMaxWaitMinutes() {
        return maxWaitMinutes;
    }

    public void setMaxWaitMinutes(long maxWaitMinutes) {
        this.maxWaitMinutes = maxWaitMinutes;
    }

    public long getScanIntervalMs() {
        return scanIntervalMs;
    }

    public void setScanIntervalMs(long scanIntervalMs) {
        this.scanIntervalMs = scanIntervalMs;
    }

    public String getTitleTemplate() {
        return titleTemplate;
    }

    public void setTitleTemplate(String titleTemplate) {
        this.titleTemplate = titleTemplate;
    }

    public String getContentTemplate() {
        return contentTemplate;
    }

    public void setContentTemplate(String contentTemplate) {
        this.contentTemplate = contentTemplate;
    }
}
