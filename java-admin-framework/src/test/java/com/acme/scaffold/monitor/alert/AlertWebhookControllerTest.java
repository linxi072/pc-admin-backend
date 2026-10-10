package com.acme.scaffold.monitor.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertWebhookControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private MockMvc mockMvc() {
        AlertReceiverService service = new AlertReceiverService(new AlertProperties(),
                org.springframework.context.ApplicationEventPublisher.class.cast(
                        new org.springframework.context.ApplicationEventPublisher() {
                            @Override
                            public void publishEvent(Object event) {
                            }
                        }));
        AlertWebhookController controller = new AlertWebhookController(service, new AlertProperties());
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String firingPayload() {
        return "{\"status\":\"firing\",\"alerts\":[{\"status\":\"firing\","
                + "\"labels\":{\"alertname\":\"HighErrorRate\",\"severity\":\"critical\"},"
                + "\"annotations\":{\"summary\":\"error rate > 1%\"}}]}";
    }

    @Test
    void webhook_acceptsAndReturns202() throws Exception {
        mockMvc().perform(post("/api/monitoring/alerts/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.received").value(1));
    }

    @Test
    void webhook_emptyPayload_returns400() throws Exception {
        mockMvc().perform(post("/api/monitoring/alerts/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALERT_EMPTY"));
    }

    @Test
    void webhook_tokenMismatch_returns401() throws Exception {
        AlertProperties props = new AlertProperties();
        props.getWebhook().setToken("secret");
        AlertReceiverService service = new AlertReceiverService(props,
                new org.springframework.context.ApplicationEventPublisher() {
                    @Override public void publishEvent(Object event) {
                    }
                });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AlertWebhookController(service, props)).build();

        mvc.perform(post("/api/monitoring/alerts/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ALERT_TOKEN_INVALID"));

        // 携带正确令牌则通过
        mvc.perform(post("/api/monitoring/alerts/webhook")
                        .header("X-Alert-Token", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isAccepted());
    }

    @Test
    void inbox_returns200() throws Exception {
        MockMvc mvc = mockMvc();
        mvc.perform(post("/api/monitoring/alerts/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/monitoring/alerts/webhook/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
