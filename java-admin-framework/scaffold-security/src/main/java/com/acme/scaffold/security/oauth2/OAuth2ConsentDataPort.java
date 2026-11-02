package com.acme.scaffold.security.oauth2;

import java.util.Optional;

/**
 * OAuth2 用户同意(consent)数据访问端口（防腐层）。
 *
 * <p>由 {@code security.oauth2} 定义，返回 security 自有 {@link OAuth2ConsentInfo} 快照，
 * system 侧提供适配器经 jOOQ 落库 {@code sys_oauth2_consent}，消除 security 对 system 持久化细节的直连。</p>
 */
public interface OAuth2ConsentDataPort {

    /** 查询用户对该客户端最新一条已批准的同意（无或已撤销则返回 empty）。 */
    Optional<OAuth2ConsentInfo> findLatestApproved(Long userId, String clientId);

    /** 记录（或续期）一条已批准同意（幂等：同一用户+客户端仅保留最新一条）。 */
    void recordApproved(Long userId, String clientId, String scopes);
}
