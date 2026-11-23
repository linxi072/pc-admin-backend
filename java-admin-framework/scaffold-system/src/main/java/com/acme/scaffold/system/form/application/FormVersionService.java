package com.acme.scaffold.system.form.application;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.form.domain.model.FormDefinitionDO;
import com.acme.scaffold.system.form.domain.model.FormDefinitionVersionDO;
import com.acme.scaffold.system.form.domain.rule.FormVersionRules;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 表单定义版本服务（E8 低代码深化）：发布时固化不可变快照，回滚时把目标版本写回主表。
 *
 * <p>与主表 {@link FormDefinitionService} 解耦：本服务只负责「版本快照」读写与发布/回滚编排，
 * 缓存失效委托主服务的 {@code refreshCache()}，保证下发面不会命中旧 schema。</p>
 */
@Service
@RequiredArgsConstructor
public class FormVersionService {

    private final DSLContext dsl;
    private final FormDefinitionService definitionService;

    /** 列出某表单定义的全部版本（按版本号升序）。 */
    public List<FormDefinitionVersionDO> listVersions(Long formDefinitionId) {
        return SqlWriters.fetchList(dsl, SqlTables.FORM_DEFINITION_VERSION, FormDefinitionVersionDO.class,
                DSL.and(SqlWriters.notDeleted(SqlTables.FORM_DEFINITION_VERSION),
                        SqlTables.FORM_DEFINITION_VERSION.field("form_definition_id", Long.class).eq(formDefinitionId)),
                SqlTables.FORM_DEFINITION_VERSION.field("version_no", Integer.class).asc());
    }

    /** 发布当前定义为新版本快照；若 schema 与最新已发布版本一致则拒绝（无变更不发布）。 */
    @Transactional
    public FormDefinitionVersionDO publish(Long formDefinitionId) {
        FormDefinitionDO current = requireDefinition(formDefinitionId);
        List<FormDefinitionVersionDO> versions = listVersions(formDefinitionId);
        FormDefinitionVersionDO latest = versions.isEmpty() ? null : versions.get(versions.size() - 1);
        if (latest != null && FormVersionRules.sameSchema(latest.getSchemaJson(), current.getSchemaJson())) {
            throw BusinessException.badRequest("表单 Schema 与最新版本一致，无需重复发布");
        }
        int nextNo = FormVersionRules.nextVersionNo(
                versions.stream().map(FormDefinitionVersionDO::getVersionNo).toList());

        FormDefinitionVersionDO snapshot = new FormDefinitionVersionDO();
        snapshot.setFormDefinitionId(formDefinitionId);
        snapshot.setFormCode(current.getFormCode());
        snapshot.setVersionNo(nextNo);
        snapshot.setFormName(current.getFormName());
        snapshot.setSchemaJson(current.getSchemaJson());
        snapshot.setTargetType(current.getTargetType());
        snapshot.setEnabled(current.getEnabled());
        snapshot.setRemark(current.getRemark());
        snapshot.setPublished(1);
        SqlWriters.insert(dsl, SqlTables.FORM_DEFINITION_VERSION, snapshot);
        return snapshot;
    }

    /** 回滚到指定版本：把快照 schema 写回主表，并再次发布为新版本（保留历史，不删除原快照）。 */
    @Transactional
    public FormDefinitionVersionDO rollback(Long formDefinitionId, int versionNo) {
        FormDefinitionDO current = requireDefinition(formDefinitionId);
        FormDefinitionVersionDO target = listVersions(formDefinitionId).stream()
                .filter(v -> v.getVersionNo() != null && v.getVersionNo() == versionNo)
                .findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND, "目标版本不存在: " + versionNo));
        if (!FormVersionRules.canRollback(currentVersionNo(formDefinitionId), versionNo)) {
            throw BusinessException.badRequest("目标版本即当前版本，无需回滚");
        }

        FormDefinitionDO update = new FormDefinitionDO();
        update.setSchemaJson(target.getSchemaJson());
        update.setFormName(target.getFormName());
        update.setTargetType(target.getTargetType());
        update.setEnabled(target.getEnabled());
        update.setRemark(target.getRemark());
        SqlWriters.updateById(dsl, SqlTables.FORM_DEFINITION, formDefinitionId, update);
        definitionService.refreshCache();
        return publish(formDefinitionId);
    }

    /** 版本间字段变更摘要（供前端展示 diff）。 */
    public List<String> diffFields(Long formDefinitionId, int fromVersionNo, int toVersionNo) {
        List<FormDefinitionVersionDO> versions = listVersions(formDefinitionId);
        String from = schemaOf(versions, fromVersionNo);
        String to = schemaOf(versions, toVersionNo);
        return FormVersionRules.diffFields(from, to);
    }

    private FormDefinitionDO requireDefinition(Long id) {
        FormDefinitionDO found = SqlWriters.fetchById(dsl, SqlTables.FORM_DEFINITION, FormDefinitionDO.class, id);
        if (found == null) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND, "表单定义不存在: " + id);
        }
        return found;
    }

    private Integer currentVersionNo(Long formDefinitionId) {
        List<FormDefinitionVersionDO> versions = listVersions(formDefinitionId);
        return versions.isEmpty() ? null : versions.get(versions.size() - 1).getVersionNo();
    }

    private static String schemaOf(List<FormDefinitionVersionDO> versions, int versionNo) {
        return versions.stream()
                .filter(v -> v.getVersionNo() != null && v.getVersionNo() == versionNo)
                .map(FormDefinitionVersionDO::getSchemaJson)
                .findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND, "版本不存在: " + versionNo));
    }
}
