package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateConfigRequest(
        @NotBlank String configKey,
        @NotBlank String configName,
        String configValue,
        @NotBlank String configType,
        String remark,
        String status) {
}
