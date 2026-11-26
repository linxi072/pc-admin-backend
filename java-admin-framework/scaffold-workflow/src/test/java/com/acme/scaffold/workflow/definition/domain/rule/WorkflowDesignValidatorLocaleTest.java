package com.acme.scaffold.workflow.definition.domain.rule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.acme.scaffold.workflow.definition.dto.design.WorkflowNodeView;
import com.acme.scaffold.workflow.support.WorkflowNodeType;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 审批配置 Locale 隔离：RATIO / INITIATOR / PREVIOUS 在 tr-TR 下仍为合法取值。 */
class WorkflowDesignValidatorLocaleTest {

    private Locale originalLocale;

    @BeforeEach
    void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void approvalConfigShouldValidateUnderTurkishLocale() throws Exception {
        Locale.setDefault(new Locale("tr", "TR"));
        Method validateApproval = WorkflowDesignValidator.class
                .getDeclaredMethod("validateApproval", WorkflowNodeView.class);
        validateApproval.setAccessible(true);
        WorkflowNodeView node = new WorkflowNodeView(
                "approve1", WorkflowNodeType.APPROVAL, "审批",
                "ratio", BigDecimal.valueOf(0.5),
                "initiator", null, 0, 0, null,
                "previous", null, null, null);

        assertDoesNotThrow(() -> validateApproval.invoke(null, node));
    }
}
