package com.acme.scaffold.system.openapi;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.openapi.domain.OpenApiClientDO;
import com.acme.scaffold.system.openapi.dto.ClientSecretView;
import com.acme.scaffold.system.openapi.dto.CreateOpenApiClientRequest;
import com.acme.scaffold.system.openapi.dto.OpenApiClientView;
import com.acme.scaffold.system.openapi.dto.UpdateOpenApiClientRequest;
import com.acme.scaffold.common.oauth2.OpenApiClientRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 开放平台客户端应用服务（V48 API 开放平台 / M24）。
 *
 * <p><b>密钥治理</b>：创建时生成明文密钥（一次性返回，仅哈希落库）；轮换重新生成并刷新
 * {@code secret_rotated_at}；停用/启用经 toggle；过期时间仅记录，协议级拦截待授权服务器落地。</p>
 *
 * <p><b>协议级开放</b>：密钥治理与令牌签发职责分离——本服务仅负责客户端注册与密钥治理，
 * 令牌签发由 {@code OAuth2TokenEndpoint}（client_credentials + RS256）承担；授权服务器侧以自包含方式
 * 手搓 RS256，无需离线仓库缺失的 authorization-server 依赖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiClientService {

    /** 排序白名单：客户端字段名 → 数据库列名（白名单外忽略，防 ORDER BY 注入）。 */
    private static final Map<String, String> SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "clientId", "client_id",
            "clientName", "client_name",
            "status", "status",
            "secretRotatedAt", "secret_rotated_at",
            "expiredAt", "expired_at",
            "createdAt", "created_at");

    private final DSLContext dsl;

    /** 与用户口令同机制的密钥哈希器（DelegatingPasswordEncoder，默认 BCrypt）。 */
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // 管理面查询
    // ------------------------------------------------------------------

    public PageResult<OpenApiClientView> page(int page, int size, String clientId,
                                              String status, String sortField, String sortDirection) {
        Condition cond = buildCondition(clientId, status);
        var query = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<OpenApiClientDO> raw = JooqWriters.page(dsl, JooqTables.OPENAPI_CLIENT,
                OpenApiClientDO.class, cond, query,
                JooqSorts.resolve(JooqTables.OPENAPI_CLIENT, query, SORT_FIELDS,
                        JooqTables.OPENAPI_CLIENT.field("id", Long.class).desc()));
        return PageResult.of(raw.page(), raw.size(), raw.total(),
                raw.records().stream().map(OpenApiClientView::of).toList());
    }

    /** 全部客户端（含停用，按 id 升序）。 */
    public List<OpenApiClientView> list() {
        List<OpenApiClientDO> rows = JooqWriters.fetchList(dsl, JooqTables.OPENAPI_CLIENT,
                OpenApiClientDO.class, JooqWriters.notDeleted(JooqTables.OPENAPI_CLIENT),
                JooqTables.OPENAPI_CLIENT.field("id", Long.class).asc());
        return rows.stream().map(OpenApiClientView::of).toList();
    }

    public OpenApiClientView get(Long id) {
        return OpenApiClientView.of(requireById(id));
    }

    // ------------------------------------------------------------------
    // 注册 / 治理
    // ------------------------------------------------------------------

    /**
     * 注册客户端：结构校验 + client_id 唯一性 → 生成密钥明文（一次性返回）→ 仅哈希落库。
     */
    public ClientSecretView create(CreateOpenApiClientRequest req) {
        List<String> errors = OpenApiClientRules.validate(req.clientId(), req.clientName(),
                req.scopes(), req.status(), req.expiredAt());
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        if (existsByClientId(req.clientId())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "客户端 ID 已存在");
        }
        String plainSecret = OpenApiClientRules.generateSecret();
        LocalDateTime now = LocalDateTime.now();
        OpenApiClientDO d = new OpenApiClientDO();
        d.setClientId(req.clientId());
        d.setClientName(req.clientName());
        d.setScopes(OpenApiClientRules.normalizeScopes(req.scopes()));
        d.setClientSecretHash(passwordEncoder.encode(plainSecret));
        d.setStatus(OpenApiClientRules.normalizeStatus(req.status()) == null
                ? OpenApiClientRules.DEFAULT_STATUS : OpenApiClientRules.normalizeStatus(req.status()));
        d.setSecretRotatedAt(now);
        d.setExpiredAt(req.expiredAt());
        d.setRemark(req.remark());
        JooqWriters.insert(dsl, JooqTables.OPENAPI_CLIENT, d);
        return new ClientSecretView(d.getClientId(), plainSecret, now);
    }

    /**
     * 更新客户端：仅传入的非空字段参与；以「合并后的完整值」做结构校验
     * （client_id 不可变更；密钥变更走 {@link #rotateSecret}）。
     */
    public void update(Long id, UpdateOpenApiClientRequest req) {
        OpenApiClientDO existing = requireById(id);
        String name = req.clientName() != null ? req.clientName() : existing.getClientName();
        String scopes = req.scopes() != null ? req.scopes() : existing.getScopes();
        String status = req.status() != null ? req.status() : existing.getStatus();
        LocalDateTime expiredAt = req.expiredAt() != null ? req.expiredAt() : existing.getExpiredAt();
        List<String> errors = OpenApiClientRules.validate(existing.getClientId(), name, scopes,
                status, expiredAt);
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        OpenApiClientDO patch = new OpenApiClientDO();
        if (req.clientName() != null) {
            patch.setClientName(req.clientName());
        }
        if (req.scopes() != null) {
            patch.setScopes(OpenApiClientRules.normalizeScopes(req.scopes()));
        }
        if (req.status() != null) {
            patch.setStatus(OpenApiClientRules.normalizeStatus(req.status()));
        }
        if (req.expiredAt() != null) {
            patch.setExpiredAt(req.expiredAt());
        }
        if (req.remark() != null) {
            patch.setRemark(req.remark());
        }
        JooqWriters.updateById(dsl, JooqTables.OPENAPI_CLIENT, id, patch);
    }

    /** 删除客户端（逻辑删除，便于审计与恢复）。 */
    public void remove(Long id) {
        requireById(id);
        JooqWriters.delete(dsl, JooqTables.OPENAPI_CLIENT, id, true);
    }

    /** 启停客户端。 */
    public void toggle(Long id, boolean enabled) {
        requireById(id);
        OpenApiClientDO patch = new OpenApiClientDO();
        patch.setStatus(enabled ? "ENABLED" : "DISABLED");
        JooqWriters.updateById(dsl, JooqTables.OPENAPI_CLIENT, id, patch);
    }

    /**
     * 轮换密钥：重新生成明文（一次性返回）→ 哈希覆盖 → 刷新 {@code secret_rotated_at}。
     * 旧密钥立即失效（哈希被覆盖，无法回放）。
     */
    public ClientSecretView rotateSecret(Long id) {
        OpenApiClientDO existing = requireById(id);
        String plainSecret = OpenApiClientRules.generateSecret();
        LocalDateTime now = LocalDateTime.now();
        OpenApiClientDO patch = new OpenApiClientDO();
        patch.setClientSecretHash(passwordEncoder.encode(plainSecret));
        patch.setSecretRotatedAt(now);
        JooqWriters.updateById(dsl, JooqTables.OPENAPI_CLIENT, id, patch);
        return new ClientSecretView(existing.getClientId(), plainSecret, now);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private OpenApiClientDO requireById(Long id) {
        OpenApiClientDO d = JooqWriters.fetchById(dsl, JooqTables.OPENAPI_CLIENT,
                OpenApiClientDO.class, id);
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "开放平台客户端不存在");
        }
        return d;
    }

    private boolean existsByClientId(String clientId) {
        return JooqWriters.fetchOne(dsl, JooqTables.OPENAPI_CLIENT, OpenApiClientDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.OPENAPI_CLIENT),
                        JooqTables.OPENAPI_CLIENT.field("client_id", String.class).eq(clientId))) != null;
    }

    /**
     * 客户端认证（OAuth2 授权服务器接入点）：查库 → BCrypt 校验密钥哈希 → 状态/过期校验。
     * 失败（含不存在、停用、过期、密钥错误）统一抛 {@link CommonErrorCode#UNAUTHORIZED}，
     * 不泄露具体失败原因，与登录链路口径一致。
     *
     * @return 通过认证的客户端实体（含 scopes 等签发令牌所需信息）
     */
    public OpenApiClientDO authenticateClient(String clientId, String secret) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(secret)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端凭证缺失");
        }
        OpenApiClientDO d = JooqWriters.fetchOne(dsl, JooqTables.OPENAPI_CLIENT, OpenApiClientDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.OPENAPI_CLIENT),
                        JooqTables.OPENAPI_CLIENT.field("client_id", String.class).eq(clientId)));
        if (d == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端不存在或已注销");
        }
        if (!"ENABLED".equals(d.getStatus())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端已停用");
        }
        if (d.getExpiredAt() != null && d.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端已过期");
        }
        if (!passwordEncoder.matches(secret, d.getClientSecretHash())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端密钥错误");
        }
        return d;
    }

    /**
     * 仅按 client_id 取「启用中」的客户端（不校验密钥），供授权端点校验客户端合法性与授权范围。
     *
     * @throws BusinessException 客户端不存在 / 已注销 / 已停用 / 已过期
     */
    public OpenApiClientDO getActiveClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端标识缺失");
        }
        OpenApiClientDO d = JooqWriters.fetchOne(dsl, JooqTables.OPENAPI_CLIENT, OpenApiClientDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.OPENAPI_CLIENT),
                        JooqTables.OPENAPI_CLIENT.field("client_id", String.class).eq(clientId)));
        if (d == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端不存在或已注销");
        }
        if (!"ENABLED".equals(d.getStatus())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端已停用");
        }
        if (d.getExpiredAt() != null && d.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端已过期");
        }
        return d;
    }

    /** 统一筛选条件：逻辑删除恒成立；client_id 模糊匹配/状态精确匹配按需收敛。 */
    private Condition buildCondition(String clientId, String status) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.OPENAPI_CLIENT));
        if (StringUtils.hasText(clientId)) {
            conds.add(JooqTables.OPENAPI_CLIENT.field("client_id", String.class).like("%" + clientId.trim() + "%"));
        }
        String st = OpenApiClientRules.normalizeStatus(status);
        if (st != null) {
            conds.add(JooqTables.OPENAPI_CLIENT.field("status", String.class).eq(st));
        }
        return DSL.and(conds);
    }
}
