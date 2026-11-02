package com.acme.scaffold.security.permission;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataScopeResult} 单元测试：显式区分「不限制 / 仅本人 / 机构集合」，避免隐式空集合约定。
 */
class DataScopeResultTest {

    @Test
    void allIsUnrestricted() {
        DataScopeResult r = DataScopeResult.all();
        assertTrue(r.unrestricted());
        assertFalse(r.selfOnly());
        assertTrue(r.orgIds().isEmpty());
    }

    @Test
    void selfIsSelfOnly() {
        DataScopeResult r = DataScopeResult.self();
        assertTrue(r.selfOnly());
        assertFalse(r.unrestricted());
        assertTrue(r.orgIds().isEmpty());
    }

    @Test
    void ofOrgsCustom() {
        DataScopeResult r = DataScopeResult.ofOrgs(Set.of(1L, 2L));
        assertEquals(DataScopeType.CUSTOM, r.type());
        assertEquals(Set.of(1L, 2L), r.orgIds());
        assertFalse(r.unrestricted());
    }

    @Test
    void ofOrgsEmptyDegradesToAll() {
        DataScopeResult r = DataScopeResult.ofOrgs(Set.of());
        assertTrue(r.unrestricted());
    }
}
