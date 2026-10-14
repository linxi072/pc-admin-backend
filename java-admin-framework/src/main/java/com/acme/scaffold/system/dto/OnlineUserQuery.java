package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.PageQuery;

/**
 * 在线用户分页查询入参。
 *
 * <p>status 为「会话展示状态」筛选：{@code ONLINE} / {@code IDLE} / {@code RESTRICTED}，
 * 为空表示全部。账号与 IP 均为模糊匹配（服务端拼参数化 LIKE，防注入）。
 * 排序固定为「最近活跃倒序」（最活跃在前），不开放客户端排序——在线列表
 * 语义上就是看「谁正在活跃」，固定口径反而更稳。
 */
public record OnlineUserQuery(int page, int size, String username, String ip, String status) {

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size);
    }

    /** 查询值清洗：去首尾空白，空串归一为 null。 */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
