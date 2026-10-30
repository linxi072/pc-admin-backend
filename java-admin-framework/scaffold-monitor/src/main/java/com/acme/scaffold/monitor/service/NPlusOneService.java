package com.acme.scaffold.monitor.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.monitor.detection.NPlusOneProbe;
import com.acme.scaffold.monitor.detection.NPlusOneSuspect;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.monitor.dto.NPlusOneStatusView;
import com.acme.scaffold.monitor.dto.NPlusOneSuspectView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * N+1 查询探测运维服务：聚合 {@link NPlusOneProbe}（线程级探测）与
 * {@link NPlusOneSuspectRegistry}（有界事件留存），向 REST 层提供只读查询与清理操作。
 *
 * <p>探测与留存组件由 {@code config.jooq.JooqConfig} 无条件装配（默认阈值 10），
 * 本服务不引入开关语义——探测本身零成本（仅同构 SQL 计数），留存容量有界，
 * 与 {@code SlowQueryListener} 同为常驻只读观测。</p>
 */
@Service
@RequiredArgsConstructor
public class NPlusOneService {

    /** 单次查询返回条数上限（防止一次拉全量留存）。 */
    public static final int MAX_LIMIT = 200;

    private final NPlusOneProbe probe;
    private final NPlusOneSuspectRegistry registry;

    /**
     * 查询最近的 N+1 疑似事件（最新在前）。
     *
     * @param limit 返回条数（1 ~ {@value #MAX_LIMIT}），越界抛 {@code VALIDATION_ERROR} → 400
     * @return 视图列表（可能为空）
     */
    public List<NPlusOneSuspectView> recentSuspects(int limit) {
        int effective = validateLimit(limit);
        return registry.recent(effective).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 采集探测运行态：阈值、留存容量、当前条数与累计越限口径。
     *
     * @return 状态视图
     */
    public NPlusOneStatusView status() {
        return new NPlusOneStatusView(
                probe.threshold(),
                registry.capacity(),
                registry.size(),
                registry.total(),
                registry.lastBreachAt());
    }

    /**
     * 清空疑似事件留存列表（累计计数 {@code totalBreaches} 与最近登记时间不清零，
     * 保持单调累计口径以便评估告警频率）。
     *
     * @return 固定 true（保留布尔包络便于前端统一处理）
     */
    public boolean clear() {
        registry.clear();
        return true;
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw BusinessException.badRequest("查询条数需在 1~" + MAX_LIMIT + " 之间");
        }
        return limit;
    }

    private NPlusOneSuspectView toView(NPlusOneSuspect suspect) {
        return new NPlusOneSuspectView(
                suspect.fingerprint(),
                suspect.sampleSql(),
                suspect.count(),
                suspect.threadName(),
                suspect.timestampMillis());
    }
}
