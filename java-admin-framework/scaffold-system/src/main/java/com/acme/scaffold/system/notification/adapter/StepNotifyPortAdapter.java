package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.notification.StepNotifyPort;
import com.acme.scaffold.system.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link StepNotifyPort} 的 system 侧适配器：委托 {@code MessageService#sendCcNotification} 落地站内信抄送。
 *
 * <p>使 workflow 模块仅依赖 common 的 Port 契约，解耦对 system 服务实现的直连（重构批次 B2）。
 * 抄送行为与原先 workflow 直连 {@code MessageService} 完全一致，仅依赖方向反转。
 */
@Component
@RequiredArgsConstructor
public class StepNotifyPortAdapter implements StepNotifyPort {

    private final MessageService messageService;

    @Override
    public int sendCc(List<Long> userIds, String title, String content) {
        return messageService.sendCcNotification(userIds, title, content);
    }
}
