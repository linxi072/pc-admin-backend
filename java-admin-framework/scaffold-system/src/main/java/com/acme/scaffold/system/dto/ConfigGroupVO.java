package com.acme.scaffold.system.dto;

import java.util.List;

/**
 * 参数配置分组视图（M36）：同一命名空间前缀下的配置聚合为一个分区，
 * 便于前端按「安全 / 缓存 / 通知 / …」分区展示。
 */
public record ConfigGroupVO(String group, String label, List<ConfigVO> items) {
}
