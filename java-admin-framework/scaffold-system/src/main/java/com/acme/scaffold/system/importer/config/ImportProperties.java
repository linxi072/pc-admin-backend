package com.acme.scaffold.system.importer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 导入中心配置（{@code app.import.*}）。
 *
 * <p>默认即生效，无需在 {@code application*.yml} 中显式声明；如需收紧/放宽单次导入规模，
 * 在配置文件中设置 {@code app.import.max-rows} / {@code app.import.max-errors} 即可。</p>
 */
@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {

    /** 单次导入最大数据行数（不含表头），超出则拒绝并提示拆分文件后分批导入。 */
    private int maxRows = 5_000;

    /** 回传前端的最大错误条数；超出的失败行仅计入统计、不回传明细，避免响应体过大。 */
    private int maxErrors = 200;

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        // 防御非法配置（如 0 或负数），保证下限为 1
        this.maxRows = Math.max(1, maxRows);
    }

    public int getMaxErrors() {
        return maxErrors;
    }

    public void setMaxErrors(int maxErrors) {
        this.maxErrors = Math.max(1, maxErrors);
    }
}
