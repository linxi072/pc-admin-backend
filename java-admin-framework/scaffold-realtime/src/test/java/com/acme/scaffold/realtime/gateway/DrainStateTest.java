package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link DrainState} 七步状态机单测：逐步推进与超时兜底。 */
@DisplayName("DrainState 优雅退出状态机")
class DrainStateTest {

    private final DrainState drain = DrainState.designDefault();
    private long now = 0L;

    private DrainState.DrainStep step(DrainState.Signal signal) {
        return drain.advance(signal, now);
    }

    @Test
    @DisplayName("从 RUNNING 进入 HEALTH_DRAINING")
    void running_to_healthDraining() {
        assertEquals(DrainState.DrainStep.HEALTH_DRAINING, step(DrainState.Signal.of(0, 0, false, 0)));
    }

    @Test
    @DisplayName("前 3 步为网关原子动作，收到信号即推进")
    void atomic_steps_advance() {
        step(DrainState.Signal.of(0, 0, false, 0)); // HEALTH_DRAINING
        assertEquals(DrainState.DrainStep.ACCEPT_STOPPED, step(DrainState.Signal.of(0, 0, false, 0)));
        assertEquals(DrainState.DrainStep.ROUTES_MIGRATING, step(DrainState.Signal.of(0, 0, false, 0)));
        assertEquals(DrainState.DrainStep.INFLIGHT_DRAINING, step(DrainState.Signal.of(0, 0, false, 0)));
    }

    @Test
    @DisplayName("INFLIGHT_DRAINING：in-flight 未排空且不超时则停留")
    void inflight_draining_waits() {
        step(DrainState.Signal.of(0, 0, false, 0)); // -> HEALTH_DRAINING
        step(DrainState.Signal.of(0, 0, false, 0)); // -> ACCEPT_STOPPED
        step(DrainState.Signal.of(0, 0, false, 0)); // -> ROUTES_MIGRATING
        step(DrainState.Signal.of(0, 0, false, 0)); // -> INFLIGHT_DRAINING
        DrainState.DrainStep s = step(DrainState.Signal.of(10, 1, false, 1000));
        assertEquals(DrainState.DrainStep.INFLIGHT_DRAINING, s);
    }

    @Test
    @DisplayName("INFLIGHT_DRAINING：排空或超时后进入 CLIENTS_CLOSING")
    void inflight_draining_done() {
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0)); // INFLIGHT_DRAINING
        // 超时兜底（elapsed >= 30000）
        assertEquals(DrainState.DrainStep.CLIENTS_CLOSING,
                step(DrainState.Signal.of(5, 1, false, 30_000L)));
    }

    @Test
    @DisplayName("CLIENTS_CLOSING：连接清空后进入 PRODUCER_FLUSHING")
    void clients_closing_to_flush() {
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 30_000L)); // CLIENTS_CLOSING
        assertEquals(DrainState.DrainStep.PRODUCER_FLUSHING, step(DrainState.Signal.of(0, 0, false, 0)));
    }

    @Test
    @DisplayName("PRODUCER_FLUSHING：flush 完成或超时后进入 TERMINATED")
    void producer_flushing_to_terminated() {
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 0));
        step(DrainState.Signal.of(0, 0, false, 30_000L)); // CLIENTS_CLOSING
        step(DrainState.Signal.of(0, 0, false, 0)); // PRODUCER_FLUSHING
        assertEquals(DrainState.DrainStep.TERMINATED, step(DrainState.Signal.of(0, 0, true, 0)));
    }
}
