package com.acme.scaffold.cms.category.domain.rule;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 栏目业务规则（C1/C2，纯函数）：编码规范、层级引用判定、删除引用判定。
 *
 * <p>把编码格式与删除可行性从 Service 私有逻辑收口，供创建/更新/删除与单测共用。</p>
 */
public final class CmsCategoryRules {

    /** 栏目编码：小写字母开头，允许小写字母/数字/连字符，长度 2~64。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");

    private CmsCategoryRules() {
    }

    /** 编码规范化：去空白并转小写（与 DDL UNIQUE 索引的大小写口径一致）。 */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validCode(String code) {
        return CODE_PATTERN.matcher(normalizeCode(code)).matches();
    }

    /** parentId=0/null 归一为 null（根节点）。 */
    public static Long normalizeParentId(Long parentId) {
        return parentId == null || parentId == 0L ? null : parentId;
    }

    /** 自引用防护：parentId 不能等于自身 id。 */
    public static boolean isSelfParent(Long id, Long parentId) {
        return id != null && parentId != null && id.equals(parentId);
    }

    /** 删除可行性（C2）：无子栏目且无关联文章才可删。 */
    public static boolean canDelete(long childCount, long articleCount) {
        return childCount == 0 && articleCount == 0;
    }

    /** 删除拒绝原因（供 Service 构造业务异常文案，口径单一）。 */
    public static String deleteRejectMessage(long childCount, long articleCount) {
        if (childCount > 0 && articleCount > 0) {
            return "栏目下存在 " + childCount + " 个子栏目、关联 " + articleCount + " 篇文章，无法删除";
        }
        if (childCount > 0) {
            return "栏目下存在 " + childCount + " 个子栏目，无法删除";
        }
        if (articleCount > 0) {
            return "栏目下关联 " + articleCount + " 篇文章，无法删除";
        }
        return "";
    }
}
