package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.validation.UpdateGroup;

import java.util.Set;

public record UpdateUserRequest(
        String displayName,
        String mobile,
        String email,
        Long primaryOrgId,
        String status,
        Set<Long> roleIds) {
}
