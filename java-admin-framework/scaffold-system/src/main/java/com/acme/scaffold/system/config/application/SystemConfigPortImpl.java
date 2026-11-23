package com.acme.scaffold.system.config.application;

import com.acme.scaffold.common.config.SystemConfigPort;
import com.acme.scaffold.common.config.SystemConfigs;
import com.acme.scaffold.system.config.dto.ConfigVO;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 系统变量端口实现：把「系统变量」（sys_config）接入配置读取链路（配置归口）。
 *
 * <p>只认 {@code status=ACTIVE/NORMAL} 且 configValue 非空的配置；停用或空值视为未配置，
 * 由调用方回退到 code default，保证「在系统变量界面停用某项即恢复默认行为」的运维语义。</p>
 */
@Component
public class SystemConfigPortImpl implements SystemConfigPort {

    private final ConfigService configService;

    public SystemConfigPortImpl(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    public void register() {
        SystemConfigs.bind(this);
    }

    @Override
    public Optional<String> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        ConfigVO vo = configService.getByKey(key);
        if (vo == null || !isActive(vo.status())) {
            return Optional.empty();
        }
        String value = vo.configValue() != null && !vo.configValue().isBlank()
                ? vo.configValue() : vo.defaultValue();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static boolean isActive(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        String s = status.trim().toUpperCase();
        return !"DISABLED".equals(s) && !"INACTIVE".equals(s) && !"0".equals(s) && !"FALSE".equals(s);
    }
}
