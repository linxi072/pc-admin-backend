package com.acme.scaffold.common.notification;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link NotificationRouter} 纯函数路由测试：按渠道命中适配器；无命中抛校验异常。
 */
class NotificationRouterTest {

    private static final NotificationPort IN_APP = new NotificationPort() {
        @Override
        public boolean supports(Channel c) {
            return c == Channel.IN_APP;
        }

        @Override
        public int dispatch(NotificationMessage m) {
            return 0;
        }
    };

    private static final NotificationPort WEBHOOK = new NotificationPort() {
        @Override
        public boolean supports(Channel c) {
            return c == Channel.WEBHOOK;
        }

        @Override
        public int dispatch(NotificationMessage m) {
            return 1;
        }
    };

    @Test
    void resolvesInApp() {
        assertSame(IN_APP, NotificationRouter.resolve(Channel.IN_APP, List.of(IN_APP, WEBHOOK)));
    }

    @Test
    void resolvesWebhook() {
        assertSame(WEBHOOK, NotificationRouter.resolve(Channel.WEBHOOK, List.of(IN_APP, WEBHOOK)));
    }

    @Test
    void throwsWhenNoAdapterSupportsChannel() {
        assertThrows(BusinessException.class,
                () -> NotificationRouter.resolve(Channel.WEBHOOK, List.of(IN_APP)));
    }
}
