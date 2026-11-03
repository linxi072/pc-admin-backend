package com.acme.scaffold.chaos;

import com.acme.scaffold.chaos.dto.ChaosArmRequest;
import com.acme.scaffold.chaos.model.ChaosFault;
import com.acme.scaffold.chaos.model.ChaosFaultType;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChaosService} 单元测试：arm 生成 id 与校验、disarm / clear / list。
 */
@DisplayName("ChaosService 单元测试")
class ChaosServiceTest {

    private final ChaosRegistry registry = new ChaosRegistry();
    private final ChaosService service = new ChaosService(registry);

    @Test
    @DisplayName("arm 生成非空 id 并登记；list 可见；disarm / clear 生效")
    void armGeneratesIdAndRegisters() {
        ChaosFault f = service.arm(new ChaosArmRequest(ChaosFaultType.ERROR, "/api/pay/**",
                null, 503, 1.0, "pay down"));
        assertNotNull(f.id());
        assertEquals(1, service.list().size());

        service.disarm(f.id());
        assertTrue(service.list().isEmpty());

        service.arm(new ChaosArmRequest(ChaosFaultType.LATENCY, "/api/x/**", 50, null, null, null));
        assertEquals(1, service.list().size());
        service.clear();
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("arm 入参校验：type 为空抛 400")
    void armRejectsNullType() {
        assertThrows(BusinessException.class,
                () -> service.arm(new ChaosArmRequest(null, "/api/x/**", null, null, null, null)));
    }

    @Test
    @DisplayName("arm 入参校验：pathPattern 为空抛 400")
    void armRejectsBlankPattern() {
        assertThrows(BusinessException.class,
                () -> service.arm(new ChaosArmRequest(ChaosFaultType.ERROR, "  ", null, null, null, null)));
    }

    @Test
    @DisplayName("arm 入参校验：probability 越界抛 400")
    void armRejectsIllegalProbability() {
        assertThrows(BusinessException.class,
                () -> service.arm(new ChaosArmRequest(ChaosFaultType.ERROR, "/api/x/**", null, 503, 1.5, null)));
        assertThrows(BusinessException.class,
                () -> service.arm(new ChaosArmRequest(ChaosFaultType.ERROR, "/api/x/**", null, 503, -0.1, null)));
    }

    @Test
    @DisplayName("arm 入参校验：LATENCY 负时延抛 400")
    void armRejectsNegativeLatency() {
        assertThrows(BusinessException.class,
                () -> service.arm(new ChaosArmRequest(ChaosFaultType.LATENCY, "/api/x/**", -5, null, null, null)));
    }

    @Test
    @DisplayName("arm 缺省值：latencyMillis=0 / errorStatus=503 / probability=1.0")
    void armAppliesDefaults() {
        ChaosFault f = service.arm(new ChaosArmRequest(ChaosFaultType.ERROR, "/api/x/**", null, null, null, null));
        assertEquals(503, f.errorStatus());
        assertEquals(1.0, f.probability(), 0.0001);
        assertEquals(0, f.latencyMillis());
    }
}
