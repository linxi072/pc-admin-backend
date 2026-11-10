package com.acme.scaffold.im.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 消息发送请求。content 为消息正文；msgType 缺省 TEXT；replyToId 为可选回复目标。
 */
@SuppressWarnings("unused")
@lombok.Data
public class MessageSendRequest implements Serializable {

    @NotNull
    private Long conversationId;

    @NotBlank
    @Size(max = 4000)
    private String content;

    @Size(max = 20)
    private String msgType;

    private Long replyToId;
}
