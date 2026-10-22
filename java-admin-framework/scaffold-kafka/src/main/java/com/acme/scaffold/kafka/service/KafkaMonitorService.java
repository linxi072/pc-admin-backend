package com.acme.scaffold.kafka.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.kafka.domain.PoisonMessage;
import com.acme.scaffold.kafka.dto.KafkaConsumerStatusView;
import com.acme.scaffold.kafka.dto.KafkaPoisonMessageView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Kafka 有序消费运维服务：聚合 {@link OrderedKafkaConsumer} 的运行指标与死信存储，向 REST 层提供只读视图与补偿操作。
 *
 * <p>消费容器由 {@code kafka.ordered-consumer.enabled} 门禁控制，未启用时 {@link OrderedKafkaConsumer}
 * Bean 不注册。本服务通过 {@link ObjectProvider#getIfAvailable()} 惰性获取，缺失时所有只读接口返回「未启用」视图、
 * 补偿操作返回 false，避免对未装配环境产生硬依赖。</p>
 */
@Service
@RequiredArgsConstructor
public class KafkaMonitorService {

    private final ObjectProvider<OrderedKafkaConsumer> consumerProvider;

    /**
     * 采集消费容器运行态与累计指标。
     *
     * @return 状态视图；未启用消费容器时 {@code enabled=false} 且计数归零
     */
    public KafkaConsumerStatusView status() {
        OrderedKafkaConsumer consumer = consumerProvider.getIfAvailable();
        if (consumer == null) {
            return new KafkaConsumerStatusView(false, false, 0L, 0L, 0L, 0, 0L, 0);
        }
        return new KafkaConsumerStatusView(
                true,
                consumer.isRunning(),
                consumer.handledCount(),
                consumer.staleCount(),
                consumer.poisonCount(),
                consumer.poisonStoreSize(),
                consumer.commitFailureCount(),
                consumer.processedVersionTableSize());
    }

    /**
     * 分页查询死信（毒消息）列表，支持按 message key 精确过滤。结果按判定时间倒序（最新在前）。
     *
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @param keyFilter 可选 key 过滤（为空或空白表示不过滤）
     * @return 分页结果（总条数基于过滤后全集）
     */
    public PageResult<KafkaPoisonMessageView> listPoison(int page, int size, String keyFilter) {
        OrderedKafkaConsumer consumer = consumerProvider.getIfAvailable();
        List<KafkaPoisonMessageView> all = consumer == null
                ? List.of()
                : consumer.poisonEntries().stream()
                        .filter(p -> keyFilter == null || keyFilter.isBlank() || keyFilter.equals(p.key()))
                        .map(this::toView)
                        .sorted(Comparator.comparingLong(KafkaPoisonMessageView::detectedAtEpochMillis).reversed())
                        .toList();

        long total = all.size();
        int from = (int) Math.max(0L, (long) (page - 1) * size);
        List<KafkaPoisonMessageView> slice = from >= all.size()
                ? List.of()
                : all.subList(from, Math.min(all.size(), from + size));
        return PageResult.of(page, size, total, slice);
    }

    /**
     * 重试某条毒消息：清除其失败计数与版本表占位，允许上游重投后重新处理。
     *
     * @param id 毒消息标识（不可为空）
     * @return 是否找到并清除（未启用消费容器或标识不存在返回 false）
     */
    public boolean retry(String id) {
        requireNonBlankId(id);
        OrderedKafkaConsumer consumer = consumerProvider.getIfAvailable();
        return consumer != null && consumer.retryPoison(id);
    }

    /**
     * 丢弃某条毒消息：从死信存储移除，视为已人工确认、无需再补偿。
     *
     * @param id 毒消息标识（不可为空）
     * @return 是否找到并移除
     */
    public boolean discard(String id) {
        requireNonBlankId(id);
        OrderedKafkaConsumer consumer = consumerProvider.getIfAvailable();
        return consumer != null && consumer.discardPoison(id);
    }

    private void requireNonBlankId(String id) {
        if (id == null || id.isBlank()) {
            throw BusinessException.badRequest("毒消息 ID 不能为空");
        }
    }

    private KafkaPoisonMessageView toView(PoisonMessage p) {
        return new KafkaPoisonMessageView(
                p.id(), p.topic(), p.partition(), p.offset(), p.key(),
                p.payloadPreview(), p.attempts(), p.lastError(), p.detectedAtEpochMillis());
    }
}
