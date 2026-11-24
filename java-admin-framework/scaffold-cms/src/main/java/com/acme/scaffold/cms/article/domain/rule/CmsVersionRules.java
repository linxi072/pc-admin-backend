package com.acme.scaffold.cms.article.domain.rule;

import java.util.List;
import java.util.Objects;

/** 文章版本规则（C4，纯函数）：版本号分配、是否为有效回滚目标、内容是否一致。 */
public final class CmsVersionRules {

    private CmsVersionRules() {
    }

    /** 下一个版本号：空历史返回 1，否则取最大值 +1（含溢出防护）。 */
    public static int nextVersionNo(List<Integer> existingVersionNos) {
        if (existingVersionNos == null || existingVersionNos.isEmpty()) {
            return 1;
        }
        int max = existingVersionNos.stream().filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0);
        return max >= Integer.MAX_VALUE ? max : Math.max(1, max + 1);
    }

    /** 是否允许回滚：目标存在且非当前版本。 */
    public static boolean canRollback(Integer currentVersionNo, Integer targetVersionNo) {
        return currentVersionNo != null && targetVersionNo != null
                && !currentVersionNo.equals(targetVersionNo);
    }

    /** 快照内容是否与当前实体一致（用于跳过无变更的重复快照）。 */
    public static boolean sameContent(String t1, String s1, String c1,
                                      String t2, String s2, String c2) {
        return eq(t1, t2) && eq(s1, s2) && eq(c1, c2);
    }

    private static boolean eq(String a, String b) {
        return Objects.equals(nullToEmpty(a), nullToEmpty(b));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
