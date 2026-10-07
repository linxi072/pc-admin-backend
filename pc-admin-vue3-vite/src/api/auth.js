import { request } from './http'

// POST /api/auth/login -> TokenView { accessToken, tokenType, expiresIn }
export const login = (username, password) =>
  request({ method: 'POST', url: '/api/auth/login', data: { username, password } })

// POST /api/auth/logout
export const logout = () => request({ method: 'POST', url: '/api/auth/logout' })
