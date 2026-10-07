package com.acme.scaffold.system.dto;

public record DictDataVO(Long id, String dictTypeCode, String dictLabel, String dictValue,
                         Integer dictSort, String status, String remark) {
}
