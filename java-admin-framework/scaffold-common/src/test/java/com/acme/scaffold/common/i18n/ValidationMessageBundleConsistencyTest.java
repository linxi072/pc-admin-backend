package com.acme.scaffold.common.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * <p>扫描基于源码文本与资源束文件。V1.0.3 起为多模块工程：从当前目录上溯至反应堆根
 * （含 {@code scaffold-common/pom.xml} 的目录），再扫描全部 {@code scaffold-<模块名>/src/main/java}
 * 与 {@code scaffold-<模块名>/src/main/resources/i18n}，离线可运行。
 */
class ValidationMessageBundleConsistencyTest {

    private static final Pattern KEY_REF = Pattern.compile("message\\s*=\\s*\"\\{(validation\\.[A-Za-z0-9.]+)\\}\"");

    /** 反应堆根（含 scaffold-common/pom.xml 的目录）；找不到时退化为当前目录。 */
    private static Path reactorRoot() {
        Path d = Path.of("").toAbsolutePath();
        while (d != null) {
            if (Files.isRegularFile(d.resolve("scaffold-common/pom.xml"))) {
                return d;
            }
            d = d.getParent();
        }
        return Path.of("").toAbsolutePath();
    }

    /** 反应堆根下的全部模块源码根（scaffold-<模块名>/src/main/java），按名排序。 */
    private static List<Path> moduleSrcRoots() {
        try (Stream<Path> dirs = Files.list(reactorRoot())) {
            return dirs.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("scaffold-"))
                    .map(d -> d.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Set<String> referencedKeys() {
        Set<String> keys = new TreeSet<>();
        for (Path root : moduleSrcRoots()) {
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
        }
        return keys;
    }

    private static Set<String> bundleKeys(String fileName) {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> dirs = Files.list(reactorRoot())) {
            for (Path moduleDir : dirs.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("scaffold-"))
                    .sorted().toList()) {
                Path p = moduleDir.resolve("src/main/resources/i18n").resolve(fileName);
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                try (Stream<String> lines = Files.lines(p)) {
                    lines.map(String::trim)
                            .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                            .forEach(l -> keys.add(l.substring(0, l.indexOf('=')).trim()));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
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
            List<String> blanks = new ArrayList<>();
            for (Path p : i18nBundlePaths(fileName)) {
                try (Stream<String> lines = Files.lines(p)) {
                    blanks.addAll(lines.map(String::trim)
                            .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                            .filter(l -> l.substring(l.indexOf('=') + 1).isBlank())
                            .toList());
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
            assertTrue(blanks.isEmpty(), fileName + " 存在空值键: " + blanks);
        }
    }

    /** 资源束文件在反应堆根下各模块中的全部副本（i18n 资源现位于 scaffold-common）。 */
    private static List<Path> i18nBundlePaths(String fileName) {
        try (Stream<Path> dirs = Files.list(reactorRoot())) {
            return dirs.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("scaffold-"))
                    .map(d -> d.resolve("src/main/resources/i18n").resolve(fileName))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
