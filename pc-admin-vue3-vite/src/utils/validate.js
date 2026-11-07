// el-form 校验收口工具（零依赖，仅用 element-plus 的 ElMessage 提示）。
//
// 背景（E2E F8）：「保存」类操作普遍写成
//     const valid = await formRef.value.validate().catch(() => false)
//     if (!valid) return
// 校验失败时只有字段边框变红，没有任何文字反馈；在弹窗里（尤其是滚动到底部点保存）
// 用户看不到顶部红框，会以为按钮没反应、反复点击。本工具把「失败」变成一条明确的
// 提示：优先取校验器给出的第一条错误文案，取不到再回退到通用的「必填」。

import { ElMessage } from 'element-plus'
import { t } from '@/i18n'

/**
 * 从 el-form 校验失败的回执里取第一条错误文案。
 *
 * <p>回执形如 {@code { username: [{ message, field }], mobile: [{...}] }}，
 * 也可能是单个 {@code { message }}（部分自定义校验器），两种都兼容。</p>
 */
export function firstValidationMessage(err) {
  if (!err || typeof err !== 'object') return ''
  for (const value of Object.values(err)) {
    if (Array.isArray(value) && value.length && value[0]?.message) return value[0].message
    if (value && typeof value === 'object' && typeof value.message === 'string') return value.message
  }
  return ''
}

/**
 * 校验表单：通过返回 true；不通过弹出一条 warning 并返回 false。
 *
 * @param {object|import('vue').Ref} formRef 表单引用（可传 ref 本身，也可传 ref.value）
 * @returns {Promise<boolean>} 是否通过校验
 */
export async function validateOrWarn(formRef) {
  const target = formRef && formRef.value !== undefined ? formRef.value : formRef
  if (!target || typeof target.validate !== 'function') {
    // 表单尚未挂载（如懒渲染的抽屉）：不阻断提交，交由后端校验兜底
    return true
  }
  try {
    await target.validate()
    return true
  } catch (err) {
    ElMessage.warning(firstValidationMessage(err) || t('common.required'))
    return false
  }
}
