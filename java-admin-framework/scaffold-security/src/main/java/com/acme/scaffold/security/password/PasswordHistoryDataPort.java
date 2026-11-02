package com.acme.scaffold.security.password;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 密码历史数据访问端口（防腐层）。
 *
 * <p>由 {@code security.password} 定义、仅返回抽象类型（或 security 自有 record），
 * system 侧提供适配器实现，承载对 {@code sys_password_history} 表的 jOOQ 读写，
 * 从而消除 security 包对 {@code system.entity} 的直连。</p>
 */
public interface PasswordHistoryDataPort {

    /** 记录一次口令变更（密文）。 */
    void insert(Long userId, String encodedHash, LocalDateTime createdAt);

    /** 取最近若干条历史哈希（按 {@code created_at} 降序），用于复用判定。 */
    List<String> recentHashes(Long userId, int limit);

    /** 取该用户全部历史记录（轻量快照，用于超限裁剪）。 */
    List<PasswordHistoryRecord> allRecords(Long userId);

    /** 物理删除指定历史记录（裁剪超出保留上限的旧记录）。 */
    void delete(Long id);
}
