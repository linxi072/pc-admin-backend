package com.acme.scaffold.system.config.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateConfigRequest(
        @NotBlank String configKey,
        @NotBlank String configName,
        String configValue,
        String defaultValue,
        @NotBlank String configType,
        String remark,
        Integer editable,
        String status) {
}
