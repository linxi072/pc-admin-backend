package com.acme.scaffold.common.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验消息键一致性守护：防止后续新增 DTO 引用键时漏配资源束或只配单语。
 *
 * <p>规则：
 * <ul>
 *   <li>代码中引用的每个 {@code validation.*} 键必须在 {@code validation_zh_CN.properties}
 *       与 {@code validation_en_US.properties} <b>同时</b>有定义；</li>
 *   <li>两个语言束的键集必须一致（不允许单语缺漏）；</li>
 *   <li>资源束值不得为空（空消息会让前端收到空白提示）。</li>
 * </ul>
 *
 * <p>扫描基于源码文本（{@code src/main/java} 与 {@code src/main/resources/i18n}），
 * surefire 工作目录即项目根，离线可运行。
 */
class ValidationMessageBundleConsistencyTest {

    private static final Pattern KEY_REF = Pattern.compile("message\\s*=\\s*\"\\{(validation\\.[A-Za-z0-9.]+)\\}\"");

    private static Set<String> referencedKeys() {
        Path root = Path.of("src/main/java");
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = KEY_REF.matcher(Files.readString(p));
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return keys;
    }

    private static Set<String> bundleKeys(String fileName) {
        Path p = Path.of("src/main/resources/i18n", fileName);
        Set<String> keys = new TreeSet<>();
        try (Stream<String> lines = Files.lines(p)) {
            lines.map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                    .forEach(l -> keys.add(l.substring(0, l.indexOf('=')).trim()));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return keys;
    }

    @Test
    @DisplayName("代码引用的键在 zh_CN / en_US 两个资源束中均有定义")
    void referencedKeysDefinedInBothBundles() {
        Set<String> used = referencedKeys();
        Set<String> zh = bundleKeys("validation_zh_CN.properties");
        Set<String> en = bundleKeys("validation_en_US.properties");
        assertTrue(used.size() >= 30, "预期至少 30 个键被引用，实际 " + used.size());
        Set<String> missingZh = new TreeSet<>(used);
        missingZh.removeAll(zh);
        Set<String> missingEn = new TreeSet<>(used);
        missingEn.removeAll(en);
        assertTrue(missingZh.isEmpty(), "zh_CN 资源束缺少键: " + missingZh);
        assertTrue(missingEn.isEmpty(), "en_US 资源束缺少键: " + missingEn);
    }

    @Test
    @DisplayName("zh_CN 与 en_US 资源束键集一致，且值均非空")
    void bundlesAreSymmetricAndNonEmpty() {
        assertEquals(bundleKeys("validation_zh_CN.properties"), bundleKeys("validation_en_US.properties"),
                "两个语言束键集不一致");
        for (String fileName : List.of("validation.properties", "validation_zh_CN.properties",
                "validation_en_US.properties")) {
            Path p = Path.of("src/main/resources/i18n", fileName);
            try (Stream<String> lines = Files.lines(p)) {
                List<String> blanks = lines.map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                        .filter(l -> l.substring(l.indexOf('=') + 1).isBlank())
                        .toList();
                assertTrue(blanks.isEmpty(), fileName + " 存在空值键: " + blanks);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
