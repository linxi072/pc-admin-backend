package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段级数据权限资源注册表测试：校验 {@link ColumnPermissionResourceRegistry}
 * 能扫描 {@code @ColumnPermission} 方法、归集资源与字段、按字段名去重。
 */
class ColumnPermissionResourceRegistryTest {

    static class OrderService {
        @ColumnPermission(resourceCode = "system:order", name = "订单管理",
                fields = {
                    @ColumnField(name = "buyerPhone", label = "买家手机号", sensitiveType = SensitiveType.MOBILE),
                    @ColumnField(name = "buyerAddress", label = "收货地址", strategy = ColumnStrategy.HIDE)
                })
        public Object detail() {
            return null;
        }

        @ColumnPermission(resourceCode = "system:order", name = "订单管理",
                fields = {
                    @ColumnField(name = "buyerPhone", label = "买家手机号（重复）", sensitiveType = SensitiveType.MOBILE),
                    @ColumnField(name = "idCard", label = "身份证", sensitiveType = SensitiveType.ID_CARD)
                })
        public Object list() {
            return null;
        }
    }

    @Test
    void registerFromClass_discoversAndMergesFields() {
        ColumnPermissionResourceRegistry registry = new ColumnPermissionResourceRegistry();
        registry.registerFromClass(OrderService.class);

        assertTrue(registry.contains("system:order"));
        List<ColumnPermissionResourceView> views = registry.listResources();
        assertEquals(1, views.size());

        ColumnPermissionResourceView view = views.get(0);
        assertEquals("system:order", view.resourceCode());
        assertEquals("订单管理", view.resourceName());

        // 跨方法按字段名去重：buyerPhone 在两方法均出现只保留一条；buyerAddress / idCard 各一条
        assertEquals(3, view.fields().size());
        assertEquals(ColumnStrategy.HIDE, fieldOf(view, "buyerAddress").strategy());
        // buyerPhone 保留首次出现（detail 方法）的 MASK/MOBILE，重复声明被忽略
        assertEquals(SensitiveType.MOBILE, fieldOf(view, "buyerPhone").sensitiveType());
        assertEquals(SensitiveType.ID_CARD, fieldOf(view, "idCard").sensitiveType());
    }

    @Test
    void fieldsOf_returnsEmptyForUnknownResource() {
        ColumnPermissionResourceRegistry registry = new ColumnPermissionResourceRegistry();
        assertTrue(registry.fieldsOf("nope").isEmpty());
        assertFalse(registry.contains("nope"));
    }

    private static ColumnPermissionFieldView fieldOf(ColumnPermissionResourceView view, String name) {
        return view.fields().stream().filter(f -> f.field().equals(name)).findFirst().orElseThrow();
    }
}
