-- V45 通知聚合摘要（M17）：sys_notification_digest，让 M6 多渠道通知具备「频次治理」能力——
-- 低价值高频通知（NOTICE 类）先聚合、按阈值或最大等待时长合并为一条摘要再投递，
-- 与 M13（谁想收什么）、M14（什么时候收）共同构成接收侧完整口径。
--
-- 设计要点：
--   1) 一次性投递语义：行无论摘要投递成功/跳过/失败均<b>物理删除</b>（无 status、
--      无 deleted 列）——聚合行是「待投递缓冲」而非档案，反复重投只会制造 FAILED
--      日志轰炸；审计由 M12 sys_notification_record 承担（摘要消息本身也走 dispatch 留痕）。
--   2) 唯一键 uk_user_channel_type：同一用户同一 (渠道, 消息类型) 至多一条聚合行，
--      新消息到达即合并（item_count 累加、items_json 保留最近 maxItems 条、
--      last_at 前推），写入走「查现存 → 更新或插入」（本仓动态 DSL 无 upsert API），
--      与 M13/M14 同范式。
--   3) 汇总触发双条件（先到先投）：item_count 达 app.notifications.digest.threshold
--      立即在挂点内刷新投递；或 last_at 距今超过 max-wait-minutes 由定时任务兜底
--      （idx_last_at 支撑扫描），防止低流量用户摘要永远囤在缓冲区。
--   4) 按接收者维度、不按广播：仅聚合有明确 userId 的定向消息（IN_APP/WEBSOCKET 的
--      receiverIds、EMAIL/SMS 反查 sys_user 全部命中的地址）；广播与 WEBHOOK 无个人
--      投递箱语义，不聚合（与 M13/M14 广播不过滤口径一致）。
--   5) items_json 为 JSON 字符串数组（每条 = 标题优先、正文回退的单行摘要，截断），
--      由服务端序列化写入；投递时按 userId 现查 email/mobile 生成摘要消息，
--      用户已删或无地址则直接删行（无处可投）。
--   6) 摘要消息 msgType 恒为 "DIGEST"：不在 app.notifications.digest.msg-types 配置内
--      （不会再被挂起，防递归），也不在 M13 偏好白名单内（无偏好行、默认开通，
--      仅受 sys_user 渠道总开关约束），天然复用 M13/M14/M16 全链路。

CREATE TABLE IF NOT EXISTS sys_notification_digest (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(16) NOT NULL,
    msg_type VARCHAR(32) NOT NULL,
    item_count INT NOT NULL DEFAULT 0,
    items_json TEXT NULL,
    first_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_channel_type (user_id, channel, msg_type),
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_last_at (last_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
