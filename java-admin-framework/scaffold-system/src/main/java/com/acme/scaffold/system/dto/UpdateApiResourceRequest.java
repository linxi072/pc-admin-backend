package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateApiResourceRequest(
        @NotBlank String resourceName,
        @NotBlank String permissionCode,
        @NotBlank String httpMethod,
        @NotBlank String pathPattern,
        String authMode,
        String status,
        String riskLevel) {
}
