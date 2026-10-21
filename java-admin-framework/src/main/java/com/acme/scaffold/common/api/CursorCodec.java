package com.acme.scaffold.common.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * keyset 游标编解码（纯函数，零依赖）。
 *
 * <p>把「上一页最后一条记录的排序键」编码为不透明、URL 安全的字符串，
 * 避免客户端伪造排序或依赖偏移量。编码内容为本服务端内部键值，不含业务语义，对外仅暴露 base64url 串。
 *
 * <p>分隔符采用 ASCII 单元分隔符（{@code \u0001}），几乎不可能出现在真实数据中；
 * 解码用 {@link Pattern#quote} 转义以防正则注入；非法游标静默降级为首页（空列表），不抛异常。
 */
public final class CursorCodec {

    private static final String UNIT_SEP = "";
    private static final Pattern SPLIT = Pattern.compile(Pattern.quote(UNIT_SEP));

    private CursorCodec() {
    }

    /** 把排序键值编码为不透明游标；空列表返回空串（表示首页）。 */
    public static String encode(List<?> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object v : values) {
            if (!first) {
                sb.append(UNIT_SEP);
            }
            sb.append(v == null ? "" : String.valueOf(v));
            first = false;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 解码游标为排序键值字符串列表；空/非法游标返回空列表（表示首页）。 */
    public static List<String> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return List.of();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            String joined = new String(bytes, StandardCharsets.UTF_8);
            String[] parts = SPLIT.split(joined, -1);
            return new ArrayList<>(Arrays.asList(parts));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
