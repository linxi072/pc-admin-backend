package com.acme.scaffold.monitor;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * N+1 查询探测：在同一线程（通常对应一次 HTTP 请求）内累计「同构 SQL」的执行次数，
 * 超过阈值即判定为 N+1 反模式。
 *
 * <p>同构指纹通过归一化得到：折叠空白，并将字面量（'...'）与数字替换为统一占位符，
 * 因此 {@code where id = 1} / {@code where id = 2} 这类仅参数不同的重复查询会被聚合计数。
 *
 * <p>探测状态按线程隔离（ThreadLocal），请求结束时由 {@link NPlusOneResetFilter} 调用
 * {@link #reset()} 清理，避免线程池复用时计数跨请求串台。本类无 Spring 依赖，便于离线单测。
 */
public class NPlusOneProbe {

    public static final int DEFAULT_THRESHOLD = 10;

    private static final Pattern LITERAL = Pattern.compile("'[^']*'|\\b\\d+(?:\\.\\d+)?\\b");
    private static final String TOKEN = "?";

    private final int threshold;
    private final ThreadLocal<Map<String, Integer>> counts = ThreadLocal.withInitial(ConcurrentHashMap::new);

    public NPlusOneProbe() {
        this(DEFAULT_THRESHOLD);
    }

    public NPlusOneProbe(int threshold) {
        this.threshold = Math.max(2, threshold);
    }

    public int threshold() {
        return threshold;
    }

    /** 归一化 SQL 为同构指纹（折叠空白 + 小写 + 字面量/数字替换为占位符 + 压缩 = 两侧空白）。 */
    public static String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        String collapsed = sql.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        collapsed = LITERAL.matcher(collapsed).replaceAll(TOKEN);
        // 压缩 = 两侧空白，使 `id = ?` 与 `id=?` 归于同一指纹
        collapsed = collapsed.replaceAll("\\s*=\\s*", "=");
        return collapsed;
    }

    /**
     * 记录一次 SQL 执行，返回是否「恰好跨越阈值」（即本次后该指纹计数等于阈值）。
     * 仅在跨越阈值的那一次返回 true，避免同一 N+1 在请求内反复刷屏。
     */
    public boolean record(String sql) {
        String key = normalize(sql);
        Map<String, Integer> map = counts.get();
        int n = map.merge(key, 1, Integer::sum);
        return n == threshold;
    }

    public int count(String sql) {
        return counts.get().getOrDefault(normalize(sql), 0);
    }

    /** 清理当前线程的探测状态（请求结束时调用）。 */
    public void reset() {
        counts.remove();
    }
}
