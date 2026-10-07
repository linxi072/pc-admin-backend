import { request } from './http'

// GET /api/profile -> ProfileView
export const getProfile = () => request({ method: 'GET', url: '/api/profile' })

// PUT /api/profile -> ProfileView
// body: { displayName, mobile, email, avatarUrl }
export const updateProfile = (body) => request({ method: 'PUT', url: '/api/profile', data: body })

// PUT /api/profile/contact -> ProfileView
// body: { channel: 'MOBILE' | 'EMAIL', contact }，contact 传空串表示解绑
export const bindContact = (body) => request({ method: 'PUT', url: '/api/profile/contact', data: body })

// POST /api/profile/avatar（multipart/form-data，字段名 file）-> ProfileView
export const uploadAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request({
    method: 'POST',
    url: '/api/profile/avatar',
    data: form,
    // 由浏览器自动生成 multipart boundary，此处不可手动设置 Content-Type
    headers: { 'Content-Type': undefined }
  })
}

// POST /api/profile/password -> void（成功后需重新登录）
export const changePassword = (body) =>
  request({ method: 'POST', url: '/api/profile/password', data: body })

// GET /api/profile/preference -> PreferenceView
export const getPreference = () => request({ method: 'GET', url: '/api/profile/preference' })

// PUT /api/profile/preference -> PreferenceView
export const updatePreference = (body) =>
  request({ method: 'PUT', url: '/api/profile/preference', data: body })

// GET /api/profile/devices -> DeviceView[]
export const listDevices = () => request({ method: 'GET', url: '/api/profile/devices' })

// DELETE /api/profile/devices/{sessionId} -> void
export const revokeDevice = (sessionId) =>
  request({ method: 'DELETE', url: `/api/profile/devices/${encodeURIComponent(sessionId)}` })

// POST /api/profile/devices/logout-others -> { updated }被下线设备数
export const logoutOtherDevices = () =>
  request({ method: 'POST', url: '/api/profile/devices/logout-others' })