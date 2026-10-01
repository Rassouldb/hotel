import { apiRequest } from './http'

export interface AuthenticatedUser {
  id: string
  username: string
  firstName: string
  lastName: string
  superAdmin: boolean
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  passwordChangeRequired: boolean
  user: AuthenticatedUser
}

export interface HotelAccess {
  hotelId: string
  roles: Array<{ id: string; code: string; name: string }>
  permissions: string[]
}

export interface Profile {
  id: string
  username: string
  firstName: string
  lastName: string
  email: string | null
  phone: string | null
  language: 'fr' | 'en'
  superAdmin: boolean
  mustChangePassword: boolean
  hotels: HotelAccess[]
}

export interface ActiveSession {
  id: string
  createdAt: string
  lastActivityAt: string
  ip: string | null
  userAgent: string | null
  current: boolean
}

const PUBLIC = { publicEndpoint: true } as const

export async function login(username: string, password: string): Promise<AuthResponse> {
  return (await apiRequest<AuthResponse>('/api/v1/auth/login', { ...PUBLIC, method: 'POST', body: { username, password } })).data
}

export async function refreshSession(): Promise<AuthResponse> {
  return (await apiRequest<AuthResponse>('/api/v1/auth/refresh', { ...PUBLIC, method: 'POST' })).data
}

export async function logout(): Promise<void> {
  await apiRequest<void>('/api/v1/auth/logout', { ...PUBLIC, method: 'POST' })
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<AuthResponse> {
  return (await apiRequest<AuthResponse>('/api/v1/auth/password', { method: 'PUT', body: { currentPassword, newPassword } })).data
}

export async function requestPasswordReset(login: string): Promise<string> {
  const result = await apiRequest<{ message: string }>('/api/v1/auth/password/forgot', { ...PUBLIC, method: 'POST', body: { login } })
  return result.data.message
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await apiRequest<void>('/api/v1/auth/password/reset', { ...PUBLIC, method: 'POST', body: { token, newPassword } })
}

export async function fetchProfile(): Promise<Profile> {
  return (await apiRequest<Profile>('/api/v1/me')).data
}

export async function updateProfile(profile: Pick<Profile, 'firstName' | 'lastName' | 'phone' | 'language'>): Promise<Profile> {
  return (await apiRequest<Profile>('/api/v1/me', { method: 'PUT', body: profile })).data
}

export async function fetchSessions(): Promise<ActiveSession[]> {
  return (await apiRequest<ActiveSession[]>('/api/v1/me/sessions')).data
}

export async function revokeSession(sessionId: string): Promise<void> {
  await apiRequest<void>(`/api/v1/me/sessions/${sessionId}`, { method: 'DELETE' })
}
