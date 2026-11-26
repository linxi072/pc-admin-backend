package com.acme.scaffold.monitor.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Alertmanager webhook 鉴权分支单测：令牌恒定时间比较不得改变 202/401/503 行为。 */
class AlertWebhookControllerTest {

    @Test
    void matchingTokenShouldBeAccepted() {
        AlertReceiverService service = mock(AlertReceiverService.class);
        when(service.receive(any())).thenReturn(1);
        AlertWebhookController controller = controller(service, "secret-token");

        ResponseEntity<?> response = controller.webhook(null, "secret-token");

        assertEquals(202, response.getStatusCode().value());
        verify(service).receive(any());
    }

    @Test
    void mismatchedTokenShouldBeRejectedBeforeReceiving() {
        AlertReceiverService service = mock(AlertReceiverService.class);
        AlertWebhookController controller = controller(service, "secret-token");

        ResponseEntity<?> response = controller.webhook(null, "wrong-token");

        assertEquals(401, response.getStatusCode().value());
        verify(service, never()).receive(any());
    }

    @Test
    void nullTokenShouldBeRejectedBeforeReceiving() {
        AlertReceiverService service = mock(AlertReceiverService.class);
        AlertWebhookController controller = controller(service, "secret-token");

        ResponseEntity<?> response = controller.webhook(null, null);

        assertEquals(401, response.getStatusCode().value());
        verify(service, never()).receive(any());
    }

    @Test
    void blankConfiguredTokenShouldFailClosed() {
        AlertReceiverService service = mock(AlertReceiverService.class);
        AlertWebhookController controller = controller(service, "   ");

        ResponseEntity<?> response = controller.webhook(null, "   ");

        assertEquals(503, response.getStatusCode().value());
        verify(service, never()).receive(any());
    }

    private static AlertWebhookController controller(AlertReceiverService service, String token) {
        AlertProperties props = new AlertProperties();
        props.getWebhook().setToken(token);
        return new AlertWebhookController(service, props);
    }
}
