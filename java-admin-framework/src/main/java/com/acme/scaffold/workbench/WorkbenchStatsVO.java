package com.acme.scaffold.workbench;

import java.util.List;

/**
 * 工作台统计响应：统计卡片列表。
 *
 * <p>卡片顺序即前端渲染顺序；文案（label）与单位（unit）由后端下发，
 * 前端不做二次映射，保证两端字段与语义严格一致。
 */
public record WorkbenchStatsVO(List<StatCardVO> cards) {
}
