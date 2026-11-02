package com.acme.scaffold.security.captcha;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ArithmeticRules 纯函数单测：四则运算出题约束（非负 / 整除 / 范围 / 运算符可配置）。
 */
class ArithmeticRulesTest {

    private static final int ADD_MAX = 20;
    private static final int MUL_MAX = 10;

    @Test
    void draw_add_staysWithinBound() {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem p = ArithmeticRules.draw(random, ADD_MAX, MUL_MAX, List.of(ArithmeticRules.Operator.ADD));
            assertEquals(ArithmeticRules.Operator.ADD, p.op());
            assertTrue(p.a() >= 1 && p.a() <= ADD_MAX, "a 越界: " + p.a());
            assertTrue(p.b() >= 1 && p.b() <= ADD_MAX, "b 越界: " + p.b());
            assertEquals(p.a() + p.b(), p.answer());
            assertEquals(p.a() + " + " + p.b() + " = ?", p.question());
        }
    }

    @Test
    void draw_sub_answerNeverNegative() {
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem p = ArithmeticRules.draw(random, ADD_MAX, MUL_MAX, List.of(ArithmeticRules.Operator.SUB));
            assertTrue(p.a() >= p.b(), "被减数小于减数: " + p);
            assertTrue(p.answer() >= 0, "答案为负: " + p.answer());
            assertEquals(p.a() - p.b(), p.answer());
            assertTrue(p.question().contains("-"), p.question());
        }
    }

    @Test
    void draw_mul_staysWithinMulBound() {
        Random random = new Random(99);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem p = ArithmeticRules.draw(random, ADD_MAX, MUL_MAX, List.of(ArithmeticRules.Operator.MUL));
            assertTrue(p.a() >= 1 && p.a() <= MUL_MAX, "a 越界: " + p.a());
            assertTrue(p.b() >= 1 && p.b() <= MUL_MAX, "b 越界: " + p.b());
            assertEquals(p.a() * p.b(), p.answer());
            assertTrue(p.question().contains("×"), p.question());
        }
    }

    @Test
    void draw_div_isExactDivisionWithDivisorAtLeastTwo() {
        Random random = new Random(1234);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem p = ArithmeticRules.draw(random, ADD_MAX, MUL_MAX, List.of(ArithmeticRules.Operator.DIV));
            assertTrue(p.b() >= 2 && p.b() <= MUL_MAX, "除数越界: " + p.b());
            assertEquals(0, p.a() % p.b(), "不能整除: " + p);
            assertEquals(p.a() / p.b(), p.answer());
            assertTrue(p.question().contains("÷"), p.question());
        }
    }

    @Test
    void draw_nullOrEmptyOperators_fallsBackToFullSet() {
        Random random = new Random(5);
        List<List<ArithmeticRules.Operator>> cases = new java.util.ArrayList<>();
        cases.add(null);
        cases.add(List.of());
        for (List<ArithmeticRules.Operator> ops : cases) {
            java.util.EnumSet<ArithmeticRules.Operator> seen = java.util.EnumSet.noneOf(ArithmeticRules.Operator.class);
            for (int i = 0; i < 2000; i++) {
                ArithmeticRules.Problem p = ArithmeticRules.draw(random, ADD_MAX, MUL_MAX, ops);
                assertNotNull(p.op());
                seen.add(p.op());
            }
            assertEquals(java.util.EnumSet.allOf(ArithmeticRules.Operator.class), seen,
                    "空运算符集合应回退四则全集");
        }
    }

    @Test
    void draw_restrictedOperators_onlyDrawsFromSubset() {
        Random random = new Random(2024);
        for (int i = 0; i < 500; i++) {
            ArithmeticRules.Problem p = ArithmeticRules.draw(random, ADD_MAX, MUL_MAX,
                    List.of(ArithmeticRules.Operator.MUL, ArithmeticRules.Operator.DIV));
            assertTrue(p.op() == ArithmeticRules.Operator.MUL || p.op() == ArithmeticRules.Operator.DIV,
                    "出现限定集合外运算符: " + p.op());
        }
    }

    @Test
    void operator_fromSymbol_roundTrip() {
        assertEquals(ArithmeticRules.Operator.ADD, ArithmeticRules.Operator.fromSymbol("+"));
        assertEquals(ArithmeticRules.Operator.MUL, ArithmeticRules.Operator.fromSymbol("×"));
        assertEquals(ArithmeticRules.Operator.DIV, ArithmeticRules.Operator.fromSymbol(" ÷ "));
        assertFalse(ArithmeticRules.Operator.fromSymbol("?") != null);
    }
}
