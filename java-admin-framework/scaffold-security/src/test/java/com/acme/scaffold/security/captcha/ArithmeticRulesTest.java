package com.acme.scaffold.security.captcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 验证码出题纯规则单测（缺陷修复回归）：
 * 覆盖四则运算的正确性、非法上界归一与极端上界的溢出防护。
 */
class ArithmeticRulesTest {

    @Test
    void addAndSubShouldAlwaysBeNonNegativeAndCorrect() {
        Random r = new Random(42);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem add = ArithmeticRules.draw(r, 20, 10, List.of(ArithmeticRules.Operator.ADD));
            assertEquals(add.a() + add.b(), add.answer());
            ArithmeticRules.Problem sub = ArithmeticRules.draw(r, 20, 10, List.of(ArithmeticRules.Operator.SUB));
            assertTrue(sub.a() >= sub.b(), "减法保证被减数不小于减数");
            assertEquals(sub.a() - sub.b(), sub.answer());
            assertTrue(sub.answer() >= 0);
        }
    }

    @Test
    void mulAndDivShouldAlwaysBeCorrect() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem mul = ArithmeticRules.draw(r, 20, 10, List.of(ArithmeticRules.Operator.MUL));
            assertEquals(mul.a() * mul.b(), mul.answer());
            ArithmeticRules.Problem div = ArithmeticRules.draw(r, 20, 10, List.of(ArithmeticRules.Operator.DIV));
            assertEquals(div.b() * div.answer(), div.a(), "除法保证整除");
            assertTrue(div.b() >= 2, "除数不小于 2");
        }
    }

    @Test
    void illegalBoundsShouldBeNormalizedNotThrow() {
        Random r = new Random(1);
        // 回归缺陷：addMax/mulMax<=0 时 random.nextInt(非正参数) 抛 IllegalArgumentException。
        for (int bad : new int[]{0, -1, Integer.MIN_VALUE}) {
            ArithmeticRules.Problem p = ArithmeticRules.draw(r, bad, bad, null);
            assertNotNull(p);
            assertTrue(p.a() >= 1 && p.b() >= 1, "非法上界归一为 1，操作数仍为正");
        }
    }

    @Test
    void extremeBoundsShouldNotOverflowToNegative() {
        // 回归缺陷：mulMax 极大时 a*b（乘）与 b*quotient（除）int 溢出为负数。
        // 现在上界被夹紧到溢出安全值，答案恒为正且算式自洽。
        Random r = new Random(2026);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem mul = ArithmeticRules.draw(
                    r, Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(ArithmeticRules.Operator.MUL));
            assertEquals((long) mul.a() * mul.b(), (long) mul.answer(), "乘法结果用 long 校验不溢出");
            assertTrue(mul.answer() > 0, "乘法答案不得为负");
            assertTrue(mul.question().contains("×"));

            ArithmeticRules.Problem div = ArithmeticRules.draw(
                    r, Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(ArithmeticRules.Operator.DIV));
            assertEquals((long) div.b() * div.answer(), (long) div.a(), "除法被除数不溢出");
            assertTrue(div.a() > 0, "被除数不得为负");
        }
    }

    @Test
    void fromSymbolShouldRoundTrip() {
        for (ArithmeticRules.Operator op : ArithmeticRules.Operator.values()) {
            assertEquals(op, ArithmeticRules.Operator.fromSymbol(" " + op.symbol() + " "));
        }
        assertEquals(null, ArithmeticRules.Operator.fromSymbol("x"));
    }
}
