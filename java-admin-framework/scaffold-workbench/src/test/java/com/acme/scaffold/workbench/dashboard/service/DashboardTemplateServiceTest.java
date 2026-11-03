package com.acme.scaffold.workbench.dashboard.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workbench.dashboard.dto.DashboardTemplateVO;
import com.acme.scaffold.workbench.dashboard.dto.TemplateSaveRequest;
import com.acme.scaffold.workbench.dashboard.dto.TemplateUpdateRequest;
import com.acme.scaffold.workbench.dashboard.support.DashboardH2;
import com.acme.scaffold.workbench.dashboard.support.StubSecurityContextFacade;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 看板模板服务集成测试：H2 承载 DDL，直接实例化 Service，覆盖 CRUD、发布态流转、校验与分页筛选。
 */
class DashboardTemplateServiceTest {

    private DSLContext dsl;
    private DashboardTemplateService templateService;

    @BeforeEach
    void setUp() throws Exception {
        dsl = DashboardH2.newContext();
        templateService = new DashboardTemplateService(dsl, new StubSecurityContextFacade());
    }

    private long createTemplate(String code, String name, String status) {
        return createTemplate(code, name, status, "{\"rows\":[]}");
    }

    private long createTemplate(String code, String name, String status, String layoutJson) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setTemplateCode(code);
        req.setTemplateName(name);
        req.setStatus(status);
        req.setLayoutJson(layoutJson);
        return templateService.create(req);
    }

    @Test
    void createDefaultsToDraft() {
        long id = createTemplate("tpl-1", "首页看板", null);
        DashboardTemplateVO v = templateService.getById(id);
        assertEquals("DRAFT", v.getStatus());
        assertEquals("tpl-1", v.getTemplateCode());
        assertEquals("首页看板", v.getTemplateName());
        assertEquals("{\"rows\":[]}", v.getLayoutJson());
    }

    @Test
    void publishSetsStatus() {
        long id = createTemplate("tpl-2", "发布模板", "DRAFT");
        templateService.publish(id);
        assertEquals("PUBLISHED", templateService.getById(id).getStatus());
    }

    @Test
    void unpublishSetsOffline() {
        long id = createTemplate("tpl-3", "撤销模板", "PUBLISHED");
        templateService.unpublish(id);
        assertEquals("OFFLINE", templateService.getById(id).getStatus());
    }

    @Test
    void viewCountIncrements() {
        long id = createTemplate("tpl-4", "计数模板", "DRAFT");
        templateService.incrementViewCount(id);
        templateService.incrementViewCount(id);
        assertEquals(2L, templateService.getById(id).getViewCount());
    }

    @Test
    void updateChangesFields() {
        long id = createTemplate("tpl-5", "原名", "DRAFT");
        TemplateUpdateRequest req = new TemplateUpdateRequest();
        req.setId(id);
        req.setTemplateCode("tpl-5");
        req.setTemplateName("新名");
        req.setLayoutJson("{\"rows\":[1,2]}");
        req.setStatus("PUBLISHED");
        templateService.update(req);
        DashboardTemplateVO v = templateService.getById(id);
        assertEquals("新名", v.getTemplateName());
        assertEquals("{\"rows\":[1,2]}", v.getLayoutJson());
        assertEquals("PUBLISHED", v.getStatus());
    }

    @Test
    void deleteIsLogicalAndRecoverableQueryHidesIt() {
        long id = createTemplate("tpl-6", "删除模板", "DRAFT");
        templateService.delete(id);
        assertThrows(BusinessException.class, () -> templateService.getById(id));
        // 分页不应再返回已逻辑删除的模板
        PageResult<DashboardTemplateVO> page = templateService.page(1, 20, "tpl-6", null, null, "createdAt", "desc");
        assertEquals(0L, page.total());
    }

    @Test
    void pageFiltersByStatusAndName() {
        createTemplate("tpl-7", "苹果看板", "DRAFT");
        createTemplate("tpl-8", "香蕉看板", "PUBLISHED");
        createTemplate("tpl-9", "橙子看板", "DRAFT");
        PageResult<DashboardTemplateVO> draft = templateService.page(1, 20, null, null, "DRAFT", "createdAt", "desc");
        assertEquals(2L, draft.total());
        PageResult<DashboardTemplateVO> byName = templateService.page(1, 20, null, "香蕉", null, "createdAt", "desc");
        assertEquals(1L, byName.total());
    }

    @Test
    void createRejectsInvalidJson() {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setTemplateCode("tpl-10");
        req.setTemplateName("坏 JSON");
        req.setLayoutJson("{not valid json");
        assertThrows(BusinessException.class, () -> templateService.create(req));
    }

    @Test
    void missingTemplateThrowsOnGet() {
        assertThrows(BusinessException.class, () -> templateService.getById(999L));
    }
}
