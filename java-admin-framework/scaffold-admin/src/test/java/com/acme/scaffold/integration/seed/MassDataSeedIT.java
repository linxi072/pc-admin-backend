package com.acme.scaffold.integration.seed;

import com.acme.scaffold.common.audit.domain.SysOperationLogDO;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.entity.SysLoginLogDO;
import com.acme.scaffold.system.entity.SysMessageDO;
import com.acme.scaffold.system.entity.SysMessageReceiptDO;
import com.acme.scaffold.system.entity.SysMonitorSampleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.notifylog.domain.SysNotificationRecordDO;
import com.acme.scaffold.workflow.entity.WfApprovalRecordDO;
import com.acme.scaffold.workflow.entity.WfInstanceExtDO;
import com.acme.scaffold.workflow.entity.WfTaskExtDO;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 海量模拟测试数据生成器（QA 战役 #1073）。
 *
 * <p>基于手写 jOOQ DSL 的 {@link JooqWriters#batchInsert} 批量落库，还原真实业务场景：
 * 用户 / 登录日志 / 站内信(+回执) / 通知记录 / 操作审计 / 监控采样 / 工作流实例(+任务+审批)。
 * 数据关系真实化（登录日志、消息、流程均引用真实生成的 user_id / message_id）。</p>
 *
 * <p><b>规模</b>：由系统属性 {@code -Dseed.scale=SMALL|MEDIUM|LARGE} 控制（默认 SMALL）。
 * SMALL≈10 万行、MEDIUM≈100 万行、LARGE≈200 万行，单表覆盖 1w~100w 量级。</p>
 *
 * <p><b>边界值与异常输入</b>：每表在"正常批量"之外单独注入一小批异常行
 * （超长字段、非法枚举、未来/历史时间、unicode/emoji、空值、负计数等），
 * 数据库拒绝计入"异常拒绝计数"，作为下游校验/脱敏/导出健壮性的观测点。</p>
 *
 * <p><b>幂等与清理</b>：所有种子行通过 sentinel 标记（{@code created_by=-987654321}、
 * {@code trace_id='SEED'}、{@code starter/assignee/operator_user_id=-987654321}）写入，
 * 用例开头按 sentinel 清理，保证可重复运行不会因唯一约束冲突失败。</p>
 *
 * <p><b>探针</b>：写入阶段打印各表插入吞吐(rows/s)；收尾打印读取延迟探针
 * （count / 深翻页 90% / 无索引枚举过滤），供性能测试 #1075 报告引用。</p>
 */
@Tag("integration")
class MassDataSeedIT extends AbstractHttpIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MassDataSeedIT.class);

    /** 种子行 sentinel：所有 seeded 行的 created_by / 部分 user_id 字段写入此正值。
     *  须为正值——相关列（id/tenant_id/created_by 等）均为 BIGINT UNSIGNED，负值越界被拒。
     *  取 9 位定值，远低于雪花 ID（~1e17），不会与真实数据碰撞。 */
    private static final long SENTINEL = 987654321L;
    /** 种子行 sentinel：日志/通知/审计类的 trace_id 写入此值。 */
    private static final String SEED_TRACE = "SEED";
    /** 批量写入分块大小：任一时刻堆内仅保留一个分块，约束内存峰值。 */
    private static final int CHUNK = 2000;

    @Autowired
    private DSLContext dsl;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Random rnd = new Random(20260928L);

    // ------------------------------------------------------------------
    // 规模配置
    // ------------------------------------------------------------------

    /** 各表目标行数（LARGE 下合计约 200 万行）。 */
    private record Scale(int users, int loginLogs, int messages, int receiptsPerMsg,
                         int notifications, int operationLogs, int monitorSamples,
                         int wfInstances, int wfTasksPerInst, int wfApprovalsPerTask) {
        static Scale resolve(String name) {
            return switch (name.toUpperCase(Locale.ROOT)) {
                case "LARGE" -> new Scale(100_000, 400_000, 50_000, 3, 100_000, 50_000, 30_000, 20_000, 3, 2);
                case "MEDIUM" -> new Scale(50_000, 200_000, 20_000, 3, 50_000, 30_000, 20_000, 10_000, 3, 2);
                default -> new Scale(5_000, 20_000, 2_000, 3, 5_000, 3_000, 10_000, 1_000, 3, 2);
            };
        }
    }

    @Test
    void generateMassData() {
        String scaleName = System.getProperty("seed.scale", "SMALL");
        Scale s = Scale.resolve(scaleName);
        log.info("MassDataSeedIT: scale={} 解析为 {}", scaleName, s);

        cleanup();

        String pwdHash = passwordEncoder.encode("Seed@2026");

        // ---- 1) 用户（捕获 id 供下游引用）----
        List<Long> userIds = seedAndCollectIds(JooqTables.SYS_USER, s.users(), (i, u) -> {
            u.setUsername("seed_u_" + i);
            u.setPasswordHash(pwdHash);
            u.setDisplayName("种子用户" + i);
            u.setStatus(rnd.nextDouble() < 0.05 ? "DISABLED" : "ACTIVE");
            u.setMobile("138" + String.format("%08d", i % 100_000_000));
            u.setEmail("seed" + i + "@example.com");
            u.setCreatedBy(SENTINEL);
        }, SysUserDO::getId);

        int uc = userIds.size();
        assertTrue(uc > 0, "用户应生成成功");

        // ---- 2) 登录日志（引用真实用户）----
        seed(JooqTables.SYS_LOGIN_LOG, s.loginLogs(), i -> {
            SysLoginLogDO l = new SysLoginLogDO();
            long uid = userIds.get(i % uc);
            l.setUserId(uid);
            l.setUsername("seed_u_" + (i % uc));
            l.setLoginType(rnd.nextBoolean() ? "PASSWORD" : "SMS");
            l.setResult(rnd.nextDouble() < 0.85 ? "SUCCESS" : "FAILURE");
            l.setIpAddress(randomIp());
            l.setUserAgent("Mozilla/5.0 SeedAgent/1.0");
            l.setTraceId(SEED_TRACE);
            l.setOccurredAt(randomPastTime());
            return l;
        });

        // ---- 3) 站内信（捕获 id 供回执引用）----
        List<Long> messageIds = seedAndCollectIds(JooqTables.SYS_MESSAGE, s.messages(), (i, m) -> {
            m.setSenderId(userIds.get(i % uc));
            m.setTitle("种子消息" + i);
            m.setContent("这是一条自动生成的测试站内信内容 #" + i
                    + "，用于还原真实业务读取与导出场景。");
            m.setMsgType(rnd.nextBoolean() ? "NOTICE" : "ALERT");
            int rc = 1 + rnd.nextInt(5);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < rc; k++) {
                sb.append(userIds.get(rnd.nextInt(uc))).append(',');
            }
            m.setReceiverIds(sb.toString());
            m.setTotalCount(rc);
            m.setReadCount(0);
            m.setSentAt(randomPastTime());
            m.setCreatedBy(SENTINEL);
        }, SysMessageDO::getId);

        // ---- 4) 站内信回执（引用 message_id + 真实用户，制造未读数偏差）----
        // 每封消息 k 个回执取 (mIdx*R+k) 取模用户数，保证同一消息内接收人互异，
        // 避免触犯 uk_msg_user(tenant_id, message_id, user_id) 唯一约束。
        int receiptTotal = s.messages() * s.receiptsPerMsg();
        seed(JooqTables.SYS_MESSAGE_RECEIPT, receiptTotal, j -> {
            int mIdx = j / s.receiptsPerMsg();
            int k = j % s.receiptsPerMsg();
            SysMessageReceiptDO r = new SysMessageReceiptDO();
            long mid = messageIds.get(mIdx % Math.max(1, messageIds.size()));
            r.setMessageId(mid);
            r.setUserId(userIds.get((mIdx * s.receiptsPerMsg() + k) % uc));
            r.setIsRead(rnd.nextBoolean() ? 1 : 0);
            r.setReadAt(rnd.nextBoolean() ? randomPastTime() : null);
            return r;
        });

        // ---- 5) 通知发送记录 ----
        seed(JooqTables.SYS_NOTIFICATION_RECORD, s.notifications(), i -> {
            SysNotificationRecordDO n = new SysNotificationRecordDO();
            n.setChannel(pick(new String[]{"IN_APP", "EMAIL", "SMS", "WEBHOOK", "WEBSOCKET"}));
            n.setMsgType(rnd.nextBoolean() ? "NOTICE" : "ALERT");
            n.setTitleSummary("通知摘要" + i);
            n.setContentSummary("内容摘要" + i);
            n.setReceiverSummary("138****8000");
            n.setReceiverCount(1 + rnd.nextInt(100));
            n.setDelivered(rnd.nextBoolean() ? 1 : 0);
            n.setStatus(rnd.nextBoolean() ? "SUCCESS" : "FAILED");
            n.setOperatorId(userIds.get(rnd.nextInt(uc)));
            n.setOperatorName("seed_op");
            n.setTraceId(SEED_TRACE);
            n.setSentAt(randomPastTime());
            return n;
        });

        // ---- 6) 操作审计日志 ----
        seed(JooqTables.SYS_OPERATION_LOG, s.operationLogs(), i -> {
            SysOperationLogDO o = new SysOperationLogDO();
            o.setModuleCode(pick(new String[]{"USER", "ROLE", "MENU", "WORKFLOW", "NOTICE"}));
            o.setOperationType(pick(new String[]{"CREATE", "UPDATE", "DELETE", "QUERY", "EXPORT"}));
            o.setOperationName("种子操作" + i);
            o.setOperatorId(userIds.get(rnd.nextInt(uc)));
            o.setOperatorName("seed_op");
            o.setRequestMethod(pick(new String[]{"GET", "POST", "PUT", "DELETE"}));
            o.setRequestPath("/api/system/seed/" + i);
            // request_summary / result_summary 为 JSON 列，须写入合法 JSON 文本
            o.setRequestSummary("\"seed-request-" + i + "\"");
            o.setResultSummary("\"ok\"");
            o.setResultCode("200");
            o.setDurationMs((long) (rnd.nextInt(2000)));
            o.setIpAddress(randomIp());
            o.setTraceId(SEED_TRACE);
            o.setSuccess(1);
            o.setOccurredAt(randomPastTime());
            return o;
        });

        // ---- 7) 监控采样时间序列 ----
        seed(JooqTables.SYS_MONITOR_SAMPLE, s.monitorSamples(), i -> {
            SysMonitorSampleDO m = new SysMonitorSampleDO();
            m.setCpuUsage(BigDecimal.valueOf(rnd.nextInt(100)));
            m.setMemoryUsage(BigDecimal.valueOf(rnd.nextInt(100)));
            m.setSystemMemoryUsage(BigDecimal.valueOf(rnd.nextInt(100)));
            m.setDiskUsage(BigDecimal.valueOf(rnd.nextInt(100)));
            m.setUsedHeapBytes((long) (rnd.nextInt(2_000) * 1_000_000L));
            m.setMaxHeapBytes(4L * 1024 * 1024 * 1024);
            m.setUsedMemoryBytes((long) (rnd.nextInt(8_000) * 1_000_000L));
            m.setTotalMemoryBytes(16L * 1024 * 1024 * 1024);
            m.setOnlineUsers(rnd.nextInt(500));
            m.setActiveSessions(rnd.nextInt(300));
            m.setThreadCount(50 + rnd.nextInt(200));
            m.setSampledAt(LocalDateTime.now().minusMinutes(s.monitorSamples() - i));
            return m;
        });

        // ---- 8) 工作流实例 / 任务 / 审批（sentinel 标记，便于清理）----
        seed(JooqTables.WF_INSTANCE_EXT, s.wfInstances(), i -> {
            WfInstanceExtDO w = new WfInstanceExtDO();
            w.setProcessInstanceId("pi_seed_" + i);
            w.setProcessDefinitionId("pd_seed_" + (i % 20));
            w.setBusinessType(pick(new String[]{"LEAVE", "EXPENSE", "PURCHASE", "CONTRACT"}));
            w.setBusinessId("biz_" + i);
            w.setTitle("种子流程" + i);
            w.setStarterUserId(SENTINEL);
            w.setStarterOrgId(SENTINEL);
            w.setCurrentActivityId("act_" + (i % 10));
            w.setStatus(pick(new String[]{"RUNNING", "COMPLETED", "TERMINATED"}));
            w.setStartedAt(randomPastTime());
            w.setFinishedAt(rnd.nextBoolean() ? randomPastTime() : null);
            return w;
        });

        seed(JooqTables.WF_TASK_EXT, s.wfInstances() * s.wfTasksPerInst(), j -> {
            WfTaskExtDO t = new WfTaskExtDO();
            long inst = j / s.wfTasksPerInst();
            t.setTaskId("task_seed_" + j);
            t.setProcessInstanceId("pi_seed_" + inst);
            t.setActivityId("act_" + (j % 10));
            t.setActivityName("节点" + (j % 10));
            t.setAssigneeUserId(SENTINEL);
            t.setStatus(pick(new String[]{"PENDING", "CLAIMED", "COMPLETED", "DELEGATED"}));
            t.setApprovalMode(pick(new String[]{"AND", "OR", "SINGLE"}));
            t.setDueAt(randomFutureTime());
            t.setClaimedAt(rnd.nextBoolean() ? randomPastTime() : null);
            t.setCompletedAt(rnd.nextBoolean() ? randomPastTime() : null);
            return t;
        });

        seed(JooqTables.WF_APPROVAL_RECORD, s.wfInstances() * s.wfTasksPerInst() * s.wfApprovalsPerTask(), k -> {
            WfApprovalRecordDO a = new WfApprovalRecordDO();
            a.setOperationId("op_seed_" + k);
            a.setProcessInstanceId("pi_seed_" + (k % s.wfInstances()));
            a.setTaskId("task_seed_" + (k / s.wfApprovalsPerTask()));
            a.setActivityId("act_" + (k % 10));
            a.setAction(pick(new String[]{"APPROVE", "REJECT", "TRANSFER", "DELEGATE"}));
            a.setOperatorUserId(SENTINEL);
            a.setOpinion("种子审批意见" + k);
            a.setTraceId(SEED_TRACE);
            a.setOccurredAt(randomPastTime());
            return a;
        });

        // ---- 9) 异常 / 边界值注入（DB 拒绝计入发现）----
        injectAnomalies(uc);

        // ---- 10) 读取延迟探针 ----
        readProbes();

        log.info("MassDataSeedIT: 数据生成完成 scale={}", scaleName);
    }

    // ------------------------------------------------------------------
    // 通用分块写入
    // ------------------------------------------------------------------

    /** 分块批量写入，并收集回填的 id（用于关系引用）。 */
    private <T> List<Long> seedAndCollectIds(JooqTables.TableRef table, int total,
                                             ThrowingBiIntFunction<T> filler, Function<T, Long> idExtractor) {
        List<Long> ids = new ArrayList<>(total);
        long t0 = System.nanoTime();
        for (int i = 0; i < total; i += CHUNK) {
            int end = Math.min(total, i + CHUNK);
            List<T> buf = new ArrayList<>(end - i);
            for (int j = i; j < end; j++) {
                T pojo = newInstance(table);
                filler.apply(j, pojo);
                buf.add(pojo);
            }
            try {
                JooqWriters.batchInsert(dsl, table, buf);
            } catch (Exception e) {
                log.warn("SEED {} 分块写入失败(跳过该分块): {}", table.name(), e.getMessage());
            }
            for (T pojo : buf) {
                Long id = idExtractor.apply(pojo);
                if (id != null) ids.add(id);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("SEED {} 行数={} 耗时={}ms 吞吐={}行/s", table.name(), total, ms,
                ms > 0 ? String.format("%.1f", total * 1000.0 / ms) : "n/a");
        return ids;
    }

    /** 分块批量写入（无需收集 id）。 */
    private <T> void seed(JooqTables.TableRef table, int total, Function<Integer, T> factory) {
        long t0 = System.nanoTime();
        int done = 0;
        for (int i = 0; i < total; i += CHUNK) {
            int end = Math.min(total, i + CHUNK);
            List<T> buf = new ArrayList<>(end - i);
            for (int j = i; j < end; j++) buf.add(factory.apply(j));
            try {
                JooqWriters.batchInsert(dsl, table, buf);
                done += buf.size();
            } catch (Exception e) {
                log.warn("SEED {} 分块写入失败(跳过该分块): {}", table.name(), e.getMessage());
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("SEED {} 行数={} 耗时={}ms 吞吐={}行/s", table.name(), done, ms,
                ms > 0 ? String.format("%.1f", done * 1000.0 / ms) : "n/a");
    }

    @SuppressWarnings("unchecked")
    private <T> T newInstance(JooqTables.TableRef table) {
        try {
            if (table == JooqTables.SYS_USER) return (T) new SysUserDO();
            if (table == JooqTables.SYS_MESSAGE) return (T) new SysMessageDO();
            throw new IllegalStateException("unsupported table for id-collection: " + table.name());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // 异常 / 边界值注入
    // ------------------------------------------------------------------

    private void injectAnomalies(int uc) {
        int batch = 50;
        log.info("ANOMALY: 注入边界值/异常输入（每类 {} 行；语义异常预期被 DB 接受=缺乏校验，约束异常预期被 DB 拒绝）", batch);

        // 用户：语义异常（非法枚举 / 空显示名 / 非法邮箱格式）——预期被 DB 接受，证明缺乏应用层校验
        List<SysUserDO> uSem = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            SysUserDO u = new SysUserDO();
            u.setUsername("seed_anom_u_" + i);
            u.setPasswordHash(passwordEncoder.encode("Seed@2026"));
            u.setDisplayName(i % 3 == 0 ? "" : "异常用户" + i);
            u.setStatus(pick(new String[]{"ACTIVE", "DISABLED", "ZZTOP_INVALID", ""}));
            u.setEmail(i % 2 == 0 ? "not-an-email" : "a@b.com");
            u.setCreatedBy(SENTINEL);
            uSem.add(u);
        }
        int uSemAcc = tryInsert(JooqTables.SYS_USER, uSem);

        // 用户：约束异常（超长邮箱 >300 字符）——预期被 DB 拒绝
        List<SysUserDO> uCon = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            SysUserDO u = new SysUserDO();
            u.setUsername("seed_c_" + i);
            u.setPasswordHash(passwordEncoder.encode("Seed@2026"));
            u.setDisplayName("超长");
            u.setStatus("ACTIVE");
            u.setEmail("a".repeat(300) + "@x.com");
            u.setCreatedBy(SENTINEL);
            uCon.add(u);
        }
        int uConAcc = tryInsert(JooqTables.SYS_USER, uCon);

        // 登录日志：语义异常（非法结果枚举 / 非法 IP / 未来时间 / 空 userId）——预期被 DB 接受
        List<SysLoginLogDO> lSem = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            SysLoginLogDO l = new SysLoginLogDO();
            l.setUserId(i % 3 == 0 ? null : (long) (i % Math.max(1, uc)));
            l.setUsername("seed_u_" + i);
            l.setLoginType("PASSWORD");
            l.setResult(pick(new String[]{"SUCCESS", "FAILURE", "MAYBE_INVALID"}));
            l.setIpAddress(pick(new String[]{"192.168.1.1", "999.999.999.999", "::1", "not_ip"}));
            l.setUserAgent("UAgent");
            l.setTraceId(SEED_TRACE);
            l.setOccurredAt(i % 2 == 0 ? randomFutureTime() : randomPastTime());
            lSem.add(l);
        }
        int lSemAcc = tryInsert(JooqTables.SYS_LOGIN_LOG, lSem);

        // 登录日志：约束异常（user_agent 超长 >500）——预期被 DB 拒绝
        List<SysLoginLogDO> lCon = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            SysLoginLogDO l = new SysLoginLogDO();
            l.setUserId((long) (i % Math.max(1, uc)));
            l.setUsername("seed_u_" + i);
            l.setLoginType("PASSWORD");
            l.setResult("SUCCESS");
            l.setIpAddress("192.168.1.1");
            l.setUserAgent("U".repeat(600));
            l.setTraceId(SEED_TRACE);
            l.setOccurredAt(randomPastTime());
            lCon.add(l);
        }
        int lConAcc = tryInsert(JooqTables.SYS_LOGIN_LOG, lCon);

        // 通知记录：语义异常（非法渠道枚举 / 非法状态 / 负接收数 / delivered 越界）——预期被 DB 接受
        List<SysNotificationRecordDO> nSem = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            SysNotificationRecordDO n = new SysNotificationRecordDO();
            n.setChannel(pick(new String[]{"IN_APP", "EMAIL", "FAX_INVALID", "PAGER"}));
            n.setMsgType("NOTICE");
            n.setTitleSummary("异常通知" + i);
            n.setContentSummary("x");
            n.setReceiverSummary("138****8000");
            n.setReceiverCount(i % 2 == 0 ? -1 : 1);
            n.setDelivered(i % 2 == 0 ? 2 : 0);
            n.setStatus(pick(new String[]{"SUCCESS", "FAILED", "QUEUED_INVALID"}));
            n.setTraceId(SEED_TRACE);
            n.setSentAt(randomPastTime());
            nSem.add(n);
        }
        int nSemAcc = tryInsert(JooqTables.SYS_NOTIFICATION_RECORD, nSem);

        // 监控采样：语义异常（CPU 250% / -10%）——预期被 DB 接受
        List<SysMonitorSampleDO> mSem = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            SysMonitorSampleDO m = new SysMonitorSampleDO();
            m.setCpuUsage(BigDecimal.valueOf(i % 2 == 0 ? 250 : -10));
            m.setMemoryUsage(BigDecimal.valueOf(50));
            m.setSampledAt(LocalDateTime.now().minusSeconds(i));
            mSem.add(m);
        }
        int mSemAcc = tryInsert(JooqTables.SYS_MONITOR_SAMPLE, mSem);

        // 工作流实例：语义异常（非法状态 / 未来开始时间）——预期被 DB 接受
        List<WfInstanceExtDO> wSem = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            WfInstanceExtDO w = new WfInstanceExtDO();
            w.setProcessInstanceId("pi_anom_" + i);
            w.setProcessDefinitionId("pd_anom");
            w.setBusinessId("biz_anom_" + i);
            w.setBusinessType("LEAVE");
            w.setTitle("异常流程" + i);
            w.setStarterUserId(SENTINEL);
            w.setStarterOrgId(SENTINEL);
            w.setStatus(pick(new String[]{"RUNNING", "FROZEN_INVALID"}));
            w.setStartedAt(i % 2 == 0 ? randomFutureTime() : randomPastTime());
            wSem.add(w);
        }
        int wSemAcc = tryInsert(JooqTables.WF_INSTANCE_EXT, wSem);

        log.info("ANOMALY 接受/拒绝 => 用户语义={}/{} 用户约束={}/{} | 日志语义={}/{} 日志约束={}/{} "
                        + "| 通知语义={}/{} 监控语义={}/{} 流程语义={}/{}",
                uSemAcc, batch - uSemAcc, uConAcc, batch - uConAcc,
                lSemAcc, batch - lSemAcc, lConAcc, batch - lConAcc,
                nSemAcc, batch - nSemAcc, mSemAcc, batch - mSemAcc, wSemAcc, batch - wSemAcc);
    }

    /** 尝试整批写入；成功返回接受行数，失败（DB 拒绝）返回 0。 */
    private <T> int tryInsert(JooqTables.TableRef table, List<T> list) {
        try {
            JooqWriters.batchInsert(dsl, table, list);
            return list.size();
        } catch (Exception e) {
            log.warn("ANOMALY {} 被数据库拒绝(整批未写入): {}", table.name(), e.getMessage());
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // 读取延迟探针
    // ------------------------------------------------------------------

    private void readProbes() {
        log.info("READ-PROBE: 开始读取延迟探针");
        // count 全表
        long t0 = System.nanoTime();
        Long userCount = dsl.selectCount().from(JooqTables.SYS_USER.table()).fetchOne(0, Long.class);
        long countMs = (System.nanoTime() - t0) / 1_000_000;

        // 深翻页（offset ≈ 90% 总量）
        long off = (long) (userCount * 0.9);
        Field<Long> idF = JooqTables.SYS_USER.field("id", Long.class);
        t0 = System.nanoTime();
        int deepRows = dsl.selectFrom(JooqTables.SYS_USER.table())
                .orderBy(idF).limit(20).offset((int) Math.min(off, Integer.MAX_VALUE - 1)).fetch().size();
        long deepMs = (System.nanoTime() - t0) / 1_000_000;

        // 无索引枚举过滤（sys_login_log.result 大概率无单列索引）
        Field<String> resF = JooqTables.SYS_LOGIN_LOG.field("result", String.class);
        t0 = System.nanoTime();
        Long successLogs = dsl.selectCount().from(JooqTables.SYS_LOGIN_LOG.table())
                .where(resF.eq("SUCCESS")).fetchOne(0, Long.class);
        long filterMs = (System.nanoTime() - t0) / 1_000_000;

        // 关联聚合（站内信 + 回执 计数）
        t0 = System.nanoTime();
        Long receiptCount = dsl.selectCount().from(JooqTables.SYS_MESSAGE_RECEIPT.table()).fetchOne(0, Long.class);
        long joinMs = (System.nanoTime() - t0) / 1_000_000;

        log.info("READ-PROBE: userCount={} countMs={} | 深翻页 offset={} rows={} deepMs={} "
                        + "| login.result=SUCCESS count={} filterMs={} | receiptCount={} joinCountMs={}",
                userCount, countMs, off, deepRows, deepMs, successLogs, filterMs, receiptCount, joinMs);
    }

    // ------------------------------------------------------------------
    // 幂等清理（sentinel 标记）
    // ------------------------------------------------------------------

    private void cleanup() {
        log.info("CLEANUP: 按 sentinel 清理历史种子数据");
        dsl.deleteFrom(JooqTables.SYS_USER.table())
                .where(JooqTables.SYS_USER.field("created_by", Long.class).eq(SENTINEL)).execute();
        // 回执须在消息删除前通过子查询关联清理（否则子查询找不到已删消息）
        dsl.execute("delete from sys_message_receipt where message_id in "
                + "(select id from sys_message where created_by = ?)", SENTINEL);
        dsl.deleteFrom(JooqTables.SYS_MESSAGE.table())
                .where(JooqTables.SYS_MESSAGE.field("created_by", Long.class).eq(SENTINEL)).execute();
        dsl.deleteFrom(JooqTables.SYS_LOGIN_LOG.table())
                .where(JooqTables.SYS_LOGIN_LOG.field("trace_id", String.class).eq(SEED_TRACE)).execute();
        dsl.deleteFrom(JooqTables.SYS_NOTIFICATION_RECORD.table())
                .where(JooqTables.SYS_NOTIFICATION_RECORD.field("trace_id", String.class).eq(SEED_TRACE)).execute();
        dsl.deleteFrom(JooqTables.SYS_OPERATION_LOG.table())
                .where(JooqTables.SYS_OPERATION_LOG.field("trace_id", String.class).eq(SEED_TRACE)).execute();
        dsl.deleteFrom(JooqTables.SYS_MONITOR_SAMPLE.table()).execute(); // 纯测试时间序列
        dsl.deleteFrom(JooqTables.WF_APPROVAL_RECORD.table())
                .where(JooqTables.WF_APPROVAL_RECORD.field("operator_user_id", Long.class).eq(SENTINEL)).execute();
        dsl.deleteFrom(JooqTables.WF_TASK_EXT.table())
                .where(JooqTables.WF_TASK_EXT.field("assignee_user_id", Long.class).eq(SENTINEL)).execute();
        dsl.deleteFrom(JooqTables.WF_INSTANCE_EXT.table())
                .where(JooqTables.WF_INSTANCE_EXT.field("starter_user_id", Long.class).eq(SENTINEL)).execute();
        log.info("CLEANUP: 完成");
    }

    // ------------------------------------------------------------------
    // 随机工具
    // ------------------------------------------------------------------

    private String pick(String[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }

    private String randomIp() {
        return rnd.nextInt(223) + 1 + "." + rnd.nextInt(256) + "." + rnd.nextInt(256) + "." + rnd.nextInt(254);
    }

    private LocalDateTime randomPastTime() {
        return LocalDateTime.now()
                .minusDays(rnd.nextInt(365))
                .minusHours(rnd.nextInt(24))
                .minusMinutes(rnd.nextInt(60));
    }

    private LocalDateTime randomFutureTime() {
        return LocalDateTime.now().plusDays(30 + rnd.nextInt(335)).plusHours(rnd.nextInt(24));
    }

    /** 接受下标与 POJO 的填充函数（POJO 由 newInstance 预创建）。 */
    @FunctionalInterface
    private interface ThrowingBiIntFunction<T> {
        void apply(int index, T pojo);
    }
}
