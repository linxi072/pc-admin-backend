package com.acme.scaffold.security.password;

import com.acme.scaffold.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 密码历史读写服务：落地设计文档 §5.3「保留最近 5 次密码哈希，禁止重复使用」。
 *
 * <ul>
 *   <li>{@link #record(Long, String)}：落库一次口令变更（密文），随后裁剪超出上限的旧记录；</li>
 *   <li>{@link #isReused(Long, String)}：取最近若干条历史哈希比对，判定候选明文是否复用。</li>
 * </ul>
 *
 * <p>与 {@link PasswordHistoryRules} 配合：规则类承载无副作用判定，本服务承载数据访问，
 * 二者均不直接依赖 {@code system.entity}——数据读写经 {@link PasswordHistoryDataPort}（system 适配器实现）完成，
 * 满足模块边界（B1/B5 同款防腐层约束）。</p>
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private final PasswordHistoryDataPort passwordHistoryDataPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    /** 记录一次口令变更（密文），并裁剪超出保留上限的旧记录。 */
    @Transactional
    public void record(Long userId, String encodedPasswordHash) {
        passwordHistoryDataPort.insert(userId, encodedPasswordHash, LocalDateTime.now());
        prune(userId);
    }

    /** 候选明文是否与最近若干次历史口令重复。 */
    public boolean isReused(Long userId, String candidateRaw) {
        int limit = Math.max(1, jwtProperties.getPasswordHistoryLimit());
        List<String> hashes = passwordHistoryDataPort.recentHashes(userId, limit);
        return PasswordHistoryRules.isReused(candidateRaw, hashes, passwordEncoder);
    }

    private void prune(Long userId) {
        int limit = Math.max(1, jwtProperties.getPasswordHistoryLimit());
        for (Long id : PasswordHistoryRules.idsToPrune(passwordHistoryDataPort.allRecords(userId), limit)) {
            passwordHistoryDataPort.delete(id);
        }
    }
}
