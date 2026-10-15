package com.acme.scaffold.kafka.service;

import com.acme.scaffold.kafka.domain.OrderedRecord;

/**
 * 有序记录业务处理器（SPI）。
 *
 * <p>由业务方实现并注册为 Spring Bean，框架默认提供日志型实现
 * （{@code LoggingOrderedRecordHandler}）。实现须幂等——at-least-once 投递下
 * 同一条记录可能被重复送达（版本去重已挡掉大部分，毒消息重试仍会复现）。
 *
 * <p>线程约定：容器以单线程串行调用 {@link #handle}，实现无需自行加锁；
 * 但不得长时间阻塞（会拖垮 poll 循环触发 rebalance）。
 */
public interface OrderedRecordHandler {

    /**
     * 处理一条记录（已被版本规则判定为「应处理」）。
     *
     * @param record 有序记录（version 可能为 null）
     * @throws Exception 处理失败时抛出——容器将放弃本批提交使下轮重投
     */
    void handle(OrderedRecord record);

    /**
     * 处理器支持的主题（框架据此做简单校验；null/空表示不校验）。
     *
     * @return 主题名或 null
     */
    default String supportedTopic() {
        return null;
    }
}
