package com.acme.scaffold.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.oauth2.OAuth2AuthorizationCodeService.CodeContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** OAuth2 授权码一次性语义单测：顺序重放、并发双花、非法请求不消耗。 */
class OAuth2AuthorizationCodeServiceTest {

    private static final String CLIENT_ID = "client-1";
    private static final String REDIRECT = "https://admin.example.com/cb";

    @Test
    void codeShouldBeSingleUse() {
        OAuth2AuthorizationCodeService service = newService();
        String code = service.issue(CLIENT_ID, REDIRECT, context());

        assertNotNull(service.consume(code, CLIENT_ID, REDIRECT, null));
        assertThrows(BusinessException.class, () -> service.consume(code, CLIENT_ID, REDIRECT, null));
    }

    @Test
    void concurrentConsumeShouldSucceedOnlyOnce() throws Exception {
        OAuth2AuthorizationCodeService service = newService();
        String code = service.issue(CLIENT_ID, REDIRECT, context());
        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                barrier.await();
                try {
                    service.consume(code, CLIENT_ID, REDIRECT, null);
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            });
        }
        try {
            List<Future<Boolean>> results = pool.invokeAll(tasks);
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes, "同一授权码并发兑换只能成功一次");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidClientShouldNotConsumeCode() {
        OAuth2AuthorizationCodeService service = newService();
        String code = service.issue(CLIENT_ID, REDIRECT, context());

        assertThrows(BusinessException.class, () -> service.consume(code, "wrong-client", REDIRECT, null));
        assertNotNull(service.consume(code, CLIENT_ID, REDIRECT, null));
    }

    private static OAuth2AuthorizationCodeService newService() {
        return new OAuth2AuthorizationCodeService(new RsaKeyProvider(new JwtProperties()));
    }

    private static CodeContext context() {
        return new CodeContext(42L, "alice", 1, Set.of("read"), Set.of("USER"),
                CLIENT_ID, "read", REDIRECT, "", "");
    }
}
