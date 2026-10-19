package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 绑定/解绑手机号或邮箱请求。
 * <p>绑定即"写入该字段"，解绑即"置空该字段"，统一走本接口，
 * 避免为解绑单独开一个语义重复的接口。
 *
 * @param channel 渠道：MOBILE / EMAIL
 * @param contact 联系方式；解绑时传空字符串
 */
public record BindContactRequest(
        @NotBlank(message = "{validation.contact.channel.notBlank}")
        @Pattern(regexp = "^(MOBILE|EMAIL)$", message = "{validation.contact.channel.pattern}")
        String channel,

        String contact) {
}
