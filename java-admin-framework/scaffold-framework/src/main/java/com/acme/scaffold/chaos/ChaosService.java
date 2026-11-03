package com.acme.scaffold.chaos;

import com.acme.scaffold.chaos.dto.ChaosArmRequest;
import com.acme.scaffold.chaos.model.ChaosFault;
import com.acme.scaffold.chaos.model.ChaosFaultType;
import com.acme.scaffold.common.exception.BusinessException;

import java.util.List;
import java.util.UUID;

/**
 * 混沌故障编排服务（R15）：负责校验入参、生成故障 id、登记/解除/查询故障规则。
 *
 * <p>本服务为纯内存编排，不触网、不落库；状态存于 {@link ChaosRegistry}（进程级，重启即清空）。
 * 任何非法入参（缺类型/路径、概率越界、LATENCY 负时延）抛 {@link BusinessException#badRequest}，
 * 由全局异常处理器映射为 400。</p>
 *
 * <p>本类不标注 {@code @Service}：其 Bean 由 {@link ChaosAutoConfiguration} 在
 * {@code app.chaos.enabled=true} 时注册，关闭时整体不存在，与既有链路零回归。</p>
 */
public class ChaosService {

    private final ChaosRegistry registry;

    public ChaosService(ChaosRegistry registry) {
        this.registry = registry;
    }

    /** 当前全部故障规则。 */
    public List<ChaosFault> list() {
        return registry.list();
    }

    /**
     * 登记一条故障规则（生成 id 后交给注册表）。
     *
     * @param request 故障注入请求
     * @return 已登记的故障规则（含服务端生成 id）
     */
    public ChaosFault arm(ChaosArmRequest request) {
        if (request.type() == null) {
            throw BusinessException.badRequest("type 不能为空");
        }
        if (request.pathPattern() == null || request.pathPattern().isBlank()) {
            throw BusinessException.badRequest("pathPattern 不能为空");
        }
        double probability = request.probabilityOrDefault();
        if (probability < 0.0 || probability > 1.0) {
            throw BusinessException.badRequest("probability 须在 0~1 之间");
        }
        if (request.type() == ChaosFaultType.LATENCY && request.latencyMillisOrDefault() < 0) {
            throw BusinessException.badRequest("LATENCY 的 latencyMillis 不能为负");
        }
        ChaosFault fault = new ChaosFault(
                UUID.randomUUID().toString(),
                request.type(),
                request.pathPattern(),
                request.latencyMillisOrDefault(),
                request.errorStatusOrDefault(),
                probability,
                request.description());
        registry.arm(fault);
        return fault;
    }

    /** 按 id 解除一条故障规则。 */
    public void disarm(String id) {
        registry.disarm(id);
    }

    /** 清空全部故障规则。 */
    public void clear() {
        registry.clear();
    }
}
