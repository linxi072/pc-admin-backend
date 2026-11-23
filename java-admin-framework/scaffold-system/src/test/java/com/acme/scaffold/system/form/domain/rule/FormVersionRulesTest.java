package com.acme.scaffold.system.form.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 表单版本规则单测（E8）：版本号分配、回滚判定、Schema diff 与等价判定。 */
class FormVersionRulesTest {

    @Test
    void shouldAllocateNextVersion() {
        assertEquals(1, FormVersionRules.nextVersionNo(null));
        assertEquals(1, FormVersionRules.nextVersionNo(List.of()));
        assertEquals(4, FormVersionRules.nextVersionNo(List.of(1, 3, 2)));
        assertEquals(1, FormVersionRules.nextVersionNo(java.util.Arrays.asList(null, 0)));
    }

    @Test
    void shouldDecideRollback() {
        assertTrue(FormVersionRules.canRollback(3, 1));
        assertFalse(FormVersionRules.canRollback(3, 3));
        assertFalse(FormVersionRules.canRollback(null, 1));
        assertFalse(FormVersionRules.canRollback(3, null));
    }

    @Test
    void shouldDiffFieldKeys() {
        String a = "{\"version\":1,\"fields\":[{\"key\":\"name\"},{\"key\":\"age\"}]}";
        String b = "{\"version\":1,\"fields\":[{\"key\":\"name\"},{\"key\":\"email\"}]}";
        List<String> diff = FormVersionRules.diffFields(a, b);
        assertEquals(2, diff.size());
        assertTrue(diff.contains("-age"));
        assertTrue(diff.contains("+email"));
        assertTrue(FormVersionRules.diffFields(a, a).isEmpty());
    }

    @Test
    void shouldDetectSameSchema() {
        String a = "{\"version\":1,\"title\":\"t\",\"fields\":[{\"key\":\"x\"}]}";
        assertTrue(FormVersionRules.sameSchema(a, a));
        assertTrue(FormVersionRules.sameSchema(
                "{\"fields\":[{\"key\":\"x\"}],\"version\":1,\"title\":\"t\"}", a));
        assertFalse(FormVersionRules.sameSchema(
                "{\"version\":2,\"title\":\"t\",\"fields\":[{\"key\":\"x\"}]}", a));
        assertFalse(FormVersionRules.sameSchema(
                "{\"version\":1,\"title\":\"t\",\"fields\":[{\"key\":\"y\"}]}", a));
        assertTrue(FormVersionRules.sameSchema(null, null));
        assertTrue(FormVersionRules.sameSchema("bad", "bad"));
    }
}
