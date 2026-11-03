package com.acme.scaffold.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class ExtensionScannerTest {

    @Test
    void scansManifestFromClassLoader() throws IOException {
        ExtensionScanner.PluginManifest manifest = ExtensionScanner.scan(getClass().getClassLoader());

        assertThat(manifest.id).isEqualTo("demo-plugin");
        assertThat(manifest.extensions).hasSize(1);
        ExtensionScanner.ExtensionEntry entry = manifest.extensions.get(0);
        assertThat(entry.className).isEqualTo("com.acme.scaffold.plugin.fixture.DemoMenuExtension");
        assertThat(entry.order).isEqualTo(0);
    }

    @Test
    void missingManifestResourceThrows() {
        ClassLoader empty = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public java.net.URL getResource(String name) {
                return null;
            }

            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return null;
            }
        };
        assertThatThrownBy(() -> ExtensionScanner.scan(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失清单资源");
    }
}
