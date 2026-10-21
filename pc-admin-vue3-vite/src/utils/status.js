/**
 * 状态标签映射：统一各列表页「状态」列的文案与 el-tag 类型。
 *
 * <p>此前每个页面各自写死三元判断（ACTIVE ? '启用' : '停用'），新增状态或调整
 * 配色需逐页修改，易出现同状态不同色。此处集中声明，页面改为
 * <code>:type="statusTag(row.status).type"</code> 与
 * <code>{{ statusTag(row.status).label }}</code>。</p>
 */
export const STATUS_TAGS = {
  ACTIVE: { label: '启用', type: 'success' },
  DISABLED: { label: '停用', type: 'info' },
  // 菜单沿用 ENABLED / DISABLED 两套取值，与 ACTIVE 同义
  ENABLED: { label: '启用', type: 'success' },
  PUBLISHED: { label: '已发布', type: 'success' },
  OFFLINE: { label: '已下线', type: 'info' },
  DRAFT: { label: '草稿', type: 'info' },
  // 在线会话状态（ONLINE / IDLE / RESTRICTED）
  ONLINE: { label: '在线', type: 'success' },
  IDLE: { label: '空闲', type: 'info' },
  RESTRICTED: { label: '已限制', type: 'danger' }
}

/**
 * 取状态对应的标签定义。
 *
 * <p>未登记的状态不吞掉，回落为「原值 + info」，避免展示空白或 undefined；
 * 空值回落为 '—'。</p>
 *
 * @param {string} v 后端状态编码
 * @returns {{ label: string, type: string }}
 */
export function statusTag(v) {
  if (!v) return { label: '—', type: 'info' }
  return STATUS_TAGS[v] || { label: v, type: 'info' }
}
