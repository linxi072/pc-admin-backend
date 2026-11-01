package com.acme.scaffold.security.oauth2;

import java.time.LocalDateTime;

/**
 * 用户同意(consent)快照（防腐层）。
 *
 * <p>由 {@code security.oauth2} 定义，承载已批准 scope 与（可选）过期时间，
 * 避免 security 直连 system 的 {@code sys_oauth2_consent} 持久化细节。</p>
 *
 * @param scopes     已批准的授权范围（规范化逗号分隔串）
 * @param expiresAt 过期时间（可空；空表示长期有效）
 */
public record OAuth2ConsentInfo(String scopes, LocalDateTime expiresAt) {
}
