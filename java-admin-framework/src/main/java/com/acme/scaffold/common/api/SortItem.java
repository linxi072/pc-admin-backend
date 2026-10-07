package com.acme.scaffold.common.api;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import java.util.List;

/**
 * 分页排序项。字段名由服务端白名单映射为数据库列，禁止直接使用客户端传入的列名。
 */
public record SortItem(String field, String direction) {

    public static final String ASC = "ASC";
    public static final String DESC = "DESC";

    public SortItem {
        direction = (direction == null || direction.isBlank()) ? ASC : direction.toUpperCase();
    }

    public boolean ascending() {
        return !DESC.equals(direction);
    }

    /**
     * 把「字段名 + 方向」两个扁平参数解析为排序项列表，供 {@link PageQuery} 承载。
     *
     * <p>字段名为空视为不排序；方向为空默认 {@code ASC}；方向非 ASC/DESC 直接 400，
     * 避免静默降级成升序后前端以为「排序生效了」。
     *
     * @return 空列表表示不指定排序（由服务端回落默认排序）
     */
    public static List<SortItem> single(String field, String direction) {
        if (field == null || field.isBlank()) {
            return List.of();
        }
        String dir = (direction == null || direction.isBlank()) ? ASC : direction.trim().toUpperCase();
        if (!ASC.equals(dir) && !DESC.equals(dir)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "排序方向仅支持 ASC/DESC，当前为：" + direction);
        }
        return List.of(new SortItem(field.trim(), dir));
    }
}
