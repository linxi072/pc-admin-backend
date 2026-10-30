package com.acme.scaffold.security.captcha;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录验证码服务：生成「四则运算题」验证码（加 / 减 / 乘 / 除，运算符集合可配置），
 * token→答案 存放于内存（单次有效、带过期）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>token 为一次性：校验成功后即销毁，重放同一 token 无效；</li>
 *   <li>带过期时间（{@link CaptchaProperties#getExpireSeconds()}）；</li>
 *   <li>纯内存存储，适用于单实例；多实例部署可替换为 Redis（按 token 维度共享）；</li>
 *   <li>出题与计分委托 {@link ArithmeticRules} 纯函数：减法保证非负、除法保证整除，与 Spring 解耦便于离线单测。</li>
 * </ul>
 */
@Service
public class CaptchaService {

    private final CaptchaProperties props;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public CaptchaService(CaptchaProperties props) {
        this.props = props;
    }

    /** 生成结果：token 与问题对外暴露，answer 仅内部用于校验；operator 用于观测与日志。 */
    public record Captcha(String token, String question, int answer, ArithmeticRules.Operator operator) {
    }

    /** 对外视图：仅含 token 与问题，绝不泄露答案。 */
    public record CaptchaView(String token, String question) {
    }

    public Captcha generate() {
        ArithmeticRules.Problem p = ArithmeticRules.draw(
                random, props.getMaxNumber(), props.getMulMaxNumber(), props.getOperators());
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new Entry(p.answer(), Instant.now().plusSeconds(props.getExpireSeconds())));
        return new Captcha(token, p.question(), p.answer(), p.op());
    }

    /**
     * 校验验证码：token 缺失 / 答案缺失 / 不存在 / 已过期 / 不匹配 均返回 false。
     * 校验成功会立即销毁 token（单次有效，防止重放）。
     */
    public boolean verify(String token, String answer) {
        if (token == null || answer == null || answer.isBlank()) {
            return false;
        }
        Entry entry = store.remove(token);
        if (entry == null) {
            return false;
        }
        if (entry.expireAt().isBefore(Instant.now())) {
            return false;
        }
        try {
            return Integer.parseInt(answer.trim()) == entry.answer();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** 当前未消费验证码数量（运维/测试观测用）。 */
    public int pendingCount() {
        return store.size();
    }

    private record Entry(int answer, Instant expireAt) {
    }
}
