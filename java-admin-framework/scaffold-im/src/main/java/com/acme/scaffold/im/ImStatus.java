package com.acme.scaffold.im;

/**
 * 站内消息领域常量（ROADMAP R6）。
 *
 * <p>会话/消息/成员的状态与类型以 VARCHAR 承载，由应用层校验，便于后续扩展（如 SYSTEM 系统消息、FILE 文件消息）。
 * 集中常量避免魔法字符串散落各 Service。</p>
 */
public final class ImStatus {

    /** 会话类型：点对点。 */
    public static final String TYPE_P2P = "P2P";
    /** 会话类型：群组。 */
    public static final String TYPE_GROUP = "GROUP";

    /** 成员角色：群主。 */
    public static final String ROLE_OWNER = "OWNER";
    /** 成员角色：管理员。 */
    public static final String ROLE_ADMIN = "ADMIN";
    /** 成员角色：普通成员。 */
    public static final String ROLE_MEMBER = "MEMBER";

    /** 会话/成员可用状态。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 成员已退出（逻辑删除语义的状态标记）。 */
    public static final String STATUS_LEFT = "LEFT";

    /** 消息类型：文本。 */
    public static final String MSG_TEXT = "TEXT";
    /** 消息类型：图片。 */
    public static final String MSG_IMAGE = "IMAGE";
    /** 消息类型：文件。 */
    public static final String MSG_FILE = "FILE";
    /** 消息类型：系统提示。 */
    public static final String MSG_SYSTEM = "SYSTEM";

    /** 消息状态：已发送。 */
    public static final String MSG_STATUS_SENT = "SENT";
    /** 消息状态：已撤回。 */
    public static final String MSG_STATUS_RECALLED = "RECALLED";

    private ImStatus() {
    }
}
