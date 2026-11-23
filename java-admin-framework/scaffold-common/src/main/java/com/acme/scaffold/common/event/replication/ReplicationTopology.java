package com.acme.scaffold.common.event.replication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨区域复制拓扑路由（E7 纯函数）：按单元拓扑计算事件扇出目标。
 *
 * <p>规则：仅向健康且在线的同伴单元复制；源单元永不成为自己的目标（避免回环复制）；
 * 目标按拓扑声明顺序去重保序，便于接收端稳定消费。拓扑不可变，构造后不再变更。</p>
 */
public final class ReplicationTopology {

    /** 单元声明：名称 → 是否在线（离线单元不接收复制）。 */
    private final Map<String, Boolean> units = new LinkedHashMap<>();

    public ReplicationTopology(Map<String, Boolean> units) {
        if (units != null) {
            units.forEach((name, online) -> this.units.put(name, Boolean.TRUE.equals(online)));
        }
    }

    /** 计算从 sourceUnit 出发应复制的目标单元（排除自身与离线单元）。 */
    public List<String> targetsFrom(String sourceUnit) {
        List<String> targets = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : units.entrySet()) {
            if (!e.getKey().equals(sourceUnit) && e.getValue()) {
                targets.add(e.getKey());
            }
        }
        return List.copyOf(targets);
    }

    /** 单元是否在线；未知单元视为离线（fail-closed，不向未知单元复制）。 */
    public boolean isOnline(String unit) {
        return Boolean.TRUE.equals(units.get(unit));
    }

    public List<String> allUnits() {
        return List.copyOf(units.keySet());
    }
}
