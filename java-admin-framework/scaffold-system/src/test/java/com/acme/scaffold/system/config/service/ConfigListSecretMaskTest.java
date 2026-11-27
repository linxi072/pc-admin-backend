package com.acme.scaffold.system.config.service;

import static com.acme.scaffold.common.sensitive.SensitiveConfigKeys.MASKED_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import com.acme.scaffold.system.config.domain.ConfigGroupView;
import com.acme.scaffold.system.config.domain.ConfigView;
import com.acme.scaffold.system.config.domain.model.SysConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 系统变量管理端脱敏回归：Webhook 密钥不得回显，内部业务读取仍拿原文。 */
class ConfigListSecretMaskTest {

    private static final String WEBHOOK = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=super-secret";

    @Test
    void listForDisplayShouldMaskWebhookAndKeepPlainValue() {
        ConfigService service = service(
                config("notification.wecom.webhook-url", WEBHOOK, WEBHOOK),
                config("app.feature.enabled", "true", "false"));

        List<ConfigView> views = service.listForDisplay();

        assertEquals(MASKED_VALUE, views.get(0).configValue());
        assertEquals(MASKED_VALUE, views.get(0).defaultValue());
        assertEquals("true", views.get(1).configValue());
        assertEquals("false", views.get(1).defaultValue());
    }

    @Test
    void getByKeyForDisplayShouldMaskButInternalReadShouldStayRaw() {
        ConfigService service = service(config("notification.dingtalk.webhook-url", WEBHOOK, null));

        assertEquals(MASKED_VALUE,
                service.getByKeyForDisplay("notification.dingtalk.webhook-url").configValue());
        assertEquals(WEBHOOK, service.getByKey("notification.dingtalk.webhook-url").configValue());
    }

    @Test
    void groupedDisplayShouldAlsoMaskNestedItems() {
        ConfigService service = service(config("notification.wecom.webhook-url", WEBHOOK, null));

        List<ConfigGroupView> groups = service.listGroupedForDisplay();

        assertEquals(1, groups.size());
        assertEquals(MASKED_VALUE, groups.get(0).items().get(0).configValue());
    }

    @Test
    void nonSensitiveNullValueShouldNotThrow() {
        ConfigService service = service(config("app.feature.enabled", null, null));

        assertNull(service.listForDisplay().get(0).configValue());
    }

    private static ConfigService service(SysConfig... configs) {
        return new ConfigService(new DSLContext(
                new FakeMapper(Arrays.stream(configs).map(ConfigListSecretMaskTest::toRow).toList())));
    }

    private static SysConfig config(String key, String value, String defaultValue) {
        SysConfig c = new SysConfig();
        c.setId(1L);
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setDefaultValue(defaultValue);
        c.setConfigType("STRING");
        c.setStatus("ACTIVE");
        c.setEditable(1);
        return c;
    }

    private static Map<String, Object> toRow(SysConfig c) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", c.getId());
        row.put("tenant_id", 0L);
        row.put("config_key", c.getConfigKey());
        row.put("config_name", c.getConfigName());
        row.put("config_value", c.getConfigValue());
        row.put("default_value", c.getDefaultValue());
        row.put("config_type", c.getConfigType());
        row.put("remark", c.getRemark());
        row.put("editable", c.getEditable());
        row.put("status", c.getStatus());
        row.put("deleted", 0);
        return row;
    }

    private static final class FakeMapper implements DynamicMapper {

        private final List<Map<String, Object>> rows;

        private FakeMapper(List<Map<String, Object>> rows) {
            this.rows = new ArrayList<>(rows);
        }

        @Override
        public List<Map<String, Object>> select(DynamicStatement statement) {
            return rows;
        }

        @Override
        public Map<String, Object> selectOne(DynamicStatement statement) {
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public int update(DynamicStatement statement) {
            return 1;
        }

        @Override
        public int insert(DynamicStatement statement) {
            return 1;
        }

        @Override
        public int delete(DynamicStatement statement) {
            return 1;
        }
    }
}
