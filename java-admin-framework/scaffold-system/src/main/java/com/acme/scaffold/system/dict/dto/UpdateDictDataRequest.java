package com.acme.scaffold.system.dict.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDictDataRequest(
        @NotBlank String dictTypeCode,
        @NotBlank String dictLabel,
        @NotBlank String dictValue,
        Integer dictSort,
        String status,
        String remark) {
}
