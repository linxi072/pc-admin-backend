package com.acme.scaffold.cms.article.domain.rule;

import com.acme.scaffold.cms.support.CmsStatus;
import java.time.LocalDateTime;

/**
 * 内容发布态流转规则（C1，纯函数）：把散落在 Service 私有方法的状态判定收口为单一口径。
 *
 * <p>覆盖：状态规范化、转入发布判定、幂等判定、发布时间/下线时间合并。
 * 所有方法无副作用、无 Spring 依赖，便于离线单测；Service 委托本类后行为保持不变。</p>
 */
public final class CmsStatusRules {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String OFFLINE = "OFFLINE";

    private CmsStatusRules() {
    }

    /** 请求状态缺省时归一为 DRAFT。 */
    public static String normalize(String status) {
        return status == null || status.isBlank() ? DRAFT : status.trim().toUpperCase();
    }

    /** 是否为合法发布态。 */
    public static boolean isValid(String status) {
        if (status == null) {
            return false;
        }
        for (CmsStatus s : CmsStatus.values()) {
            if (s.name().equals(status)) {
                return true;
            }
        }
        return false;
    }

    /** 由旧态+目标态判定是否「新转入发布」（用于补登 publishedAt 与广播一次事件）。 */
    public static boolean becamePublished(String oldStatus, String newStatus) {
        return PUBLISHED.equals(newStatus) && !PUBLISHED.equals(oldStatus);
    }

    /** 是否处于发布态（与 CmsStatus.isPublishable 语义一致）。 */
    public static boolean isPublishable(String status) {
        return PUBLISHED.equals(status);
    }

    /** 发布是否幂等跳过（已是 PUBLISHED）。 */
    public static boolean isPublishNoop(String currentStatus) {
        return isPublishable(currentStatus);
    }

    /** 下线是否幂等跳过（已是 OFFLINE）。 */
    public static boolean isOfflineNoop(String currentStatus) {
        return OFFLINE.equals(currentStatus);
    }

    /** 计算发布时间：转入发布时用 now，否则保留原值（幂等不刷新）。 */
    public static LocalDateTime resolvePublishedAt(boolean becamePublished,
                                                    LocalDateTime existing,
                                                    LocalDateTime now) {
        return becamePublished ? now : existing;
    }

    /** 计算下线时间：下线用 now；转出下线态（含重发）返回 null 触发显式置空。 */
    public static LocalDateTime resolveOfflineAt(boolean goOffline,
                                                  LocalDateTime existing,
                                                  LocalDateTime now) {
        return goOffline ? now : null;
    }

    /** 目标态非 OFFLINE 时是否需清空下线时间（重发/转草稿均需清）。 */
    public static boolean shouldClearOfflineAt(String newStatus) {
        return newStatus != null && !OFFLINE.equals(newStatus);
    }
}
