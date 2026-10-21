package com.acme.scaffold.e2e;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.exception.GlobalExceptionHandler;
import com.acme.scaffold.common.i18n.I18nService;
import com.acme.scaffold.common.web.ClientIpResolver;
import com.acme.scaffold.security.controller.AuthController;
import com.acme.scaffold.security.vo.TokenView;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.service.AuthService;
import com.acme.scaffold.system.controller.ConfigController;
import com.acme.scaffold.system.controller.UserController;
import com.acme.scaffold.system.dto.ConfigVO;
import com.acme.scaffold.system.dto.UserView;
import com.acme.scaffold.system.service.ConfigService;
import com.acme.scaffold.system.service.UserService;
import com.acme.scaffold.workflow.controller.TaskController;
import com.acme.scaffold.workflow.dto.TaskView;
import com.acme.scaffold.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端 API 边界测试（离线可执行切片）。
 *
 * <p>策略：以 standalone MockMvc 装配<b>真实</b>控制器（Auth / Config / User / Workflow Task）
 * 与<b>真实</b> {@link GlobalExceptionHandler}（构造<b>真实</b> {@link I18nService}，读取
 * {@code i18n/messages*} 资源束），服务层以 Mockito 桩接。覆盖「HTTP 路由 → 参数反序列化与
 * Bean Validation → 控制器编排 → 服务调用 → 统一 {@link Result} 包络 / 异常映射」全链路，
 * 在<b>无需 MySQL</b> 的前提下对核心用户操作路径做接口级 E2E 验证。</p>
 *
 * <p>说明：本类与 {@code *IT} 集成测试互补——后者在 {@code integration} profile 下拉起真实
 * Spring 上下文 + 本机 MySQL，验证「认证 → 授权 → 业务 → 数据落库」全栈；本类聚焦接口契约与
 * 错误处理，作为沙箱/CI 中可离线稳定执行的 E2E 前哨。</p>
 */
@DisplayName("核心 API 端到端边界测试")
class CoreApiE2eTest {

    private AuthService authService;
    private ClientIpResolver clientIpResolver;
    private ConfigService configService;
    private UserService userService;
    private WorkflowService workflowService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        clientIpResolver = mock(ClientIpResolver.class);
        configService = mock(ConfigService.class);
        userService = mock(UserService.class);
        workflowService = mock(WorkflowService.class);

        when(clientIpResolver.resolve(any())).thenReturn("127.0.0.1");

        AuthController authController = new AuthController(authService, clientIpResolver);
        ConfigController configController = new ConfigController(configService);
        UserController userController = new UserController(userService);
        TaskController taskController = new TaskController(workflowService);

        mvc = MockMvcBuilders.standaloneSetup(authController, configController, userController, taskController)
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

    private static final TokenView TOKEN = new TokenView("access-xyz", "refresh-xyz", 3600L);
    private static final ConfigVO CONFIG = new ConfigVO(1L, "key1", "名称1", "v1", "dv1",
            "STRING", "remark", 1, "1");
    private static final UserView USER = UserView.from(1L, "admin", "管理员", "13800000000",
            "a@b.com", List.of(1L), List.of("管理员"), List.of("ROLE_ADMIN"),
            List.of(2L), List.of("技术部"), List.of(), List.of(), "1", LocalDateTime.now());
    private static final TaskView TASK = new TaskView("t1", "pi1", "act1", "审批", "PENDING",
            1L, LocalDateTime.now().plusDays(1));

    // ===================== 认证授权 =====================

    @Nested
    @DisplayName("AUTH 认证授权")
    class AuthE2e {

        @Test
        @DisplayName("AUTH-01 账号密码登录成功 → 200 / code=0 / 返回 accessToken")
        void login_success() throws Exception {
            when(authService.login(any(), any(), any(), any())).thenReturn(TOKEN);
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"S3cret123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.message").value("OK"))
                    .andExpect(jsonPath("$.data.accessToken").value("access-xyz"));
        }

        @Test
        @DisplayName("AUTH-02 登录参数缺失（用户名/密码为空） → 400 / COMMON_001")
        void login_validation() throws Exception {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("AUTH-03 用户名或密码错误 → 401 / AUTH_001（业务异常映射）")
        void login_wrongPassword() throws Exception {
            when(authService.login(any(), any(), any(), any()))
                    .thenThrow(new BusinessException(SecurityErrorCode.BAD_CREDENTIALS));
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_001"));
        }

        @Test
        @DisplayName("AUTH-04 刷新令牌换取新访问令牌成功 → 200 / code=0")
        void refresh_success() throws Exception {
            when(authService.refresh(any())).thenReturn(TOKEN);
            mvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"r1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-xyz"));
        }

        @Test
        @DisplayName("AUTH-05 刷新令牌为空 → 400 / COMMON_001")
        void refresh_validation() throws Exception {
            mvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("AUTH-06 会话无关改密（首次登录/重置后）成功 → 200 / code=0")
        void changePassword_success() throws Exception {
            doNothing().when(authService).changePassword(any(), any(), any());
            mvc.perform(post("/api/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"oldPassword\":\"S3cret123\",\"newPassword\":\"NewPass123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("AUTH-07 改密新密码过短（<8） → 400 / COMMON_001")
        void changePassword_weak() throws Exception {
            mvc.perform(post("/api/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"oldPassword\":\"S3cret123\",\"newPassword\":\"123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("AUTH-08 忘记密码自助重置成功 → 200 / code=0")
        void forgotPassword_success() throws Exception {
            doNothing().when(authService).forgotPassword(any(), any(), any(), any());
            mvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"newPassword\":\"NewPass123\",\"mobile\":\"13800000000\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("AUTH-09 登出（吊销刷新令牌）成功 → 200 / code=0")
        void logout_success() throws Exception {
            doNothing().when(authService).logout();
            mvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }
    }

    // ===================== 系统参数（配置中心） =====================

    @Nested
    @DisplayName("CFG 系统参数配置")
    class ConfigE2e {

        @Test
        @DisplayName("CFG-01 配置列表 → 200 / code=0 / data 非空")
        void list() throws Exception {
            when(configService.list()).thenReturn(List.of(CONFIG));
            mvc.perform(get("/api/system/configs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data[0].configKey").value("key1"));
        }

        @Test
        @DisplayName("CFG-02 按配置键读取存在 → 200 / code=0")
        void getByKey_found() throws Exception {
            when(configService.getByKey("key1")).thenReturn(CONFIG);
            mvc.perform(get("/api/system/configs/key/key1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.configKey").value("key1"));
        }

        @Test
        @DisplayName("CFG-03 按配置键读取不存在 → 404 / COMMON_404（控制器抛 NOT_FOUND）")
        void getByKey_notFound() throws Exception {
            when(configService.getByKey("missing")).thenReturn(null);
            mvc.perform(get("/api/system/configs/key/missing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMON_404"));
        }

        @Test
        @DisplayName("CFG-04 创建配置成功 → 200 / code=0 / data=新ID")
        void create() throws Exception {
            when(configService.create(any())).thenReturn(1L);
            mvc.perform(post("/api/system/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"configKey\":\"key2\",\"configName\":\"名称2\",\"configType\":\"STRING\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data").value("1"));
        }

        @Test
        @DisplayName("CFG-05 创建配置缺 configKey → 400 / COMMON_001")
        void create_validation() throws Exception {
            mvc.perform(post("/api/system/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"configName\":\"名称2\",\"configType\":\"STRING\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("CFG-06 更新配置成功 → 200 / code=0")
        void update() throws Exception {
            doNothing().when(configService).update(any(), any());
            mvc.perform(put("/api/system/configs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"configKey\":\"key2\",\"configName\":\"名称2\",\"configType\":\"STRING\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("CFG-07 删除配置成功 → 200 / code=0")
        void deleteSuccess() throws Exception {
            doNothing().when(configService).delete(any());
            mvc.perform(delete("/api/system/configs/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }
    }

    // ===================== 用户管理 =====================

    @Nested
    @DisplayName("USR 用户管理")
    class UserE2e {

        @Test
        @DisplayName("USR-01 分页查询用户 → 200 / code=0 / 返回分页数据")
        void page() throws Exception {
            when(userService.list(any())).thenReturn(
                    new com.acme.scaffold.common.api.dto.PageResult<>(1, 20, 1, List.of(USER)));
            mvc.perform(get("/api/system/users/page"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.records[0].username").value("admin"));
        }

        @Test
        @DisplayName("USR-02 用户详情存在 → 200 / code=0")
        void get_found() throws Exception {
            when(userService.getView(1L)).thenReturn(USER);
            mvc.perform(get("/api/system/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.username").value("admin"));
        }

        @Test
        @DisplayName("USR-03 用户详情不存在 → 404 / COMMON_404")
        void get_notFound() throws Exception {
            when(userService.getView(2L)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
            mvc.perform(get("/api/system/users/2"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMON_404"));
        }

        @Test
        @DisplayName("USR-04 创建用户成功 → 200 / code=0 / data=新ID")
        void create() throws Exception {
            when(userService.create(any())).thenReturn(1L);
            mvc.perform(post("/api/system/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"bob\",\"password\":\"NewPass123\",\"displayName\":\"Bob\"," +
                                    "\"roleIds\":[1],\"deptIds\":[2]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data").value("1"));
        }

        @Test
        @DisplayName("USR-05 创建用户缺角色/部门集合 → 400 / COMMON_001")
        void create_validation() throws Exception {
            mvc.perform(post("/api/system/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"bob\",\"password\":\"NewPass123\",\"displayName\":\"Bob\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("USR-06 更新用户成功 → 200 / code=0")
        void update() throws Exception {
            doNothing().when(userService).update(any(), any());
            mvc.perform(put("/api/system/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Bob2\",\"status\":\"1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("USR-07 重置用户密码成功 → 200 / code=0")
        void resetPassword() throws Exception {
            doNothing().when(userService).resetPassword(any(), any());
            mvc.perform(post("/api/system/users/1/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"NewPass123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("USR-08 重置密码过短（<8） → 400 / COMMON_001")
        void resetPassword_validation() throws Exception {
            mvc.perform(post("/api/system/users/1/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("USR-09 删除用户成功 → 200 / code=0")
        void deleteSuccess() throws Exception {
            doNothing().when(userService).delete(any());
            mvc.perform(delete("/api/system/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }
    }

    // ===================== 工作流任务 =====================

    @Nested
    @DisplayName("WF 工作流任务")
    class WorkflowE2e {

        @Test
        @DisplayName("WF-01 我的待办 → 200 / code=0 / 返回任务列表")
        void mine() throws Exception {
            when(workflowService.myTasks()).thenReturn(List.of(TASK));
            mvc.perform(get("/api/workflow/tasks/mine"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data[0].taskId").value("t1"));
        }

        @Test
        @DisplayName("WF-02 认领任务成功 → 200 / code=0")
        void claim() throws Exception {
            doNothing().when(workflowService).claim(any());
            mvc.perform(post("/api/workflow/tasks/t1/claim"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("WF-03 审批/驳回任务成功 → 200 / code=0")
        void complete() throws Exception {
            doNothing().when(workflowService).completeTask(any());
            mvc.perform(post("/api/workflow/tasks/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"taskId\":\"t1\",\"action\":\"APPROVE\",\"operationId\":\"op1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }

        @Test
        @DisplayName("WF-04 审批参数缺失 taskId → 400 / COMMON_001")
        void complete_validation() throws Exception {
            mvc.perform(post("/api/workflow/tasks/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"APPROVE\",\"operationId\":\"op1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("WF-05 转办任务成功 → 200 / code=0")
        void transfer() throws Exception {
            doNothing().when(workflowService).transfer(any());
            mvc.perform(post("/api/workflow/tasks/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"taskId\":\"t1\",\"toUserId\":2,\"operationId\":\"op1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
        }
    }
}
