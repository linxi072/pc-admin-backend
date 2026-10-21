package com.acme.scaffold.security.captcha;

import java.util.List;
import java.util.Random;

/**
 * 验证码运算题纯函数规则：加 / 减 / 乘 / 除四则运算的出题与计分。
 *
 * <p>与 Spring 及存储完全解耦，便于离线单测。约束（保证题目「对人类友好」）：
 * <ul>
 *   <li>加法：a、b ∈ [1, addMax]；</li>
 *   <li>减法：a、b ∈ [1, addMax]，无序生成后交换保证 a ≥ b，答案非负；</li>
 *   <li>乘法：a、b ∈ [1, mulMax]，独立上界避免乘积过大；</li>
 *   <li>除法：先定除数 b ∈ [2, mulMax] 与商 q ∈ [1, mulMax]，被除数 a = b × q，
 *       保证整除且答案为整数，除数 ≥ 2 避免「÷ 1」无意义题。</li>
 * </ul>
 * 随机源由调用方注入（{@link java.security.SecureRandom}），单测可传确定性 {@link Random}。
 */
public final class ArithmeticRules {

    private ArithmeticRules() {
    }

    /** 运算符：符号使用 × ÷ 展示，避免字母 x 与乘号混淆。 */
    public enum Operator {
        ADD("+"),
        SUB("-"),
        MUL("×"),
        DIV("÷");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        /** 按符号反查（容错 trim 与大小写），无匹配返回 null。 */
        public static Operator fromSymbol(String symbol) {
            if (symbol == null) {
                return null;
            }
            String s = symbol.trim();
            for (Operator op : values()) {
                if (op.symbol.equals(s)) {
                    return op;
                }
            }
            return null;
        }
    }

    /** 一道运算题：操作数、运算符、标准答案与展示问题（如 {@code "7 × 8 = ?"}）。 */
    public record Problem(int a, int b, Operator op, int answer, String question) {
    }

    /**
     * 随机抽取一道题。
     *
     * @param random  随机源（生产传 SecureRandom，测试可传种子化 Random）
     * @param addMax  加法 / 减法操作数上界（∈ [1, addMax]）
     * @param mulMax  乘法 / 除法操作数上界（∈ [1, mulMax]；除法除数 ∈ [2, mulMax]）
     * @param allowed 允许的运算符集合；null 或空表示四则全集
     */
    public static Problem draw(Random random, int addMax, int mulMax, List<Operator> allowed) {
        List<Operator> ops = (allowed == null || allowed.isEmpty())
                ? List.of(Operator.values())
                : List.copyOf(allowed);
        Operator op = ops.get(random.nextInt(ops.size()));
        int a;
        int b;
        int answer;
        switch (op) {
            case ADD -> {
                a = rand(random, addMax);
                b = rand(random, addMax);
                answer = a + b;
            }
            case SUB -> {
                a = rand(random, addMax);
                b = rand(random, addMax);
                if (a < b) {
                    int t = a;
                    a = b;
                    b = t;
                }
                answer = a - b;
            }
            case MUL -> {
                a = rand(random, mulMax);
                b = rand(random, mulMax);
                answer = a * b;
            }
            case DIV -> {
                // 除数 ∈ [2, mulMax]；mulMax < 2 时退化为固定除数 2，避免 nextInt 非正参
                b = mulMax > 2 ? random.nextInt(mulMax - 1) + 2 : 2;
                int quotient = rand(random, mulMax); // 商 ∈ [1, mulMax]
                a = b * quotient;
                answer = quotient;
            }
            default -> throw new IllegalStateException("未支持的运算符: " + op);
        }
        return new Problem(a, b, op, answer, a + " " + op.symbol() + " " + b + " = ?");
    }

    /** [1, max] 均匀整数。 */
    private static int rand(Random random, int max) {
        return random.nextInt(max) + 1;
    }
}
