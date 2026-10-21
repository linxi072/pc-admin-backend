package com.acme.scaffold.kafka.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.kafka.dto.KafkaConsumerStatusView;
import com.acme.scaffold.kafka.dto.KafkaPoisonMessageView;
import com.acme.scaffold.kafka.service.KafkaMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kafka 有序消费监控与死信补偿接口。
 *
 * <p>能力：消费容器运行态与指标（handled/stale/poison/commit-failure/版本表）、死信列表、
 * 毒消息重试与丢弃补偿。消费容器由 {@code acme.kafka.ordered-consumer.enabled} 门禁控制，
 * 未启用时状态返回 {@code enabled=false}，补偿操作返回 false。</p>
 *
 * <p><b>权限</b>：读接口需 {@code system:kafka:read}；补偿（写）接口需 {@code system:kafka:manage}。
 * 权限码由 {@code ApiResourceScanner} 自动登记到 {@code sys_api_resource} 并授予 SUPER_ADMIN，
 * 同时登记 {@code sys_menu} 菜单（route=/kafka/monitor）以驱动侧边栏可见性。</p>
 */
@Tag(name = "Kafka 有序消费监控")
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaMonitorController {

    private final KafkaMonitorService monitorService;

    @Operation(summary = "消费容器运行态与累计指标")
    @GetMapping("/consumer/status")
    @PreAuthorize("hasAuthority('system:kafka:read')")
    public Result<KafkaConsumerStatusView> status() {
        return Result.success(monitorService.status());
    }

    @Operation(summary = "死信（毒消息）列表，支持按 message key 精确过滤")
    @GetMapping("/consumer/poison")
    @PreAuthorize("hasAuthority('system:kafka:read')")
    public Result<PageResult<KafkaPoisonMessageView>> poison(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String key) {
        return Result.success(monitorService.listPoison(page, size, key));
    }

    @Operation(summary = "重试毒消息：清除失败计数与版本表占位，允许上游重投后重新处理")
    @PostMapping("/consumer/poison/{id}/retry")
    @PreAuthorize("hasAuthority('system:kafka:manage')")
    public Result<Boolean> retry(@PathVariable String id) {
        return Result.success(monitorService.retry(id));
    }

    @Operation(summary = "丢弃毒消息：从死信存储移除，视为已人工确认无需补偿")
    @PostMapping("/consumer/poison/{id}/discard")
    @PreAuthorize("hasAuthority('system:kafka:manage')")
    public Result<Boolean> discard(@PathVariable String id) {
        return Result.success(monitorService.discard(id));
    }
}
