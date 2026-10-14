package com.acme.scaffold.security.password;

/**
 * 「忘记密码」自助重置的纯函数规则：身份核验判定，无 IO 无状态，便于离线单测。
 *
 * <p>设计约束：
 * <ul>
 *   <li><b>防账号枚举</b>：调用方对「用户不存在」「验证信息不匹配」必须返回同一错误
 *   （见 {@code AuthApplicationService#forgotPassword}），本类只回答「核验是否通过」，
 *   不区分失败原因，从结构上杜绝把「用户不存在」泄漏给调用方；</li>
 *   <li><b>验证凭据二选一</b>：手机号或邮箱任一与库中记录匹配即通过（内网运营后台场景，
 *   无短信/邮件下发通道，采用「知道即验证」的轻量核验）；</li>
 *   <li><b>null 安全</b>：库里 mobile/email 允许为空，空值永远不匹配（防止空串撞空库）。</li>
 * </ul>
 */
public final class ForgotPasswordRules {

    private ForgotPasswordRules() {
    }

    /** 是否提供了至少一种验证凭据（手机号/邮箱任一非空白）。 */
    public static boolean hasVerifier(String mobile, String email) {
        return hasText(mobile) || hasText(email);
    }

    /**
     * 单个凭据匹配判定：两端均非空白且去除首尾空白后相等。
     *
     * @param stored 库中记录（可为 null）
     * @param input  用户输入（可为 null）
     */
    public static boolean matches(String stored, String input) {
        return hasText(stored) && hasText(input) && stored.trim().equals(input.trim());
    }

    /**
     * 核验总判定：任一凭据与库中记录匹配即通过。
     *
     * @param storedMobile 库中手机号
     * @param storedEmail  库中邮箱
     * @param inputMobile  用户输入手机号
     * @param inputEmail   用户输入邮箱
     * @return true=核验通过；false=未提供匹配凭据（含库中两字段均为空的情形）
     */
    public static boolean verified(String storedMobile, String storedEmail,
                                   String inputMobile, String inputEmail) {
        return matches(storedMobile, inputMobile) || matches(storedEmail, inputEmail);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
