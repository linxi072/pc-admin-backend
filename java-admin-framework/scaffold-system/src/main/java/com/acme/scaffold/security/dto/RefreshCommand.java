package com.acme.scaffold.security.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshCommand(@NotBlank String refreshToken) {
}
