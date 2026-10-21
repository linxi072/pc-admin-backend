package com.acme.scaffold.e2e;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.realtime.controller.RealtimeController;
import com.acme.scaffold.realtime.dto.RealtimeBroadcastRequest;
import com.acme.scaffold.realtime.dto.RealtimePushResultView;
import com.acme.scaffold.realtime.dto.RealtimeSessionView;
import com.acme.scaffold.realtime.dto.RealtimeUserPushRequest;
import com.acme.scaffold.realtime.service.RealtimeAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 实时推送管理端到端边界测试（离线可执行切片）。
 *
 * <p>以 standalone MockMvc 装配<b>真实</b> {@link RealtimeController} 与<b>真实</b>
 * {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取
 * {@code i18n/messages*} 资源束），服务层以 Mockito 桩接。覆盖「HTTP 路由 → Bean Validation →
 * 控制器编排 → 服务调用 → 统一 {@link Result} 包络 / 异常映射」全链路，在无需 MySQL /
 * WebSocket 连接的前提下对实时推送管理接口做契约级验证。</p>
 */
@DisplayName("实时推送管理端到端边界测试")
class RealtimeE2eTest {

    private RealtimeAdminService adminService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        adminService = mock(RealtimeAdminService.class);
        RealtimeController controller = new RealtimeController(adminService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(i18n()))
                .build();
    }

    private I18nService i18n() {
        ReloadableResourceBundleMessageSource src = new ReloadableResourceBundleMessageSource();
        src.setBasenames("classpath:i18n/messages", "classpath:i18n/validation");
        src.setDefaultEncoding("UTF-8");
        src.setFallbackToSystemLocale(false);
        src.setCacheSeconds(-1);
        return new I18nService(src);
    }

    @Test
    @DisplayName("RT-01 在线会话列表 → 200 / code=0 / 返回会话分布")
    void sessions_ok() throws Exception {
        when(adminService.listSessions())
                .thenReturn(List.of(new RealtimeSessionView(1L, 2), new RealtimeSessionView(7L, 1)));
        mvc.perform(get("/api/realtime/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].userId").value(1))
                .andExpect(jsonPath("$.data[0].sessionCount").value(2));
    }

    @Test
    @DisplayName("RT-02 在线会话总数 → 200 / code=0 / data=3")
    void onlineCount_ok() throws Exception {
        when(adminService.onlineCount()).thenReturn(3);
        mvc.perform(get("/api/realtime/online-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    @DisplayName("RT-03 广播推送成功 → 200 / code=0 / data.delivered=4")
    void broadcast_ok() throws Exception {
        when(adminService.broadcast(any(RealtimeBroadcastRequest.class)))
                .thenReturn(new RealtimePushResultView(4));
        mvc.perform(post("/api/realtime/push/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOTIFICATION\",\"payload\":{\"title\":\"系统维护\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.delivered").value(4));
    }

    @Test
    @DisplayName("RT-04 广播推送缺 type → 400 / COMMON_001（Bean Validation 拦截）")
    void broadcast_validation() throws Exception {
        mvc.perform(post("/api/realtime/push/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"\",\"payload\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("RT-05 定向推送成功 → 200 / code=0 / data.delivered=2")
    void pushToUser_ok() throws Exception {
        when(adminService.pushToUsers(any(RealtimeUserPushRequest.class)))
                .thenReturn(new RealtimePushResultView(2));
        mvc.perform(post("/api/realtime/push/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1,2],\"type\":\"UNREAD_MESSAGE\",\"payload\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.delivered").value(2));
    }

    @Test
    @DisplayName("RT-06 定向推送 userIds 为空 → 400 / COMMON_001")
    void pushToUser_emptyUserIds() throws Exception {
        mvc.perform(post("/api/realtime/push/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[],\"type\":\"UNREAD_MESSAGE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }
}
