package com.acme.scaffold.system.dto;

import com.acme.scaffold.system.entity.SysPostDO;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 岗位视图。
 *
 * @param id        岗位 ID（雪花 ID，序列化为字符串防 JS 精度丢失）
 * @param postCode  岗位编码（租户内唯一，稳定标识）
 * @param postName  岗位名称
 * @param postSort  显示排序（升序）
 * @param status    状态：ACTIVE / DISABLED
 * @param remark    备注
 * @param createdAt 创建时间
 */
public record PostView(@JsonSerialize(using = ToStringSerializer.class) Long id,
                       String postCode,
                       String postName,
                       Integer postSort,
                       String status,
                       String remark,
                       LocalDateTime createdAt) {

    public static PostView from(SysPostDO post) {
        return new PostView(post.getId(), post.getPostCode(), post.getPostName(),
                post.getPostSort(), post.getStatus(), post.getRemark(), post.getCreatedAt());
    }
}
