package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDictTypeRequest(
        @NotBlank String dictCode,
        @NotBlank String dictName,
        String status,
        Integer sortNo,
        String remark) {
}
