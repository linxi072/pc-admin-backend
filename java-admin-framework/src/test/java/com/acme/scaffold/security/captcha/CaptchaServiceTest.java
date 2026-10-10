package com.acme.scaffold.security.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaServiceTest {

    private CaptchaService newService(int expireSeconds) {
        CaptchaProperties props = new CaptchaProperties();
        props.setExpireSeconds(expireSeconds);
        props.setMaxNumber(10);
        return new CaptchaService(props);
    }

    @Test
    void generate_returnsTokenAndQuestion() {
        CaptchaService.Captcha c = newService(120).generate();
        assertNotNull(c.token());
        assertTrue(c.question().contains("+"));
        assertTrue(c.answer() >= 2);
    }

    @Test
    void verify_correctAnswer_returnsTrue() {
        CaptchaService service = newService(120);
        CaptchaService.Captcha c = service.generate();
        assertTrue(service.verify(c.token(), String.valueOf(c.answer())));
    }

    @Test
    void verify_wrongAnswer_returnsFalse() {
        CaptchaService service = newService(120);
        CaptchaService.Captcha c = service.generate();
        assertFalse(service.verify(c.token(), String.valueOf(c.answer() + 1)));
    }

    @Test
    void verify_isSingleUse() {
        CaptchaService service = newService(120);
        CaptchaService.Captcha c = service.generate();
        assertTrue(service.verify(c.token(), String.valueOf(c.answer())));
        // 同一 token 二次校验应失败（已销毁）
        assertFalse(service.verify(c.token(), String.valueOf(c.answer())));
    }

    @Test
    void verify_unknownToken_returnsFalse() {
        CaptchaService service = newService(120);
        assertFalse(service.verify("nope", "123"));
    }

    @Test
    void verify_nullInput_returnsFalse() {
        CaptchaService service = newService(120);
        assertFalse(service.verify(null, "1"));
        assertFalse(service.verify("tok", null));
        assertFalse(service.verify("tok", "  "));
    }

    @Test
    void verify_expired_returnsFalse() throws InterruptedException {
        // expireSeconds=0：生成瞬间即过期，稍后校验应失败
        CaptchaService service = newService(0);
        CaptchaService.Captcha c = service.generate();
        Thread.sleep(60);
        assertFalse(service.verify(c.token(), String.valueOf(c.answer())));
    }
}
