import { apiRequest, type ApiResult } from './http'

export interface Assignment {
  hotelId: string
  roleId: string
  roleCode?: string | null
  roleName?: string | null
}

export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface User {
  id: string
  username: string
  firstName: string
  lastName: string
  email: string | null
  phone: string | null
  language: 'fr' | 'en'
  superAdmin: boolean
  status: UserStatus
  mustChangePassword: boolean
  locked: boolean
  lastLoginAt: string | null
  assignments: Assignment[]
  version: number
}

export interface UserInput {
  username?: string
  firstName: string
  lastName: string
  email: string | null
  phone: string | null
  language: 'fr' | 'en'
  superAdmin: boolean
  assignments: Array<{ hotelId: string; roleId: string }>
  version?: number
}

export interface UserWithTemporaryPassword {
  user: User
  temporaryPassword: string
}

export interface Role {
  id: string
  code: string
  name: string
  description: string | null
  system: boolean
  permissions: string[]
  userCount: number
  version: number
}

export interface RoleInput {
  name: string
  description: string | null
  permissions: string[]
  version?: number
}

export interface PermissionDefinition {
  code: string
  module: string
  moduleLabel: string
  description: string
}

export interface AuditLog {
  id: string
  occurredAt: string
  hotelId: string | null
  userId: string | null
  userName: string | null
  action: string
  entityType: string
  entityId: string | null
  oldValue: unknown
  newValue: unknown
  reason: string | null
  ip: string | null
  result: 'SUCCESS' | 'FAILURE'
  errorCode: string | null
  traceId: string | null
}

function query(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

// --- Utilisateurs -------------------------------------------------------------------------------------------

export function fetchUsers(params: { search?: string; status?: UserStatus | ''; page: number; size: number }): Promise<ApiResult<User[]>> {
  return apiRequest<User[]>(`/api/v1/users${query(params)}`)
}

export async function createUser(input: UserInput): Promise<UserWithTemporaryPassword> {
  return (await apiRequest<UserWithTemporaryPassword>('/api/v1/users', { method: 'POST', body: input })).data
}

export async function updateUser(id: string, input: UserInput): Promise<User> {
  return (await apiRequest<User>(`/api/v1/users/${id}`, { method: 'PUT', body: input })).data
}

export async function disableUser(id: string): Promise<User> {
  return (await apiRequest<User>(`/api/v1/users/${id}/disable`, { method: 'POST' })).data
}

export async function enableUser(id: string): Promise<User> {
  return (await apiRequest<User>(`/api/v1/users/${id}/enable`, { method: 'POST' })).data
}

export async function resetUserPassword(id: string): Promise<UserWithTemporaryPassword> {
  return (await apiRequest<UserWithTemporaryPassword>(`/api/v1/users/${id}/reset-password`, { method: 'POST' })).data
}

// --- Rôles et permissions -----------------------------------------------------------------------------------

export async function fetchRoles(): Promise<Role[]> {
  return (await apiRequest<Role[]>('/api/v1/roles')).data
}

export async function fetchPermissions(): Promise<PermissionDefinition[]> {
  return (await apiRequest<PermissionDefinition[]>('/api/v1/permissions')).data
}

export async function createRole(input: RoleInput): Promise<Role> {
  return (await apiRequest<Role>('/api/v1/roles', { method: 'POST', body: input })).data
}

export async function updateRole(id: string, input: RoleInput): Promise<Role> {
  return (await apiRequest<Role>(`/api/v1/roles/${id}`, { method: 'PUT', body: input })).data
}

export async function deleteRole(id: string): Promise<void> {
  await apiRequest<void>(`/api/v1/roles/${id}`, { method: 'DELETE' })
}

// --- Journal d'audit ----------------------------------------------------------------------------------------

export interface AuditFilters {
  action?: string
  entityType?: string
  entityId?: string
  result?: '' | 'SUCCESS' | 'FAILURE'
  from?: string
  to?: string
}

export function fetchAuditLogs(filters: AuditFilters & { page: number; size: number }): Promise<ApiResult<AuditLog[]>> {
  return apiRequest<AuditLog[]>(`/api/v1/audit-logs${query({ ...filters })}`)
}
