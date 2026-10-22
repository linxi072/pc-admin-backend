package com.acme.scaffold.kafka.gateway.bus;

import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.domain.Tier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.concurrent.ExecutionException;

/**
 * 真实 Kafka 发送器（包裹 {@link KafkaProducer}）。
 *
 * <p>同步 get 结算（网关双写对延迟不敏感，且需确切的 accepted/rejected 语义驱动计数）。
 * 故障统一转为 {@link ProduceResult#rejected} 而非抛出，保证热路径稳定（§2.3）。
 */
public final class KafkaRecordSender implements RecordSender {

    private final KafkaProducer<byte[], byte[]> producer;

    public KafkaRecordSender(KafkaProducer<byte[], byte[]> producer) {
        this.producer = producer;
    }

    @Override
    public ProduceResult send(Tier tier, String topic, int partition, byte[] key, byte[] value) {
        try {
            RecordMetadata md = producer.send(new ProducerRecord<>(topic, partition, key, value)).get();
            return ProduceResult.confirmed(topic, md.partition(), md.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProduceResult.rejected(topic, "INTERRUPTED", e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return ProduceResult.rejected(topic, "SEND_FAILED", cause == null ? e.getMessage() : cause.getMessage());
        }
    }

    @Override
    public void flush(long timeoutMs) {
        producer.flush();
    }

    @Override
    public void close() {
        producer.close();
    }
}
