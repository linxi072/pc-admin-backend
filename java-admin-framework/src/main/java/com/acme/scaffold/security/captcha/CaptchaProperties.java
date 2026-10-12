package com.acme.scaffold.security.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录验证码开关与参数。
 *
 * <p>{@code enabled} 默认 {@code false}：功能完整实现，但默认不开启，避免在没有前端配合时直接阻断现有登录链路。
 * 运维在 application(-local/-prod).yml 中显式置 {@code auth.captcha.enabled: true} 即可启用，
 * 前端需先调用 {@code GET /api/auth/captcha} 取得 token 与问题，登录时回传 token + 答案。</p>
 *
 * <p>运算类型默认加 / 减 / 乘 / 除四则全集（{@code operators}），可通过配置收窄
 * （如 {@code operators: ADD,SUB} 仅出加减题）；乘 / 除操作数使用独立上界
 * {@code mulMaxNumber}，避免乘积过大影响口算体验。</p>
 */
@ConfigurationProperties(prefix = "auth.captcha")
public class CaptchaProperties {

    /** 是否启用登录验证码校验。 */
    private boolean enabled = false;

    /** 验证码有效时长（秒）。 */
    private int expireSeconds = 120;

    /** 加法 / 减法操作数上界（a/b ∈ [1, maxNumber]）。 */
    private int maxNumber = 20;

    /** 乘法 / 除法操作数上界（乘法因子、除法除数与商均 ∈ [1, mulMaxNumber]，除数 ≥ 2）。 */
    private int mulMaxNumber = 10;

    /** 允许的运算类型；空表示四则全集。配置示例：operators: ADD,SUB,MUL,DIV。 */
    private List<ArithmeticRules.Operator> operators = new ArrayList<>(List.of(ArithmeticRules.Operator.values()));

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

    public int getMulMaxNumber() {
        return mulMaxNumber;
    }

    public void setMulMaxNumber(int mulMaxNumber) {
        this.mulMaxNumber = mulMaxNumber;
    }

    public List<ArithmeticRules.Operator> getOperators() {
        return operators;
    }

    public void setOperators(List<ArithmeticRules.Operator> operators) {
        this.operators = operators;
    }
}
