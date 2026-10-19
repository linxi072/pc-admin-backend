package com.acme.scaffold.common.i18n;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Validator;

/**
 * Bean Validation 消息键化装配：把约束注解的 {@code message} 从硬编码文本改为资源束键，
 * 由 Spring {@link MessageSource} 按当前 {@link java.util.Locale} 插值。
 *
 * <p>工作机制：
 * <ul>
 *   <li>DTO 注解以 {@code message = "{validation.<键>}"}（花括号键，触发 Hibernate 资源束查找）形式引用，键值存放于
 *       {@code i18n/validation[_locale].properties}（见 {@link I18nConfig} 的资源束声明）；</li>
 *   <li>{@link LocalValidatorFactoryBean#setValidationMessageSource} 将插值器委托给
 *       MessageSource：键命中时返回本地化文本，缺失时回退为键本身（不抛异常，前端可据此
 *       识别漏配的键）；</li>
 *   <li>插值发生在 {@code ConstraintViolation#getMessage()}，因此
 *       {@code GlobalExceptionHandler} 的 {@code getDefaultMessage()} 天然输出本地化消息，
 *       异常处理零改动。</li>
 * </ul>
 *
 * <p>与 Spring Boot {@code ValidationAutoConfiguration} 的互斥：两者均
 * {@code @ConditionalOnMissingBean(Validator.class)}，本 Bean 优先注册，自动配置退让；
 * 测试或上层覆盖 {@link Validator} 时本 Bean 同样退让（不强行抢注）。
 *
 * <p>{@code @Role(ROLE_INFRASTRUCTURE)}：标注为基础设施 Bean，避免被组件扫描误认为业务组件。
 */
@Configuration
public class ValidationMessageConfig {

    /**
     * 声明本地化插值的 {@link Validator}。
     *
     * <p>声明为 {@code static} 工厂：避免与其它 {@code @Configuration} 形成
     * 「配置类 → 自身」构造循环（同 {@link I18nConfig} 的 interceptor 工厂）。
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(Validator.class)
    public static LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }
}
