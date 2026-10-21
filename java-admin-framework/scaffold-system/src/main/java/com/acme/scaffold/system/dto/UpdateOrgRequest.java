package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrgRequest(
        Long parentId,
        @NotBlank String orgCode,
        @NotBlank String orgName,
        String orgType,
        Integer sortNo,
        Long leaderUserId,
        String status) {
}
