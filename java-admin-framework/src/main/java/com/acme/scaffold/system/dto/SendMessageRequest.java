package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发送站内信请求（同时支持单条与批量）。
 *
 * <p><b>三种投递模式</b>（按优先级判定）：
 * <ol>
 *   <li><b>指定接收人</b>：传 {@code receiverIds}，直接给这些用户投递；</li>
 *   <li><b>按角色/部门批量</b>：传 {@code roleIds} 和/或 {@code orgIds}，
 *       由服务端解析出接收人集合（角色与部门之间为「或」关系，命中任一即投递）；</li>
 *   <li>两者都不传 → 请求非法，返回校验失败。</li>
 * </ol>
 * 注意：V6 收敛后用户仅绑定单角色单部门，因此批量筛选只需匹配 sys_user.role_id / sys_user.org_id。
 */
public record SendMessageRequest(
        @NotBlank @Size(max = 200, message = "标题长度不能超过200") String title,
        @NotBlank(message = "消息内容不能为空") String content,
        /** NOTICE(通知) / ALERT(告警) / SYSTEM(系统)，为空默认 NOTICE */
        String msgType,
        /** 单条发送：显式接收人ID列表 */
        List<Long> receiverIds,
        /** 批量发送：按角色筛选 */
        List<Long> roleIds,
        /** 批量发送：按部门筛选 */
        List<Long> orgIds) {
}
