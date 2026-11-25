package com.acme.scaffold.im.support;

import com.acme.scaffold.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 站内消息业务规则纯函数层（无 Spring / 无 DB 依赖，可离线单测）。
 *
 * <p>把会话创建校验、成员集合构造、消息类型/预览归一、已读游标推进与会话排序口径从
 * Service 中抽出，避免同一规则在多处内联漂移；行为与原内联实现逐字等价。</p>
 */
public final class ImRules {

    /** 内容预览截断长度（实时推送气泡与通知摘要用）。 */
    public static final int PREVIEW_MAX = 50;
    /** 预览截断后的省略号。 */
    public static final String PREVIEW_ELLIPSIS = "…";

    private ImRules() {
    }

    /**
     * 会话创建参数校验（P2P 恰好 1 名对方成员、GROUP 至少 1 名、不得包含创建者本人）。
     * 校验顺序与错误信息与原服务内联实现保持一致。
     */
    public static void validateCreateConversation(String type, List<Long> peers, Long ownerId) {
        if (!ImStatus.TYPE_P2P.equals(type) && !ImStatus.TYPE_GROUP.equals(type)) {
            throw BusinessException.badRequest("会话类型必须为 P2P 或 GROUP");
        }
        if (peers == null || peers.isEmpty()) {
            throw BusinessException.badRequest("会话成员不能为空");
        }
        if (ImStatus.TYPE_P2P.equals(type) && peers.size() != 1) {
            throw BusinessException.badRequest("点对点会话仅支持 1 名对方成员");
        }
        if (ImStatus.TYPE_GROUP.equals(type) && peers.size() < 1) {
            throw BusinessException.badRequest("群组会话至少需要 1 名成员");
        }
        if (peers.contains(ownerId)) {
            throw BusinessException.badRequest("会话成员不能包含创建者本人");
        }
    }

    /** 会话成员集合：创建者作为 OWNER 排首位，其余按入参顺序去重。 */
    public static Set<Long> resolveMemberIds(Long ownerId, List<Long> peers) {
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(ownerId);
        userIds.addAll(peers);
        return userIds;
    }

    /** 消息类型归一：空值回退为文本消息。 */
    public static String normalizeMsgType(String raw) {
        return raw == null || raw.isBlank() ? ImStatus.MSG_TEXT : raw;
    }

    /** 实时推送预览：超长截断并附加省略号。 */
    public static String preview(String content, int max) {
        return content.length() > max ? content.substring(0, max) + PREVIEW_ELLIPSIS : content;
    }

    /** 已读游标是否应推进：无消息或游标已不落后时幂等跳过，只前进不回退。 */
    public static boolean shouldAdvanceReadCursor(Long current, Long maxId) {
        if (maxId == null) {
            return false;
        }
        return current == null || current < maxId;
    }

    /** 会话列表排序：最近消息时间倒序，无消息会话排末尾。 */
    public static int compareLastMessageAt(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }
}
