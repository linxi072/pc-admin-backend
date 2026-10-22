package com.acme.scaffold.common.messaging.domain;

/**
 * 帧消息类型（对应设计稿 §3.1 连接生命周期与 §3.6 重连续传）。
 *
 * <p>热路径仅允许 {@link #DATA}（二进制帧）；控制帧（PING/PONG/ACK/CLOSE/RESUME）
 * 带宽占比极低，但语义必须显式，便于客户端 SDK 与网关对齐。
 */
public enum MsgType {

    /** 业务数据帧（上行不带 seq，由网关分配；下行必带 seq）。 */
    DATA(0x01),

    /** 应用层确认帧，携带已确认的 seq（关键消息必回，见 §3.3）。 */
    ACK(0x02),

    /** 服务端心跳探测（30s 周期）。 */
    PING(0x03),

    /** 客户端心跳响应。 */
    PONG(0x04),

    /** 服务端主动关闭（携带关闭码：4028 限流 / 4029 慢消费者 / 1001 优雅退出）。 */
    CLOSE(0x05),

    /** 重连续传：客户端携带 last_seq，服务端据此补洞（§3.6）。 */
    RESUME(0x06);

    private final int code;

    MsgType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * 按线编码还原类型。
     *
     * @throws IllegalArgumentException 编码越界或未定义（协议防御：拒绝未知类型而非猜测）
     */
    public static MsgType fromCode(int code) {
        for (MsgType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知消息类型编码: " + code);
    }
}
