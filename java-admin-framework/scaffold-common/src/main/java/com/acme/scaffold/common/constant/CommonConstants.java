package com.acme.scaffold.common.constant;

/**
 * 跨模块通用常量，对应若依 {@code common/constant} 的约定位置。
 *
 * <p>只收录「字面值稳定、语义跨模块一致」的编译期常量，供各模块以委托方式引用，
 * 避免同一魔数在多处各自定义后发生漂移。全部取值与此前散落在各模块的字面值逐字一致，
 * 因此不改变任何 HTTP 响应、JSON 序列化格式、数据库内容与用户可见行为。</p>
 *
 * <p>刻意不收录：业务错误码（由 {@code common.error.CommonErrorCode} 单一维护）、
 * 分页护栏阈值（由 {@code common.api.PagingRules} 单一维护）、各模块专属配置前缀。
 * 这些都已有各自的单一事实来源，重复搬运反而会造成两个事实来源。</p>
 */
public final class CommonConstants {

    /** 单页条数上限：服务端把 size 钳制到 {@code [1, 100]}，与 api-contract.json 的 maxSize 一致。 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 秒级日期时间格式：报表窗口、运行指标、任务一次性执行时间的展示与序列化。 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 分钟级日期时间格式：登录设备、通知摘要与认证信息展示。 */
    public static final String DATE_MINUTE_PATTERN = "yyyy-MM-dd HH:mm";

    /** 头像静态资源 URL 前缀：上传落库与 WebMvc 资源映射必须使用同一个值。 */
    public static final String AVATAR_URL_PREFIX = "/uploads/avatar/";

    /** 统一响应体的成功业务码，见 scripts/api-contract.json 的 successCodes。 */
    public static final String SUCCESS_CODE = "0";

    private CommonConstants() {
    }
}
