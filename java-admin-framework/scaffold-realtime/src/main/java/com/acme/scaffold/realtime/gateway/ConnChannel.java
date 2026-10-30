package com.acme.scaffold.realtime.gateway;

/**
 * 连接通道端口（传输层绑定的接缝）。
 *
 * <p>核心逻辑（{@link GatewayNode}）只依赖此端口；Spring WebSocket 实现
 * （{@code SpringConnChannel}）负责真实 socket 写入与 in-flight 计量。
 * 测试用手写假通道，无需网络。
 */
public interface ConnChannel {

    /** 连接 ID（本节点唯一）。 */
    String connId();

    /** 用户 ID（握手成功后非空）。 */
    String userId();

    /** 写入下行帧（二进制字节；实现方负责帧类型包装）。 */
    void writeFrame(byte[] frame);

    /** 以指定 WebSocket 关闭码关闭连接。 */
    void close(int code, String reason);

    boolean isActive();

    /** 待发送队列深度（慢消费者判定的输入）。 */
    int inflight();

    /** 累计丢弃最旧帧数（L1 处置的执行结果回读）。 */
    long droppedCount();

    /** 标记一次丢弃最旧（实现方在队列溢出时调用）。 */
    void markDroppedOldest();

    /** 最近一次关闭码；连接从未关闭时返回 -1（测试与运维观测用）。 */
    int closeCode();
}
