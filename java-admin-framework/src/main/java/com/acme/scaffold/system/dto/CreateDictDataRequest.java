package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDictDataRequest(
        @NotBlank String dictTypeCode,
        @NotBlank String dictLabel,
        @NotBlank String dictValue,
        Integer dictSort,
        String status,
        String remark) {
}
