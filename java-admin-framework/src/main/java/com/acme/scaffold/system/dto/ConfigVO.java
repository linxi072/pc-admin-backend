package com.acme.scaffold.system.dto;

public record ConfigVO(Long id, String configKey, String configName, String configValue,
                      String configType, String remark, String status) {
}
