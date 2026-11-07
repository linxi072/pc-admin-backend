package com.acme.scaffold.common.constant;

import com.acme.scaffold.common.api.PagingRules;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.common.api.dto.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CommonConstants} 单测：把「跨模块字面值逐字不变」编码成回归契约。
 *
 * <p>本轮把散落在 16 处的分页上限、时间格式、头像前缀与成功码改为委托引用。
 * 委托必须保持字面值与行为完全一致；任何一处后续被误改都会在这里或对应服务测试中暴露。</p>
 */
class CommonConstantsTest {

    @Test
    @DisplayName("字面值：与收敛前各模块散落的常量逐字一致")
    void literalValuesRemainUnchanged() {
        assertEquals(100, CommonConstants.MAX_PAGE_SIZE);
        assertEquals("yyyy-MM-dd HH:mm:ss", CommonConstants.DATE_TIME_PATTERN);
        assertEquals("yyyy-MM-dd HH:mm", CommonConstants.DATE_MINUTE_PATTERN);
        assertEquals("/uploads/avatar/", CommonConstants.AVATAR_URL_PREFIX);
        assertEquals("0", CommonConstants.SUCCESS_CODE);
    }

    @Test
    @DisplayName("委托：分页上限的四个既有公开字段仍与常量同源，钳制行为不变")
    void pagingDelegatesShareSingleSource() {
        assertEquals(CommonConstants.MAX_PAGE_SIZE, PagingRules.MAX_PAGE_SIZE);
        assertEquals(CommonConstants.MAX_PAGE_SIZE, PageQuery.MAX_SIZE);
        assertEquals(CommonConstants.MAX_PAGE_SIZE, KeysetPageQuery.MAX_SIZE);
        assertEquals(100, PageQuery.of(1, 1000).size());
        assertEquals(100, KeysetPageQuery.of(1000).limit());
        assertEquals(100L, PagingRules.offsetOf(2, 1000));
    }

    @Test
    @DisplayName("委托：统一响应成功码仍是 api-contract.json 约定的字符串 0")
    void successCodeDrivesResultEnvelope() {
        Result<String> ok = Result.success("payload");
        assertEquals(CommonConstants.SUCCESS_CODE, ok.code());
        assertEquals("0", ok.code());
        assertEquals("OK", ok.message());
        assertEquals("payload", ok.data());
        assertTrue(ok.isSuccess());
    }

    @Test
    @DisplayName("时间格式：两种 pattern 均可解析且渲染结果不变")
    void datePatternsRenderAsBefore() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 7, 22, 30, 45);
        assertEquals("2026-11-07 22:30:45",
                DateTimeFormatter.ofPattern(CommonConstants.DATE_TIME_PATTERN).format(at));
        assertEquals("2026-11-07 22:30",
                DateTimeFormatter.ofPattern(CommonConstants.DATE_MINUTE_PATTERN).format(at));
    }

    @Test
    @DisplayName("常量持有者不可实例化")
    void constantsHolderIsNotInstantiable() throws Exception {
        Constructor<CommonConstants> ctor = CommonConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }
}
