package com.acme.scaffold.common.i18n;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ValidationMessageConfig} 集成测试：在最小 Spring 上下文中装配真实的
 * {@link I18nConfig}（提供含 {@code validation} 基名的 {@code MessageSource}）与
 * {@link ValidationMessageConfig}（提供 {@code LocalValidatorFactoryBean}），验证 DTO 约束注解的
 * {@code message} 键经 MessageSource 按当前线程 {@link Locale} 插值为本地化文本。
 *
 * <p>为何用 {@link ApplicationContextRunner} 而非手动 {@code new LocalValidatorFactoryBean() +
 * afterPropertiesSet()}：后者绕过了 Spring 对 {@code LocalValidatorFactoryBean} 的完整装配——
 * {@code setValidationMessageSource} 的 MessageSource 委托依赖 ApplicationContext 生命周期
 * （Spring 在 {@code setApplicationContext} 阶段注入 MessageSource 并据此构建插值器），手动构造
 * 无法稳定复现生产行为，故以最小上下文锁住真实装配。</p>
 */
class ValidationMessageInterpolatorTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(I18nConfig.class, ValidationMessageConfig.class);

    @AfterEach
    void clear() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** 测试载体：键引用与 UpdateProfileRequest 同款（mobile 走 pattern，title 走 size）。 */
    record ProbeRequest(
            @NotBlank @Size(max = 200, message = "{validation.title.size}") String title,
            @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "{validation.profile.mobile.pattern}") String mobile) {
    }

    private Set<ConstraintViolation<ProbeRequest>> violate(Validator validator, String title, String mobile) {
        return validator.validate(new ProbeRequest(title, mobile));
    }

    @Test
    void interpolatesChineseUnderThreadLocale() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        // title 传超长串以触发 @Size 约束（空串只会触发 @NotBlank 的默认消息，无法验证键化）
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);
            Set<ConstraintViolation<ProbeRequest>> violations = violate(validator, "x".repeat(201), "123");
            assertEquals(2, violations.size());
            assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("标题长度不能超过200")));
            assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("手机号格式不正确")));
        });
    }

    @Test
    void interpolatesEnglishUnderThreadLocale() {
        LocaleContextHolder.setLocale(Locale.US);
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);
            Set<ConstraintViolation<ProbeRequest>> violations = violate(validator, "x".repeat(201), "123");
            assertEquals(2, violations.size());
            assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Title must not exceed 200 characters")));
            assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Invalid mobile number format")));
        });
    }

    @Test
    void fallsBackToDefaultBundleForUnknownLocale() {
        // fr_FR 无专属束 -> 回退 validation.properties（中文兜底），不抛 MissingResourceException
        LocaleContextHolder.setLocale(Locale.FRANCE);
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);
            Set<ConstraintViolation<ProbeRequest>> violations = violate(validator, "x".repeat(201), "123");
            assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("标题长度不能超过200")));
        });
    }

    @Test
    void returnsKeyItselfWhenKeyMissing() {
        // 缺失键安全降级：Hibernate 返回带花括号的原始模板（不抛异常），前端可据此识别漏配的键
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        record BadKeyRequest(@Size(max = 5, message = "{validation.no.such.key}") String value) {
        }
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);
            Set<ConstraintViolation<BadKeyRequest>> violations = validator.validate(new BadKeyRequest("toolong"));
            assertEquals(1, violations.size());
            assertEquals("{validation.no.such.key}", violations.iterator().next().getMessage());
        });
    }

    @Test
    void validatesGroupedObjectWithNestedKeys() {
        // 嵌套对象同样走本地化插值（校验传播路径）
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        record Wrapper(@Valid ProbeRequest probe) {
        }
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);
            Set<ConstraintViolation<Wrapper>> violations =
                    validator.validate(new Wrapper(new ProbeRequest("x".repeat(201), "123")));
            assertEquals(2, violations.size());
            List<String> messages = violations.stream().map(ConstraintViolation::getMessage).toList();
            assertTrue(messages.contains("标题长度不能超过200"));
            assertTrue(messages.contains("手机号格式不正确"));
        });
    }
}
