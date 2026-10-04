package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank String roleCode,
        @NotBlank String roleName,
        String roleType,
        String status,
        Integer sortNo,
        Set<Long> menuIds,
        Set<Long> apiIds) {
}
