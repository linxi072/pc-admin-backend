package com.acme.scaffold.workbench;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作台统计：供首页统计卡片使用的聚合指标。
 *
 * <p>全部走 jOOQ 聚合（{@code selectCount}），不加载明细实体，避免全表扫描与内存膨胀。
 * 指标分两类：
 * <ul>
 *   <li><b>周期型</b>：按时间窗统计并给出「上一周期」对比，可计算真实趋势
 *       （今日新增用户 / 今日发起流程：今日 vs 昨日）；</li>
 *   <li><b>快照型</b>：统计当前存量（我的待办 / 运行中流程 / 在线接口），
 *       无历史基线，趋势记持平，不伪造涨跌。</li>
 * </ul>
 * <p>「我的待办」按当前登录人收敛，其余为全局口径。
 */
@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private final DSLContext dsl;

    /** 返回工作台统计卡片数据。 */
    public WorkbenchStatsVO stats(Long userId) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        List<StatCardVO> cards = new ArrayList<>();

        // 1) 今日新增用户（周期型：今日 vs 昨日）
        long newUserToday = countBetween(JooqTables.SYS_USER, "created_at", todayStart, tomorrowStart);
        long newUserYesterday = countBetween(JooqTables.SYS_USER, "created_at", yesterdayStart, todayStart);
        cards.add(StatCardVO.of("NEW_USER_TODAY", "今日新增用户", newUserToday, newUserYesterday, "人"));

        // 2) 今日发起流程（周期型：今日 vs 昨日）
        long newInstanceToday = countBetween(JooqTables.WF_INSTANCE_EXT, "started_at",
                todayStart, tomorrowStart);
        long newInstanceYesterday = countBetween(JooqTables.WF_INSTANCE_EXT, "started_at",
                yesterdayStart, todayStart);
        cards.add(StatCardVO.of("NEW_INSTANCE_TODAY", "今日发起流程",
                newInstanceToday, newInstanceYesterday, "个"));

        // 3) 我的待办（快照型：按当前登录人过滤，未认领/未完成的待办任务）
        long myTodo = userId == null ? 0L : JooqWriters.count(dsl, JooqTables.WF_TASK_EXT, DSL.and(
                JooqWriters.notDeleted(JooqTables.WF_TASK_EXT),
                JooqTables.WF_TASK_EXT.field("assignee_user_id", Long.class).eq(userId),
                JooqTables.WF_TASK_EXT.field("status", String.class).eq("PENDING")));
        cards.add(StatCardVO.snapshot("MY_TODO_TASKS", "我的待办", myTodo, "条"));

        // 4) 运行中流程（快照型）
        long running = JooqWriters.count(dsl, JooqTables.WF_INSTANCE_EXT, DSL.and(
                JooqWriters.notDeleted(JooqTables.WF_INSTANCE_EXT),
                JooqTables.WF_INSTANCE_EXT.field("status", String.class).eq("RUNNING")));
        cards.add(StatCardVO.snapshot("RUNNING_INSTANCE", "运行中流程", running, "个"));

        // 5) 在线接口（快照型：已登记且启用的接口资源）
        long activeApi = JooqWriters.count(dsl, JooqTables.SYS_API_RESOURCE, DSL.and(
                JooqWriters.notDeleted(JooqTables.SYS_API_RESOURCE),
                JooqTables.SYS_API_RESOURCE.field("status", String.class).eq("ACTIVE")));
        cards.add(StatCardVO.snapshot("ACTIVE_API", "在线接口", activeApi, "个"));

        return new WorkbenchStatsVO(List.copyOf(cards));
    }

    /** 统计指定时间窗 [from, to) 内的记录数（自动带逻辑删除过滤）。 */
    private long countBetween(JooqTables.TableRef table, String timeColumn,
                              LocalDateTime from, LocalDateTime to) {
        return JooqWriters.count(dsl, table, DSL.and(
                JooqWriters.notDeleted(table),
                table.field(timeColumn, LocalDateTime.class).ge(from),
                table.field(timeColumn, LocalDateTime.class).lt(to)));
    }
}
