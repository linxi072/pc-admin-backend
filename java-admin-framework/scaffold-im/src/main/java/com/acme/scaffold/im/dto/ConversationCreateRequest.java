package com.acme.scaffold.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/**
 * 会话创建请求。创建者自动作为成员（群主）加入；memberIds 仅填写「对方成员」。
 * P2P 仅允许 1 名对方成员；GROUP 至少 1 名。
 */
@SuppressWarnings("unused")
@lombok.Data
public class ConversationCreateRequest implements Serializable {

    @NotBlank
    @Size(max = 20)
    private String conversationType;

    @Size(max = 255)
    private String title;

    @NotNull
    @NotEmpty
    private List<Long> memberIds;
}
