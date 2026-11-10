package com.acme.scaffold.system.menu.domain.rule;

import java.util.Set;

/**
 * 菜单类型取值域（纯函数规则）。
 *
 * <p>若依规范对齐 P0-2 的第二半：{@code sys_menu.menu_type} 此前是任意
 * {@code VARCHAR(20)}，既无数据库 CHECK 也无应用层校验，写入拼错的类型会让菜单树
 * 出现无法归类的节点。此处以纯函数收敛取值域，由 {@code MenuService} 在写入前调用。
 *
 * <p><b>刻意覆盖两套字面量且不归一化</b>：后端 V32 基线种子使用全名
 * {@code CATALOG} / {@code MENU}，而前端 {@code MenuView.vue} 沿用若依的单字母
 * {@code C} / {@code M} / {@code B}。若只放行全名，前端新建菜单会直接 400；
 * 若在此归一化（如 M → MENU），前端 {@code typeLabel} 又会把目录错显为菜单。
 * 因此两套都视为合法输入、原样落库，只拦截集合外的值 —— 零行为变更。
 *
 * <p>不引入 {@code BUTTON} 之外的数据库 CHECK 约束：存量库可能已有历史取值，
 * 结构级约束会造成升级失败，应用层校验是本仓既有范式（见各簇 {@code *Rules}）。
 */
public final class MenuTypeRules {

    /** 允许的菜单类型：后端全名 3 个 + 前端若依单字母 3 个。 */
    private static final Set<String> ALLOWED = Set.of(
            "CATALOG", "MENU", "BUTTON",
            "C", "M", "B");

    private MenuTypeRules() {
    }

    /** 是否为合法菜单类型；{@code null} / 空白 / 集合外取值均返回 {@code false}。 */
    public static boolean isValid(String menuType) {
        return menuType != null && ALLOWED.contains(menuType);
    }

    /** 供错误提示使用的合法取值说明。 */
    public static String allowedDescription() {
        return "CATALOG/MENU/BUTTON 或 C/M/B";
    }
}
