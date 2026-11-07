package com.acme.scaffold.common.api;

/**
 * OFFSET 深翻页护栏的单一事实来源（纯函数，零依赖）。
 *
 * <p>此前 {@code UserService.list} 与 {@code MessageService.pageMine/pageSent} 各自内联了
 * 相同的「page/size 归一 → 计算 offset → 与 5000 行上限比较」逻辑，并各自维护一份
 * {@code MAX_*_OFFSET = 5000} 魔数；前端 {@code utils/keyset.js#OFFSET_SOFT_LIMIT}
 * 又与该值对齐。任一处调整都会造成前后端阈值漂移，故统一收敛到此处。</p>
 *
 * <p>本类只做计算与文案拼装，不抛业务异常、不触 DB；各服务保留原有的
 * {@code BusinessException(CommonErrorCode.VALIDATION_ERROR, ...)} 抛出口径与逐字错误文案，
 * 确保接口行为与用户可见提示完全不变。</p>
 */
public final class PagingRules {

    /** OFFSET 深翻页上限（行）：超过即要求调用方改用对应 keyset 游标端点。 */
    public static final long MAX_OFFSET = 5000;

    /** 单页条数上限，与 {@code PageQuery} 的服务端钳制保持一致。 */
    public static final int MAX_PAGE_SIZE = 100;

    private PagingRules() {
    }

    /**
     * 计算 OFFSET 分页偏移量（行）。
     *
     * <p>page 用 long 参与运算，避免 {@code Integer.MIN_VALUE - 1} 溢出成极大正数后
     * 被误判为「深翻页」；size 钳制到 {@code [1, MAX_PAGE_SIZE]}。</p>
     */
    public static long offsetOf(int page, int size) {
        long safePage = Math.max((long) page - 1, 0L);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return safePage * safeSize;
    }

    /** 是否越过 OFFSET 上限（严格大于，与前端 {@code shouldUseKeyset} 口径一致）。 */
    public static boolean exceedsOffset(int page, int size) {
        return offsetOf(page, size) > MAX_OFFSET;
    }

    /**
     * 拼装护栏错误文案。逐字复用既有三处文案模板，避免收敛后出现提示漂移。
     *
     * @param subject         列表名称，如「用户列表」「站内信」「已发送消息」
     * @param keysetEndpoint  对应游标端点路径，如 {@code /api/system/users/page-keyset}
     */
    public static String offsetLimitMessage(String subject, String keysetEndpoint) {
        return subject + "分页偏移过大（超过 " + MAX_OFFSET + " 行），请改用游标分页接口 " + keysetEndpoint;
    }
}
