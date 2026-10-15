package com.acme.scaffold.kafka.service;

import com.acme.scaffold.kafka.domain.OrderedRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认有序记录处理器：日志落盘。
 *
 * <p>脚手架占位实现——业务方应实现 {@link OrderedRecordHandler} 并注册为 Spring Bean
 * （容器按类型注入唯一实例；存在多个实现时以 {@code @Primary} 或本类被排除的方式二选一）。
 */
@Slf4j
@Component
public class LoggingOrderedRecordHandler implements OrderedRecordHandler {

    @Override
    public void handle(OrderedRecord record) {
        log.info("消费有序记录: topic={}, partition={}, offset={}, key={}, version={}, payload={}",
                record.topic(), record.partition(), record.offset(),
                record.key(), record.version(), record.payload());
    }

    @Override
    public String supportedTopic() {
        return null;
    }
}
