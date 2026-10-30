package com.acme.scaffold.kafka.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.kafka.domain.PoisonMessage;
import com.acme.scaffold.kafka.dto.KafkaPoisonMessageView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link KafkaMonitorService} 单元验证：消费容器缺失（未启用）时的降级、输入校验（空 ID）、
 * 以及补偿操作对 {@link OrderedKafkaConsumer} 的委托。
 */
@DisplayName("KafkaMonitorService 单元验证")
class KafkaMonitorServiceTest {

    private final ObjectProvider<OrderedKafkaConsumer> provider = mock(ObjectProvider.class);
    private final KafkaMonitorService service = new KafkaMonitorService(provider);

    @Test
    @DisplayName("未启用消费容器时 status.enabled=false 且补偿返回 false")
    void disabled_returnsSafeDefaults() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertFalse(service.status().enabled());
        assertFalse(service.retry("any"));
        assertFalse(service.discard("any"));
        assertEquals(0L, service.listPoison(1, 10, null).total());
    }

    @Test
    @DisplayName("空 ID 重试/丢弃触发输入校验（BusinessException / VALIDATION_ERROR）")
    void blankId_throwsValidation() {
        when(provider.getIfAvailable()).thenReturn(mock(OrderedKafkaConsumer.class));

        BusinessException retryEx = assertThrows(BusinessException.class, () -> service.retry(" "));
        BusinessException discardEx = assertThrows(BusinessException.class, () -> service.discard(""));
        assertEquals("COMMON_001", retryEx.getErrorCode().code());
        assertEquals("COMMON_001", discardEx.getErrorCode().code());
    }

    @Test
    @DisplayName("重试成功委托到消费容器的 retryPoison")
    void retry_delegatesToConsumer() {
        OrderedKafkaConsumer consumer = mock(OrderedKafkaConsumer.class);
        when(provider.getIfAvailable()).thenReturn(consumer);
        when(consumer.retryPoison("pid-1")).thenReturn(true);

        assertTrue(service.retry("pid-1"));
    }

    @Test
    @DisplayName("死信列表按 key 过滤并映射为视图")
    void listPoison_filtersByKeyAndMaps() {
        OrderedKafkaConsumer consumer = mock(OrderedKafkaConsumer.class);
        when(provider.getIfAvailable()).thenReturn(consumer);
        PoisonMessage keep = new PoisonMessage("p1", "t", 0, 1L, "k1", "{}", 3, "err", 100L);
        PoisonMessage other = new PoisonMessage("p2", "t", 0, 2L, "k2", "{}", 3, "err", 200L);
        when(consumer.poisonEntries()).thenReturn(List.of(keep, other));

        PageResult<KafkaPoisonMessageView> result = service.listPoison(1, 10, "k1");
        assertEquals(1L, result.total());
        assertEquals("p1", result.records().get(0).id());
        assertEquals("t", result.records().get(0).topic());
    }
}
