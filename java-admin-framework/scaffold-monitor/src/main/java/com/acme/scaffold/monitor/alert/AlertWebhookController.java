package com.acme.scaffold.monitor.alert;

import com.acme.scaffold.common.api.Result;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alertmanager 应用内 webhook 接收端（A2）。
 *
 * <p>作为 Alertmanager 的「真实接收器」：Alertmanager 通过 webhook_configs 将告警 POST 到本端点，
 * 应用完成解析、入库与事件发布。可选 {@code X-Alert-Token} 头用于轻量鉴权；
 * 生产环境须配合网络隔离 / 反向代理 / 网关进一步加固。
 */
@RestController
@RequestMapping("/api/monitoring/alerts")
public class AlertWebhookController {

    private final AlertReceiverService service;
    private final AlertProperties props;

    public AlertWebhookController(AlertReceiverService service, AlertProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Result<?>> webhook(
            @RequestBody(required = false) AlertPayload payload,
            @RequestHeader(value = "X-Alert-Token", required = false) String token) {

        if (!props.getWebhook().isEnabled()) {
            return ResponseEntity.status(503)
                    .body(Result.error("ALERT_WEBHOOK_DISABLED", "告警接收端未启用"));
        }
        String expected = props.getWebhook().getToken();
        if (expected != null && !expected.isBlank() && !expected.equals(token)) {
            return ResponseEntity.status(401)
                    .body(Result.error("ALERT_TOKEN_INVALID", "告警令牌不匹配"));
        }

        int count = service.receive(payload);
        if (count == 0) {
            return ResponseEntity.status(400)
                    .body(Result.error("ALERT_EMPTY", "告警负载为空"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("received", count);
        body.put("traceId", MDC.get("traceId") == null ? "-" : MDC.get("traceId"));
        return ResponseEntity.accepted().body(Result.success(body));
    }

    /**
     * 存量兼容读取端点（旧版前端/脚本可能仍在调用）。
     *
     * <p><b>已硬化</b>：补 {@code system:monitor:read} 权限——告警数据属敏感运维信息，
     * 匿名可拉取全部收件箱是真实的越权面；管理面请改用
     * {@link com.acme.scaffold.monitor.alert.controller.AlertInboxController}
     * （{@code /api/monitoring/alerts/inbox}，支持筛选/汇总/清空与视图 DTO）。
     * 本端点保留原返回形态（领域对象直出）仅为兼容，新代码不应依赖。</p>
     */
    @GetMapping("/webhook/inbox")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<?> inbox() {
        return Result.success(service.inbox());
    }
}
