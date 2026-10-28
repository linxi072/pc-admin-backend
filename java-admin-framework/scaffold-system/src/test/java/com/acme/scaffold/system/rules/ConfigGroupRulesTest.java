package com.acme.scaffold.system.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ConfigGroupRules} 测试：按 {@code config_key} 命名空间前缀分组，未知分组回退。
 */
class ConfigGroupRulesTest {

    @Test
    void deriveGroupTakesNamespacePrefix() {
        assertEquals("security", ConfigGroupRules.deriveGroup("security.jwt.issuer"));
        assertEquals("notification", ConfigGroupRules.deriveGroup("notification.wecom.enabled"));
        assertEquals("cache", ConfigGroupRules.deriveGroup("cache.ttl"));
    }

    @Test
    void deriveGroupNoDotReturnsOther() {
        assertEquals("other", ConfigGroupRules.deriveGroup("nodot"));
        assertEquals("other", ConfigGroupRules.deriveGroup(null));
        assertEquals("other", ConfigGroupRules.deriveGroup(""));
        assertEquals("other", ConfigGroupRules.deriveGroup("   "));
    }

    @Test
    void groupLabelMapsKnownAndFallsBack() {
        assertEquals("安全", ConfigGroupRules.groupLabel("security"));
        assertEquals("通知", ConfigGroupRules.groupLabel("notification"));
        assertEquals("导入导出", ConfigGroupRules.groupLabel("import"));
        assertEquals("密钥源", ConfigGroupRules.groupLabel("secret"));
        assertEquals("限流", ConfigGroupRules.groupLabel("ratelimit"));
        assertEquals("unknownprefix", ConfigGroupRules.groupLabel("unknownprefix"));
        assertEquals("other", ConfigGroupRules.groupLabel("other"));
    }
}
