package com.acme.scaffold.common.api;

/**
 * 分页排序项。字段名由服务端白名单映射为数据库列，禁止直接使用客户端传入的列名。
 */
public record SortItem(String field, String direction) {

    public SortItem {
        direction = (direction == null || direction.isBlank()) ? "ASC" : direction.toUpperCase();
    }

    public boolean ascending() {
        return !"DESC".equals(direction);
    }
}
