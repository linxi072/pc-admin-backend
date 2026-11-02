package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.oauth2.domain.OAuth2TokenDO;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OAuth2 令牌管理应用服务（M43 令牌治理）。
 *
 * <p>承载管理端「查看活动令牌 / 吊销令牌」用例，编排 {@link OAuth2TokenRegistry} 与视图映射；
 * 不直接触碰持久层（与既有 *Service 保持一致的防腐边界）。</p>
 */
@Service
@RequiredArgsConstructor
public class OAuth2TokenAdminService {

    private final OAuth2TokenRegistry registry;

    /** 分页查询活动令牌，可按 clientId / userId 过滤。 */
    public PageResult<OAuth2TokenView> page(int page, int size, String clientId, Long userId) {
        PageResult<OAuth2TokenDO> result = registry.listActive(PageQuery.of(page, size), clientId, userId);
        List<OAuth2TokenView> views = result.records().stream().map(OAuth2TokenAdminService::toView).toList();
        return new PageResult<>(result.page(), result.size(), result.total(), views);
    }

    /** 吊销指定 jti（幂等：重复吊销安全）。 */
    public void revoke(String jti) {
        registry.revoke(jti);
    }

    private static OAuth2TokenView toView(OAuth2TokenDO t) {
        return new OAuth2TokenView(
                t.getJti(),
                t.getClientId(),
                t.getUserId(),
                t.getUsername(),
                t.getScope(),
                t.getTokenType(),
                t.getIssuedAt(),
                t.getExpiresAt(),
                t.getStatus());
    }
}
