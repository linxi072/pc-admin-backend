package com.acme.scaffold.security.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录验证码开关与参数。
 *
 * <p>{@code enabled} 默认 {@code false}：功能完整实现，但默认不开启，避免在没有前端配合时直接阻断现有登录链路。
 * 运维在 application(-local/-prod).yml 中显式置 {@code auth.captcha.enabled: true} 即可启用，
 * 前端需先调用 {@code GET /api/auth/captcha} 取得 token 与问题，登录时回传 token + 答案。
 */
@ConfigurationProperties(prefix = "auth.captcha")
public class CaptchaProperties {

    /** 是否启用登录验证码校验。 */
    private boolean enabled = false;

    /** 验证码有效时长（秒）。 */
    private int expireSeconds = 120;

    /** 运算题操作数上界（题目为 a + b，a/b ∈ [1, maxNumber]）。 */
    private int maxNumber = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(int expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public int getMaxNumber() {
        return maxNumber;
    }

    public void setMaxNumber(int maxNumber) {
        this.maxNumber = maxNumber;
    }
}
