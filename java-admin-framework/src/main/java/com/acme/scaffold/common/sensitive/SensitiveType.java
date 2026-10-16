package com.acme.scaffold.common.sensitive;

/**
 * 敏感数据分类：驱动 {@link SensitiveMasker#mask(String, SensitiveType)} 选择脱敏策略。
 *
 * <p><b>用途</b>：把「某字段属于哪类敏感数据」与「脱敏算法」解耦。调用方（视图工厂、导出任务等）
 * 只需声明类型，具体掩码规则集中在 {@link SensitiveMasker} 中，避免各处硬编码正则造成口径漂移。</p>
 *
 * <p><b>安全默认</b>：接口对外返回时一律按本类型脱敏，仅当调用方显式持有对应「明文可见」权限
 * （如 {@code system:audit:sensitive} / {@code system:login-log:sensitive}）时才回传原文。</p>
 */
public enum SensitiveType {

    /** 中国大陆手机号（保留前 3 后 4）。 */
    MOBILE("手机号"),

    /** 身份证号（保留后 4 位）。 */
    ID_CARD("身份证号"),

    /** 银行卡号（保留后 4 位）。 */
    BANK_CARD("银行卡号"),

    /** 邮箱（保留首字符与域名）。 */
    EMAIL("邮箱"),

    /** 姓名（保留首字符）。 */
    NAME("姓名"),

    /** 联系地址（保留前 6 字符，通常到区/县一级）。 */
    ADDRESS("联系地址"),

    /** IP 地址（IPv4 保留前两段；非 IPv4 整体掩码）。 */
    IP("IP 地址"),

    /** 不定结构的凭证 / 敏感自由文本（按键名与值形态双策略抹除，见 {@link SensitiveMasker#maskSummary}）。 */
    CREDENTIAL_TEXT("凭证/敏感文本"),

    /** 无需脱敏（显式声明，便于在同一批字段中区分）。 */
    NONE("不脱敏");

    private final String label;

    SensitiveType(String label) {
        this.label = label;
    }

    /** 中文展示名（用于审计/日志说明与测试断言，不参与业务判断）。 */
    public String label() {
        return label;
    }
}
