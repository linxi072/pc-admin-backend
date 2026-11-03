package com.acme.scaffold.common.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DefaultExtensionRegistryTest {

    interface SamplePoint extends ExtensionPoint {
        String tag();
    }

    /** 更具体的扩展点，仅 StringExtension 实现，用于验证按点过滤。 */
    interface StringPoint extends SamplePoint {
    }

    static class StringExtension implements StringPoint {
        final String label;

        StringExtension(String label) {
            this.label = label;
        }

        @Override
        public String tag() {
            return label;
        }
    }

    static class NumberExtension implements SamplePoint {
        @Override
        public String tag() {
            return "num";
        }
    }

    @Test
    void refreshWithoutContextDoesNotThrow() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.refresh(); // applicationContext 为 null，应安全返回
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void registerAndGetFiltersByExtensionPointAndOrdersByDescriptorOrder() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(new ExtensionDescriptor("a", "A", "1.0", "", 10, StringExtension.class, new StringExtension("a")));
        registry.register(new ExtensionDescriptor("b", "B", "1.0", "", 5, NumberExtension.class, new NumberExtension()));
        registry.register(new ExtensionDescriptor("c", "C", "1.0", "", 1, StringExtension.class, new StringExtension("c")));

        // 按更具体的扩展点 StringPoint 过滤：仅两个 StringExtension
        List<StringPoint> strings = registry.getExtensions(StringPoint.class);
        assertThat(strings).hasSize(2);
        assertThat(strings).extracting(StringPoint::tag).containsExactly("c", "a");

        // 按父扩展点 SamplePoint 过滤：三个都命中
        assertThat(registry.getExtensions(SamplePoint.class)).hasSize(3);

        // 非接口类型返回空
        assertThat(registry.getExtensions(StringExtension.class)).isEmpty();
    }

    @Test
    void getExtensionsReturnsEmptyForNonInterfaceOrUnknownPoint() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(new ExtensionDescriptor("a", "A", "1.0", "", 0, String.class, "x"));

        assertThat(registry.getExtensions(String.class)).isEmpty();
        assertThat(registry.getExtensions(Runnable.class)).isEmpty();
    }

    @Test
    void descriptorLookupAndAll() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        ExtensionDescriptor d = new ExtensionDescriptor("b", "B", "1.0", "desc", 0, NumberExtension.class, new NumberExtension());
        registry.register(d);

        assertThat(registry.all()).containsExactly(d);
        assertThat(registry.getDescriptor("b")).isSameAs(d);
        assertThat(registry.getDescriptor("missing")).isNull();
    }

    @Test
    void supportsReportsAssignability() {
        ExtensionDescriptor d = new ExtensionDescriptor("a", "A", "1.0", "", 0, StringExtension.class, new StringExtension("a"));
        assertThat(d.supports(SamplePoint.class)).isTrue();
        assertThat(d.supports(NumberExtension.class)).isFalse();
    }
}
