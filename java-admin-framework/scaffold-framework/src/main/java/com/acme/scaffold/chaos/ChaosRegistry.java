package com.acme.scaffold.chaos;

import com.acme.scaffold.chaos.model.ChaosFault;
import java.util.List;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.util.AntPathMatcher;

/**
 * 混沌故障注册表（R15）：进程内留存当前已 arm 的故障规则，供 {@code ChaosFilter} 在请求链路匹配并注入。
 *
 * <p>线程安全：基于 {@link CopyOnWriteArrayList}，读写并发安全；故障规则数量极小（演练期个位数），
 * 写操作（arm/disarm/clear）极低频，读操作（match）每请求一次，COW 开销可忽略。</p>
 *
 * <p>本类不标注 {@code @Component}：其 Bean 由 {@link ChaosAutoConfiguration} 在
 * {@code app.chaos.enabled=true} 时注册，关闭时整体不存在，与既有链路零回归。</p>
 */
public class ChaosRegistry {

    private final List<ChaosFault> faults = new CopyOnWriteArrayList<>();
    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 登记一条故障规则（幂等：同 id 重复 arm 会覆盖）。 */
    public void arm(ChaosFault fault) {
        disarm(fault.id());
        faults.add(fault);
    }

    /** 按 id 解除一条故障规则。 */
    public void disarm(String id) {
        faults.removeIf(f -> f.id().equals(id));
    }

    /** 清空全部故障规则（演练结束恢复常态）。 */
    public void clear() {
        faults.clear();
    }

    /** 当前全部故障规则（只读副本）。 */
    public List<ChaosFault> list() {
        return List.copyOf(faults);
    }

    /**
     * 按请求 URI 匹配第一个命中的故障规则（Ant 风格）。
     *
     * @param requestUri 请求路径（含上下文路径后的部分，如 /api/pay/order/1）
     * @return 命中的规则（无匹配时为空）
     */
    public Optional<ChaosFault> match(String requestUri) {
        if (requestUri == null) {
            return Optional.empty();
        }
        return faults.stream()
                .filter(f -> matcher.match(f.pathPattern(), requestUri))
                .findFirst();
    }
}
