package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.validation.CreateGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank String displayName,
        String mobile,
        String email,
        Long primaryOrgId,
        Set<Long> roleIds) {
}
