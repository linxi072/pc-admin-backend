package com.acme.scaffold.workflow.adapter.step;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StepUserIds} 单元测试：覆盖流程变量的三种元素形态与异常输入。
 *
 * <p>该方法原为 {@code CcStepDelegate} / {@code GenericStepDelegate} 各一份的私有拷贝
 * （逐字节相同、零测试覆盖），提取后由本测试锁定契约。
 */
class StepUserIdsTest {

    @Test
    @DisplayName("null 与非 List 变量返回空列表")
    void nullAndNonListYieldEmpty() {
        assertThat(StepUserIds.parseUserIds(null)).isEmpty();
        assertThat(StepUserIds.parseUserIds("not-a-list")).isEmpty();
        assertThat(StepUserIds.parseUserIds(42L)).isEmpty();
    }

    @Test
    @DisplayName("Long 元素原样保留")
    void longElementsPassThrough() {
        List<Long> ids = StepUserIds.parseUserIds(List.of(1L, 2L, 3L));
        assertThat(ids).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("数字字符串解析为 Long，非数字字符串静默跳过")
    void stringElementsParsedOrSkipped() {
        List<Long> ids = StepUserIds.parseUserIds(List.of("10", "  ", "abc", "20"));
        assertThat(ids).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("其他 Number 形态（Integer/Double）按 longValue 转换")
    void otherNumberFormsConverted() {
        List<Long> ids = StepUserIds.parseUserIds(List.of(7, 8.0));
        assertThat(ids).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("混合形态保序解析，null 元素跳过")
    void mixedElementsKeepOrder() {
        // Arrays.asList 允许 null 元素（List.of 会抛 NPE），用于验证 null 元素被跳过
        List<Long> ids = StepUserIds.parseUserIds(Arrays.asList(1L, "2", 3, null, "x", 4L));
        assertThat(ids).containsExactly(1L, 2L, 3L, 4L);
    }
}
