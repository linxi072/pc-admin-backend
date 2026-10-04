package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateApiResourceRequest(
        @NotBlank String resourceName,
        @NotBlank String permissionCode,
        @NotBlank String httpMethod,
        @NotBlank String pathPattern,
        String authMode,
        String riskLevel) {
}
