package com.acme.scaffold.config.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 跨域允许源配置（{@code app.cors.*}）。
 *
 * <p>用于替换原先硬编码的 {@code setAllowedOrigins("*")}，使 WebSocket 握手来源可经配置收紧。
 * 默认 {@code ["*"]}（放开全部来源，便于本地前后端分离联调）；生产应改为具体前端域名，
 * 例如 {@code app.cors.allowed-origins: ["https://admin.example.com"]}，收窄跨站 WebSocket 握手面。</p>
 *
 * <p>取值为空或非法（{@code null} / 空列表）时回退为 {@code ["*"]}，与缺省行为一致，
 * 避免误配导致全部握手被拒而服务不可用。</p>
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** 允许建立 WebSocket 的来源（Origin）列表；含 {@code *} 表示放开全部来源。 */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        // 防御空配置：视为放开全部来源，与缺省一致
        this.allowedOrigins = (allowedOrigins == null || allowedOrigins.isEmpty())
                ? new ArrayList<>(List.of("*"))
                : allowedOrigins;
    }
}
