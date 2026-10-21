package com.acme.scaffold.system.export.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 导出中心配置（{@code app.export.*}）。
 *
 * <p>默认即生效，无需在 {@code application*.yml} 中显式声明；如需收紧/放宽单次导出行上限，
 * 在配置文件中设置 {@code app.export.max-rows} 即可。</p>
 */
@ConfigurationProperties(prefix = "app.export")
public class ExportProperties {

    /** 单次导出最大数据行数（不含表头），超出则拒绝并提示缩小筛选范围后分批导出。 */
    private int maxRows = 100_000;

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        // 防御非法配置（如 0 或负数），保证下限为 1
        this.maxRows = Math.max(1, maxRows);
    }
}
