package com.acme.scaffold.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描外部 jar 的插件清单 {@code META-INF/scaffold-extensions/extensions.json}，
 * 产出轻量 {@link PluginManifest}（仅含元数据，不含实例）。
 */
public final class ExtensionScanner {

    public static final String MANIFEST_RESOURCE = "META-INF/scaffold-extensions/extensions.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExtensionScanner() {
    }

    public static PluginManifest scan(ClassLoader classLoader) throws IOException {
        try (InputStream in = classLoader.getResourceAsStream(MANIFEST_RESOURCE)) {
            if (in == null) {
                throw new IllegalArgumentException("插件 jar 缺失清单资源: " + MANIFEST_RESOURCE);
            }
            return MAPPER.readValue(in, PluginManifest.class);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PluginManifest {
        public String id;
        public String name;
        public String version;
        public String contributor;
        public List<ExtensionEntry> extensions = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtensionEntry {
        public String name;
        public String version;
        public String description;
        public Integer order;
        public String className;
    }
}
