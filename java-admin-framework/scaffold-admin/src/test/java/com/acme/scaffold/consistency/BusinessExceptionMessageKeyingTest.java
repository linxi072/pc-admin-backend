package com.acme.scaffold.consistency;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.error.ErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.idempotency.IdempotencyErrorCode;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.system.file.FileErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务异常消息键化守护（Phase D）：
 * <ul>
 *   <li>{@code ErrorCode.messageKey()} 恒为 {@code error.<CODE>}；</li>
 *   <li>所有错误码的资源束（zh_CN / en_US）键齐备、对称、值非空，且 zh_CN 与枚举中文一致；</li>
 *   <li>{@code GlobalExceptionHandler} 按请求语言解析默认构造异常的本地化文案，自定义消息原样透传；</li>
 *   <li>{@code getMessage()} 始终为中文兜底（利于日志，不受语言影响）。</li>
 * </ul>
 *
 * <p>直接构造 {@link ReloadableResourceBundleMessageSource} 读取 {@code i18n/messages*} 资源束，
 * 不依赖 Spring 上下文，保证离线可编译可运行。
 *
 * <p><b>V1.0.3 模块化说明</b>：本测试横跨 common / system 两模块的错误码枚举与资源束，
 * 属跨模块一致性守护，故置于 scaffold-admin 的 consistency 包（admin 依赖全部模块）；
 * 资源束文件改经「反应堆根发现」定位（scaffold-common/src/main/resources/i18n），
 * 不再依赖 surefire 工作目录恰为项目根。
 */
class BusinessExceptionMessageKeyingTest {

    private static final ErrorCode[] ALL = concat(
            CommonErrorCode.values(), SecurityErrorCode.values(),
            IdempotencyErrorCode.values(), FileErrorCode.values());

    private I18nService i18n() {
        ReloadableResourceBundleMessageSource src = new ReloadableResourceBundleMessageSource();
        src.setBasenames("classpath:i18n/messages", "classpath:i18n/validation");
        src.setDefaultEncoding("UTF-8");
        src.setFallbackToSystemLocale(false);
        src.setCacheSeconds(-1);
        return new I18nService(src);
    }

    private GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(i18n());
    }

    @AfterEach
    void clear() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("ErrorCode.messageKey() 恒为 error.<CODE>")
    void messageKeyDerivedFromCode() {
        for (ErrorCode ec : ALL) {
            assertEquals("error." + ec.code(), ec.messageKey(), ec.code());
        }
    }

    @Test
    @DisplayName("默认构造异常带 messageKey，自定义消息透传时 messageKey 为 null")
    void messageKeyPresence() {
        BusinessException defaultEx = new BusinessException(CommonErrorCode.UNAUTHORIZED);
        assertEquals("error.COMMON_401", defaultEx.getMessageKey());
        assertEquals("未认证或登录已失效", defaultEx.getMessage());

        BusinessException customEx = BusinessException.of(CommonErrorCode.UNAUTHORIZED, "账号 alice 已被禁用");
        assertNull(customEx.getMessageKey());
        assertEquals("账号 alice 已被禁用", customEx.getMessage());
    }

    @Test
    @DisplayName("handler 按请求语言解析默认构造异常的本地化文案")
    void handlerResolvesByLocale() {
        BusinessException ex = new BusinessException(SecurityErrorCode.BAD_CREDENTIALS);
        GlobalExceptionHandler h = handler();

        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        ResponseEntity<Result<Void>> zh = h.handleBusiness(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, zh.getStatusCode());
        assertEquals("AUTH_001", zh.getBody().code());
        assertEquals("用户名或密码错误", zh.getBody().message());

        LocaleContextHolder.setLocale(Locale.US);
        ResponseEntity<Result<Void>> en = h.handleBusiness(ex);
        assertEquals("Invalid username or password", en.getBody().message());
    }

    @Test
    @DisplayName("handler 对自定义消息原样透传，不做键化")
    void handlerPassesThroughCustomMessage() {
        BusinessException ex = BusinessException.conflict("当前版本与最新版本不一致，请刷新");
        LocaleContextHolder.setLocale(Locale.US);
        ResponseEntity<Result<Void>> resp = handler().handleBusiness(ex);
        assertEquals("当前版本与最新版本不一致，请刷新", resp.getBody().message());
    }

    @Test
    @DisplayName("资源束 zh_CN / en_US 对所有错误码键齐备、对称、非空，且 zh_CN 与枚举中文一致")
    void bundlesCoverAllErrorCodes() {
        Set<String> zh = bundleKeys("messages_zh_CN.properties");
        Set<String> en = bundleKeys("messages_en_US.properties");
        for (ErrorCode ec : ALL) {
            String key = ec.messageKey();
            assertTrue(zh.contains(key), "zh_CN 缺少键 " + key);
            assertTrue(en.contains(key), "en_US 缺少键 " + key);
            String zhVal = valueOf("messages_zh_CN.properties", key);
            String enVal = valueOf("messages_en_US.properties", key);
            assertNotNull(zhVal, "zh_CN 键 " + key + " 值缺失");
            assertNotNull(enVal, "en_US 键 " + key + " 值缺失");
            assertFalse(zhVal.isBlank(), "zh_CN 键 " + key + " 值为空");
            assertFalse(enVal.isBlank(), "en_US 键 " + key + " 值为空");
            assertEquals(ec.message(), zhVal, "zh_CN 键 " + key + " 与枚举中文不一致");
            assertFalse(enVal.equals(zhVal), "en_US 键 " + key + " 未翻译（与中文相同）");
        }
        assertEquals(zh, en, "zh_CN 与 en_US 错误码键集应对称");
    }

    // ---- helpers ----

    private static ErrorCode[] concat(ErrorCode[]... arrays) {
        List<ErrorCode> all = new ArrayList<>();
        for (ErrorCode[] a : arrays) {
            for (ErrorCode e : a) {
                all.add(e);
            }
        }
        return all.toArray(new ErrorCode[0]);
    }

    private static Set<String> bundleKeys(String fileName) {
        Set<String> keys = new TreeSet<>();
        for (Path p : i18nBundlePaths(fileName)) {
            try (var lines = Files.lines(p)) {
                lines.map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                        .forEach(l -> keys.add(l.substring(0, l.indexOf('=')).trim()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return keys;
    }

    private static String valueOf(String fileName, String key) {
        for (Path p : i18nBundlePaths(fileName)) {
            try (var lines = Files.lines(p)) {
                var found = lines.map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                        .filter(l -> l.substring(0, l.indexOf('=')).trim().equals(key))
                        .map(l -> l.substring(l.indexOf('=') + 1).trim())
                        .findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * 定位消息资源束文件。V1.0.3 起 i18n 资源位于 scaffold-common 模块，
     * surefire 工作目录为本模块（scaffold-admin），故从当前目录上溯至反应堆根
     * （含 scaffold-common/pom.xml 的目录）后再按模块 glob。
     */
    private static List<Path> i18nBundlePaths(String fileName) {
        List<Path> paths = new ArrayList<>();
        for (Path moduleDir : moduleDirs()) {
            Path p = moduleDir.resolve("src/main/resources/i18n").resolve(fileName);
            if (Files.isRegularFile(p)) {
                paths.add(p);
            }
        }
        if (paths.isEmpty()) {
            throw new IllegalStateException("未找到消息资源束 " + fileName + "（反应堆根发现失败）");
        }
        return paths;
    }

    /** 反应堆根下的全部 scaffold-* 模块目录（按名排序）。 */
    private static List<Path> moduleDirs() {
        Path root = reactorRoot();
        try (var s = Files.list(root)) {
            return s.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("scaffold-"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 从当前工作目录上溯寻找反应堆根（含 scaffold-common/pom.xml）。 */
    private static Path reactorRoot() {
        Path d = Path.of("").toAbsolutePath();
        while (d != null) {
            if (Files.isRegularFile(d.resolve("scaffold-common/pom.xml"))) {
                return d;
            }
            d = d.getParent();
        }
        throw new IllegalStateException("未找到反应堆根（scaffold-common/pom.xml）");
    }
}
