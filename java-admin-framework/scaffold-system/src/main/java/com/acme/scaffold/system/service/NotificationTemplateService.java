package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateNotificationTemplateRequest;
import com.acme.scaffold.system.dto.NotificationTemplateView;
import com.acme.scaffold.system.dto.PreviewNotificationTemplateRequest;
import com.acme.scaffold.system.dto.UpdateNotificationTemplateRequest;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.rules.NotificationTemplateRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知模板中心服务（V41）。
 *
 * <p>为 M6 多渠道通知提供可复用模板：模板含 {@code ${var}} 占位，发送时由调用方按变量映射
 * 渲染后再走各 {@code Channel} 适配器。本服务只负责模板的「增删改查 + 渲染预览」，
 * 不耦合具体发送链路——发送侧按需取模板、渲染、投递，职责清晰。</p>
 *
 * <p>校验分层：Bean Validation 做入参快速失败；{@link NotificationTemplateRules#validate}
 * 做模板自身结构校验（编码格式/渠道/内容）；{@code template_code} 唯一性由本服务查库判定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    /** 排序白名单：客户端字段名 → 数据库列名（白名单外忽略，防 ORDER BY 注入）。 */
    private static final Map<String, String> TEMPLATE_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "templateCode", "template_code",
            "templateName", "template_name",
            "channel", "channel",
            "enabled", "enabled",
            "createdAt", "created_at");

    private final DSLContext dsl;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 分页查询通知模板（渠道/关键字/启用状态筛选，服务端排序白名单）。
     */
    public PageResult<NotificationTemplateView> page(int page, int size, String channel,
                                                     String keyword, Boolean enabled,
                                                     String sortField, String sortDirection) {
        Condition cond = buildCondition(channel, keyword, enabled);
        var query = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<SysNotificationTemplateDO> raw = JooqWriters.page(dsl, JooqTables.SYS_NOTIFICATION_TEMPLATE,
                SysNotificationTemplateDO.class, cond, query,
                JooqSorts.resolve(JooqTables.SYS_NOTIFICATION_TEMPLATE, query, TEMPLATE_SORT_FIELDS,
                        JooqTables.SYS_NOTIFICATION_TEMPLATE.field("created_at", LocalDateTime.class).desc(),
                        JooqTables.SYS_NOTIFICATION_TEMPLATE.field("id", Long.class).desc()));
        List<NotificationTemplateView> views = raw.records().stream()
                .map(d -> NotificationTemplateView.of(d,
                        NotificationTemplateRules.extractVariables(d.getContent())))
                .toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /** 查询单个模板（含变量名解析）。 */
    public NotificationTemplateView get(Long id) {
        return NotificationTemplateView.of(requireById(id),
                NotificationTemplateRules.extractVariables(requireById(id).getContent()));
    }

    /** 提取模板正文中的变量名列表（供前端渲染变量输入表单）。 */
    public List<String> variables(Long id) {
        return NotificationTemplateRules.extractVariables(requireById(id).getContent());
    }

    /**
     * 渲染预览：用传入变量映射渲染模板正文；未提供的变量保持 {@code ${name}} 原样，
     * 并在响应中单独列出缺失变量，便于前端提示。
     *
     * @return {@code rendered}（渲染结果）/ {@code requiredVariables}（模板要求变量）/
     *         {@code missingVariables}（本次预览未提供取值的变量）
     */
    public Map<String, Object> preview(Long id, PreviewNotificationTemplateRequest request) {
        SysNotificationTemplateDO d = requireById(id);
        Map<String, String> vars = request.variables() == null ? Map.of() : request.variables();
        String rendered = NotificationTemplateRules.render(d.getContent(), vars);
        List<String> required = NotificationTemplateRules.extractVariables(d.getContent());
        List<String> missing = new ArrayList<>();
        for (String name : required) {
            String v = vars.get(name);
            if (v == null || v.isBlank()) {
                missing.add(name);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rendered", rendered);
        out.put("requiredVariables", required);
        out.put("missingVariables", missing);
        return out;
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /** 创建模板：结构校验 + 编码唯一性校验，通过后落库并返回主键。 */
    public Long create(CreateNotificationTemplateRequest req) {
        List<String> errors = NotificationTemplateRules.validate(
                req.templateCode(), req.templateName(), req.channel(), req.content());
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        if (existsByCode(req.templateCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "模板编码已存在");
        }
        SysNotificationTemplateDO d = new SysNotificationTemplateDO();
        d.setTemplateCode(req.templateCode());
        d.setTemplateName(req.templateName());
        d.setChannel(NotificationTemplateRules.normalizeChannel(req.channel()));
        d.setTitle(req.title());
        d.setContent(req.content());
        d.setMsgType(StringUtils.hasText(req.msgType()) ? req.msgType() : "NOTICE");
        d.setEnabled(req.enabled() != null && req.enabled() ? 1 : 0);
        d.setRemark(req.remark());
        JooqWriters.insert(dsl, JooqTables.SYS_NOTIFICATION_TEMPLATE, d);
        return d.getId();
    }

    /**
     * 更新模板：仅传入的非空字段参与更新；以「合并后的完整值」做结构校验，
     * 编码变更时再做唯一性校验，保证部分更新后仍满足模板契约。
     */
    public void update(Long id, UpdateNotificationTemplateRequest req) {
        SysNotificationTemplateDO existing = requireById(id);
        String code = req.templateCode() != null ? req.templateCode() : existing.getTemplateCode();
        String name = req.templateName() != null ? req.templateName() : existing.getTemplateName();
        String channel = req.channel() != null ? req.channel() : existing.getChannel();
        String content = req.content() != null ? req.content() : existing.getContent();
        List<String> errors = NotificationTemplateRules.validate(code, name, channel, content);
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        if (req.templateCode() != null && !req.templateCode().equals(existing.getTemplateCode())
                && existsByCode(req.templateCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "模板编码已存在");
        }
        SysNotificationTemplateDO patch = new SysNotificationTemplateDO();
        if (req.templateCode() != null) {
            patch.setTemplateCode(req.templateCode());
        }
        if (req.templateName() != null) {
            patch.setTemplateName(req.templateName());
        }
        if (req.channel() != null) {
            patch.setChannel(NotificationTemplateRules.normalizeChannel(req.channel()));
        }
        if (req.title() != null) {
            patch.setTitle(req.title());
        }
        if (req.content() != null) {
            patch.setContent(req.content());
        }
        if (req.msgType() != null) {
            patch.setMsgType(req.msgType());
        }
        if (req.enabled() != null) {
            patch.setEnabled(req.enabled() ? 1 : 0);
        }
        if (req.remark() != null) {
            patch.setRemark(req.remark());
        }
        JooqWriters.updateById(dsl, JooqTables.SYS_NOTIFICATION_TEMPLATE, id, patch);
    }

    /** 删除模板（逻辑删除，便于审计与恢复）。 */
    public void remove(Long id) {
        requireById(id);
        JooqWriters.delete(dsl, JooqTables.SYS_NOTIFICATION_TEMPLATE, id, true);
    }

    /** 启停模板。 */
    public void toggle(Long id, boolean enabled) {
        requireById(id);
        SysNotificationTemplateDO patch = new SysNotificationTemplateDO();
        patch.setEnabled(enabled ? 1 : 0);
        JooqWriters.updateById(dsl, JooqTables.SYS_NOTIFICATION_TEMPLATE, id, patch);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private SysNotificationTemplateDO requireById(Long id) {
        SysNotificationTemplateDO d = JooqWriters.fetchById(dsl, JooqTables.SYS_NOTIFICATION_TEMPLATE,
                SysNotificationTemplateDO.class, id);
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "通知模板不存在");
        }
        return d;
    }

    private boolean existsByCode(String code) {
        return fetchByCode(code) != null;
    }

    /** 按编码取未删除模板（不存在返回 null）。create/requireEnabledByCode 共用。 */
    private SysNotificationTemplateDO fetchByCode(String code) {
        return JooqWriters.fetchOne(dsl, JooqTables.SYS_NOTIFICATION_TEMPLATE,
                SysNotificationTemplateDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                        JooqTables.SYS_NOTIFICATION_TEMPLATE.field("template_code", String.class).eq(code)));
    }

    /**
     * 按编码取「启用中」的模板，供发送侧（M11 {@code send-with-template}）渲染投递前取模板。
     *
     * <p>缺失抛 {@code NOT_FOUND}、停用抛 {@code CONFLICT}——发送必须显式失败，
     * 不允许静默使用停用模板或回退到空正文。</p>
     */
    public SysNotificationTemplateDO requireEnabledByCode(String code) {
        SysNotificationTemplateDO d = fetchByCode(code);
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "通知模板不存在: " + code);
        }
        if (d.getEnabled() == null || d.getEnabled() != 1) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "通知模板已停用: " + code);
        }
        return d;
    }

    /** 统一筛选条件：逻辑删除恒成立；渠道/启用状态/关键字按需收敛。 */
    private Condition buildCondition(String channel, String keyword, Boolean enabled) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.SYS_NOTIFICATION_TEMPLATE));
        String ch = NotificationTemplateRules.normalizeChannel(channel);
        if (ch != null) {
            conds.add(JooqTables.SYS_NOTIFICATION_TEMPLATE.field("channel", String.class).eq(ch));
        }
        if (enabled != null) {
            conds.add(JooqTables.SYS_NOTIFICATION_TEMPLATE.field("enabled", Integer.class)
                    .eq(enabled ? 1 : 0));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim() + "%";
            conds.add(DSL.or(
                    JooqTables.SYS_NOTIFICATION_TEMPLATE.field("template_code", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_TEMPLATE.field("template_name", String.class).like(kw),
                    JooqTables.SYS_NOTIFICATION_TEMPLATE.field("content", String.class).like(kw)));
        }
        return DSL.and(conds);
    }
}
