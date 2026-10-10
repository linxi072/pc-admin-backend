package com.acme.scaffold.security.password;

import com.acme.scaffold.system.entity.SysPasswordHistoryDO;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 密码历史复用判定纯规则（无 Spring / 无 DB 依赖，便于离线单测）。
 *
 * <p>设计文档 §5.3：「保留最近 5 次密码哈希，禁止重复使用」。
 * 判定与历史裁剪均为无副作用函数，仅以参数注入必要的 {@link PasswordEncoder}（仅用于哈希匹配）。
 */
public final class PasswordHistoryRules {

    private PasswordHistoryRules() {
    }

    /**
     * 是否复用历史口令：候选明文与最近若干条历史哈希任一匹配即视为复用。
     *
     * @param candidateRaw 待校验的明文口令
     * @param recentHashes 最近若干条历史哈希（密文）
     * @param encoder      密码匹配器（须与存储哈希算法一致；测试可用 NoOp / BCrypt）
     * @return 命中任一历史哈希返回 {@code true}
     */
    public static boolean isReused(String candidateRaw, List<String> recentHashes, PasswordEncoder encoder) {
        if (candidateRaw == null || recentHashes == null || recentHashes.isEmpty()) {
            return false;
        }
        for (String hash : recentHashes) {
            if (hash != null && encoder.matches(candidateRaw, hash)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算超出保留上限、需物理删除的历史记录 id。
     * 按 {@code createdAt} 降序保留最近 {@code limit} 条，其余返回待删除 id（从旧到新）。
     *
     * @param all   该用户的全部历史记录（无序）
     * @param limit 保留条数上限（设计文档约定 5）
     * @return 需要删除的记录主键列表
     */
    public static List<Long> idsToPrune(List<SysPasswordHistoryDO> all, int limit) {
        if (all == null || all.size() <= limit) {
            return List.of();
        }
        List<SysPasswordHistoryDO> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(SysPasswordHistoryDO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<Long> prune = new ArrayList<>();
        for (int i = limit; i < sorted.size(); i++) {
            prune.add(sorted.get(i).getId());
        }
        return prune;
    }
}
