package com.acme.scaffold.common.id;

import com.acme.scaffold.common.IdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link IdGeneratorInitializer} 单测：覆盖未配置、合法配置、越界与非数字四类路径。
 *
 * <p>注意：{@link IdGenerator#setWorkerId} 为静态写，用例间通过 {@link #restoreDefaultWorkerId()}
 * 复位为默认值 1，避免污染同一 JVM 内的其他测试。</p>
 */
class IdGeneratorInitializerTest {

    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_BITS = 10L;

    @AfterEach
    void restoreDefaultWorkerId() {
        IdGenerator.setWorkerId(1L);
    }

    @Test
    @DisplayName("未配置 app.id.worker-id 时不抛异常，维持默认 workerId=1")
    void unconfiguredKeepsDefault() {
        IdGeneratorInitializer init0 = new IdGeneratorInitializer(null);
        IdGeneratorInitializer initBlank = new IdGeneratorInitializer("   ");
        init0.init();
        initBlank.init();

        long id = IdGenerator.nextId();
        assertEquals(1L, (id >> SEQUENCE_BITS) & ~(-1L << WORKER_ID_BITS));
    }

    @Test
    @DisplayName("合法 workerId 配置生效：生成的 ID 携带对应 workerId 段")
    void validWorkerIdApplied() {
        IdGeneratorInitializer initializer = new IdGeneratorInitializer("7");
        initializer.init();

        long id = IdGenerator.nextId();
        assertEquals(7L, (id >> SEQUENCE_BITS) & ~(-1L << WORKER_ID_BITS));
    }

    @Test
    @DisplayName("workerId 越界（>1023）时启动失败并给出可读原因")
    void outOfRangeWorkerIdFailsFast() {
        IdGeneratorInitializer initializer = new IdGeneratorInitializer("1024");
        IllegalStateException ex = assertThrows(IllegalStateException.class, initializer::init);
        assertEquals("app.id.worker-id 非法: workerId 超出范围 [0, 1023]", ex.getMessage());
    }

    @Test
    @DisplayName("workerId 非数字时构造期即失败")
    void nonNumericWorkerIdFailsFast() {
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> new IdGeneratorInitializer("abc"));
        assertEquals("app.id.worker-id 必须为整数: abc", ex.getMessage());
    }
}
