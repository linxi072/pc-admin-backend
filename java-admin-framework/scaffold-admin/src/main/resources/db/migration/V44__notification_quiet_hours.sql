-- V44 通知免打扰时段（M14）：sys_notification_quiet_hours，让 M6 多渠道通知首次具备
-- 「什么时候收」的时间维口径——M13 偏好中心闭合「谁想收什么」，本模块补齐接收侧最后一维。
--
-- 设计要点：
--   1) 懒行模型：无行 = 无免打扰（与 M13「无行默认开通」互为镜像）；关停免打扰保留配置行
--      （enabled=0），故无「恢复默认」删行语义，也无 deleted 列。
--   2) 按接收者维度、不按渠道：免打扰是时间维总闸（先于 M13 偏好过滤生效），
--      一名用户至多一行，唯一键 uk_user 兜底幂等，写入走「查现存 → 更新或插入」。
--   3) 时间口径：start_time / end_time 为 TIME 列，半开区间 [start, end)，按服务器本地时间
--      裁决；start > end 表达跨午夜窗口（默认 22:00–08:00）；start = end 为退化窗口，
--      由服务端校验拒绝（见 NotificationQuietHoursService.update）。
--   4) 告警穿透：alert_breakthrough 默认 1——ALERT 类型消息在免打扰窗口内照常投递，
--      故障告警不应被时间窗吞掉；其余消息类型（NOTICE/SYSTEM/CC）受抑制。
--   5) 广播（IN_APP 仅 roleIds/orgIds、WEBSOCKET 无定向接收者）与 WEBHOOK 不过滤：
--      受众规模不定且无个人作息语义，属文档化限制（见开发手册 M14 章节）。
--   6) 自服务页面（个人中心 → 免打扰）：无菜单、无权限码播种，
--      接口 /api/notification-quiet-hours/mine 仅登录可访问，userId 一律服务端取自安全上下文。

CREATE TABLE IF NOT EXISTS sys_notification_quiet_hours (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    alert_breakthrough TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
