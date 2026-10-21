package com.acme.scaffold.system.dto;

import java.util.List;

/**
 * 导出模板视图（编码 / 展示名 / 文件名 / 支持格式）。
 *
 * <p>由 {@code ExportCenterService} 构造，经 {@code ExportController} 以 {@code Result} 包装返回；
 * 属读模型（View），置于 {@code system.dto} 以符合分层约定（H4）。</p>
 */
public record ExportTemplateView(String code, String displayName, String fileName, List<String> formats) {
}
