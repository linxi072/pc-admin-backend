package com.acme.scaffold.security.captcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * CaptchaService 单测：覆盖容量上限、过期清理、一次性校验与并发生成。
 * 全部离线运行，不依赖 Spring 上下文。
 */
class CaptchaServiceTest {

    private static CaptchaProperties props(int expireSeconds, int maxPending) {
        CaptchaProperties p = new CaptchaProperties();
        p.setExpireSeconds(expireSeconds);
        p.setMaxPending(maxPending);
        p.setOperators(java.util.List.of(ArithmeticRules.Operator.ADD));
        return p;
    }

    @Test
    void generateShouldStayWithinMaxPending() {
        CaptchaProperties props = props(120, 3);
        CaptchaService service = new CaptchaService(props);
        for (int i = 0; i < 20; i++) {
            service.generate();
        }
        assertEquals(3, service.pendingCount(), "未消费验证码数量不得超过配置上限");
    }

    @Test
    void verifySuccessShouldDestroyToken() {
        CaptchaService service = new CaptchaService(props(120, 16));
        CaptchaService.Captcha c = service.generate();
        assertTrue(service.verify(c.token(), String.valueOf(c.answer())));
        assertEquals(0, service.pendingCount());
        assertFalse(service.verify(c.token(), String.valueOf(c.answer())), "同一 token 不得二次校验成功");
    }

    @Test
    void verifyWrongAnswerShouldAlsoDestroyToken() {
        CaptchaService service = new CaptchaService(props(120, 16));
        CaptchaService.Captcha c = service.generate();
        assertFalse(service.verify(c.token(), "wrong-answer"));
        assertEquals(0, service.pendingCount(), "失败校验同样销毁 token，防止暴力枚举");
    }

    @Test
    void expiredEntryShouldBeCleanedUpOnNextGenerate() {
        CaptchaService service = new CaptchaService(props(1, 16));
        service.generate();
        assertEquals(1, service.pendingCount());
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        service.generate();
        // 过期条目在生成新验证码时被清理，加上新生成的 1 条，总数应为 1
        assertEquals(1, service.pendingCount(), "过期条目应在生成时被惰性清理");
    }

    @Test
    void concurrentGenerateShouldKeepUniqueTokensAndRespectCap() throws InterruptedException {
        CaptchaService service = new CaptchaService(props(120, 50));
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> tokens = java.util.Collections.synchronizedSet(new HashSet<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 5; j++) {
                        tokens.add(service.generate().token());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(tokens.size(), new HashSet<>(tokens).size(), "并发生成的 token 必须唯一");
        assertTrue(service.pendingCount() <= 50, "并发生成不得突破容量上限");
    }
}
