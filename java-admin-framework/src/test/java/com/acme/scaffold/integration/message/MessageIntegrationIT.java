package com.acme.scaffold.integration.message;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.entity.SysMessageDO;
import com.acme.scaffold.system.entity.SysMessageReceiptDO;
import com.acme.scaffold.system.entity.SysUserDO;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 站内信批量发送集成测试：验证「发送接口 → 消息落库 → 收件明细批量写入」端到端，
 * 并重点断言 M2/M3 批量写入缺陷修复后「收件明细条数 == 接收人数」（无漏写、无重复）。
 *
 * <p>运行前提：同 {@link com.acme.scaffold.integration.menu.MenuIntegrationIT}。
 * 注意：本类走真实 HTTP 端口，发送会在库中落盘（测试用唯一 title 标识，便于排查）；
 * 若 admin 实际未授予 {@code system:message:send} 权限，主一致性断言将自动 skip，不会误报失败。
 *
 * 运行：{@code mvn -o test -Dtest=MessageIntegrationIT -Dspring.profiles.active=integration ...}
 */
class MessageIntegrationIT extends AbstractHttpIntegrationTest {

    @Autowired
    private DSLContext dsl;

    /** 权限探测：空接收人应进入业务校验（400），而非权限拒绝（403）；403 表示 admin 无发送权限。 */
    @Test
    @SuppressWarnings("unchecked")
    void sendCreatesOneReceiptPerReceiver() {
        String token = login("admin", "admin123");
        ResponseEntity<Result> probe = post("/api/system/messages/send",
                Map.of("title", "probe", "content", "x"), Result.class, token);
        assumeTrue(probe.getStatusCode().value() != 403,
                "admin 无 system:message:send 权限，跳过发送一致性断言");

        // 取两个真实存在的用户作为接收人（doSend 会过滤不存在的 userId）
        List<SysUserDO> users = JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_USER));
        assertTrue(users.size() >= 2, "库中应至少存在 2 个用户用于接收");
        List<Long> receivers = List.of(users.get(0).getId(), users.get(1).getId());

        String title = "IT-msg-" + System.nanoTime();
        Map<String, Object> req = Map.of(
                "title", title,
                "content", "集成测试站内信内容",
                "receiverIds", receivers);
        ResponseEntity<Result> resp = post("/api/system/messages/send", req, Result.class, token);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().isSuccess());

        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        int receiverCount = ((Number) data.get("receiverCount")).intValue();
        assertEquals(2, receiverCount, "返回的实际投递数应等于接收人数");

        // 反查刚发送的消息（按唯一 title），验证收件明细条数 == 接收人数（M2/M3 批量写入）
        List<SysMessageDO> msgs = JooqWriters.fetchList(dsl, JooqTables.SYS_MESSAGE, SysMessageDO.class,
                JooqTables.SYS_MESSAGE.field("title", String.class).eq(title));
        assertFalse(msgs.isEmpty());
        SysMessageDO msg = msgs.get(msgs.size() - 1);

        long receiptCount = JooqWriters.count(dsl, JooqTables.SYS_MESSAGE_RECEIPT,
                JooqTables.SYS_MESSAGE_RECEIPT.field("message_id", Long.class).eq(msg.getId()));
        assertEquals(2L, receiptCount, "批量写入应生成与接收人数一致的收件明细，不得漏写");

        List<SysMessageReceiptDO> receipts = JooqWriters.fetchList(dsl, JooqTables.SYS_MESSAGE_RECEIPT,
                SysMessageReceiptDO.class,
                JooqTables.SYS_MESSAGE_RECEIPT.field("message_id", Long.class).eq(msg.getId()));
        Set<Long> actualReceivers = new HashSet<>();
        for (SysMessageReceiptDO rc : receipts) {
            actualReceivers.add(rc.getUserId());
        }
        assertEquals(new HashSet<>(receivers), actualReceivers, "收件明细应覆盖全部接收人且无重复");
    }

    /** 异常边界：既不指定接收人、也不按角色/部门筛选时，应被业务校验拒绝（400），而非 500。 */
    @Test
    @SuppressWarnings("unchecked")
    void sendWithoutReceiverRejected() {
        String token = login("admin", "admin123");
        Map<String, Object> req = Map.of("title", "x", "content", "y");
        ResponseEntity<Result> resp = post("/api/system/messages/send", req, Result.class, token);
        int status = resp.getStatusCode().value();
        assertTrue(status == 400 || status == 403, "空接收人应被校验/权限拒绝，不应 500");
        assertFalse(resp.getBody().isSuccess());
    }
}
