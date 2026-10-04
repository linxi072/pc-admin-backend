package com.acme.scaffold.security.context;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.error.SecurityErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 基于 Spring SecurityContextHolder 的安全上下文实现。
 */
@Slf4j
@Component
public class SecurityContextFacadeImpl implements SecurityContextFacade {

    @Override
    public Optional<CurrentPrincipal> getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    @Override
    public CurrentPrincipal requireCurrentPrincipal() {
        return getCurrentPrincipal().orElseThrow(
                () -> new BusinessException(SecurityErrorCode.TOKEN_REVOKED, "凭证已失效，请重新登录"));
    }
}
