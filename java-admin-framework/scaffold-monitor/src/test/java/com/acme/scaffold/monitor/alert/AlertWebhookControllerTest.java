package com.acme.scaffold.monitor.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertWebhookControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String TOKEN = "secret";

    private ApplicationEventPublisher silentPublisher() {
        return new ApplicationEventPublisher() {
            @Override
            public void publishEvent(Object event) {
            }
        };
    }

    /** 默认带令牌的配置（P0-2：空令牌端点应拒绝，故正常用例须显式配置令牌）。 */
    private AlertProperties propsWithToken() {
        AlertProperties props = new AlertProperties();
        props.getWebhook().setToken(TOKEN);
        return props;
    }

    private MockMvc mockMvc(AlertProperties props) {
        AlertReceiverService service = new AlertReceiverService(props, silentPublisher());
        return MockMvcBuilders.standaloneSetup(new AlertWebhookController(service, props)).build();
    }

    private String firingPayload() {
        return "{\"status\":\"firing\",\"alerts\":[{\"status\":\"firing\","
                + "\"labels\":{\"alertname\":\"HighErrorRate\",\"severity\":\"critical\"},"
                + "\"annotations\":{\"summary\":\"error rate > 1%\"}}]}";
    }

    @Test
    void webhook_acceptsAndReturns202() throws Exception {
        mockMvc(propsWithToken()).perform(post("/api/monitoring/alerts/webhook")
                        .header("X-Alert-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.received").value(1));
    }

    @Test
    void webhook_emptyPayload_returns400() throws Exception {
        mockMvc(propsWithToken()).perform(post("/api/monitoring/alerts/webhook")
                        .header("X-Alert-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALERT_EMPTY"));
    }

    @Test
    void webhook_tokenMismatch_returns401() throws Exception {
        AlertProperties props = propsWithToken();
        MockMvc mvc = mockMvc(props);

        // 无令牌头 => 401
        mvc.perform(post("/api/monitoring/alerts/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ALERT_TOKEN_INVALID"));

        // 错误令牌 => 401
        mvc.perform(post("/api/monitoring/alerts/webhook")
                        .header("X-Alert-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ALERT_TOKEN_INVALID"));

        // 携带正确令牌则通过
        mvc.perform(post("/api/monitoring/alerts/webhook")
                        .header("X-Alert-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isAccepted());
    }

    @Test
    void webhook_blankToken_rejectsWith503() throws Exception {
        // P0-2：启用但令牌为空 => 端点拒绝（fail-closed），不再匿名开放
        AlertProperties props = new AlertProperties(); // 默认 enabled=true, token=""
        mockMvc(props).perform(post("/api/monitoring/alerts/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ALERT_WEBHOOK_TOKEN_MISSING"));
    }

    @Test
    void inbox_returns200() throws Exception {
        MockMvc mvc = mockMvc(propsWithToken());
        mvc.perform(post("/api/monitoring/alerts/webhook")
                        .header("X-Alert-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firingPayload()))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/monitoring/alerts/webhook/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
