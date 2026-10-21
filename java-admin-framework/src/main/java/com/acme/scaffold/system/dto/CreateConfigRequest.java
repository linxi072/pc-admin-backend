package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateConfigRequest(
        @NotBlank String configKey,
        @NotBlank String configName,
        String configValue,
        String defaultValue,
        @NotBlank String configType,
        String remark,
        Integer editable,
        String status) {
}
