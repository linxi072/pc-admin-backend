package com.acme.scaffold.realtime.port;

import java.util.Map;

/**
 * 实时推送模板渲染端口（防腐层 / SPI）。
 *
 * <p>realtime 模块只依赖本端口，<b>不依赖通知模板中心（system 模块）</b>，
 * 由 system 模块以适配器形式注入实现，保持 realtime → system 单向依赖（避免模块成环，
 * 与 {@link HandshakeTokenAuthenticator} 同源模式）。</p>
 *
 * <p><b>语义</b>：按模板编码取「启用中」的实时推送模板（WEBSOCKET 渠道），用变量映射渲染
 * {@code ${var}} 占位后返回标题 / 正文 / 消息类型；模板缺失或停用时由实现方抛业务异常，
 * realtime 侧不静默降级（与 M11 send-with-template 口径一致）。</p>
 */
public interface RealtimeTemplatePort {

    /**
     * 渲染实时推送模板。
     *
     * @param templateCode 模板编码（须为启用中的 WEBSOCKET 渠道模板）
     * @param variables    变量名 → 取值；未提供变量保持 {@code ${name}} 原样
     * @return 渲染结果（标题 / 正文 / 消息类型，均非空）
     */
    RealtimeTemplateRenderResult render(String templateCode, Map<String, String> variables);
}
