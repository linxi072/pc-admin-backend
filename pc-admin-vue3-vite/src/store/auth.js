import { reactive } from 'vue'

const TOKEN_KEY = 'pc_admin_token'
const USER_KEY = 'pc_admin_user'

export const authState = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  username: localStorage.getItem(USER_KEY) || ''
})

export function isAuthenticated() {
  return !!authState.token
}

export function setAuth(token, username) {
  authState.token = token
  authState.username = username || ''
  localStorage.setItem(TOKEN_KEY, token)
  if (username) localStorage.setItem(USER_KEY, username)
}

export function clearAuth() {
  authState.token = ''
  authState.username = ''
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
