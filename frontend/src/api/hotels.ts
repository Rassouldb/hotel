import { apiRequest } from './http'

export interface Hotel {
  id: string
  organizationId: string
  code: string
  name: string
  legalName: string | null
  address: string | null
  phone: string | null
  email: string | null
  currencyCode: string
  timezone: string
  language: string
  /** Date d'exploitation (AAAA-MM-JJ), avancée par la clôture journalière. */
  businessDate: string
  status: 'ACTIVE' | 'INACTIVE'
}

export async function fetchHotels(signal?: AbortSignal): Promise<Hotel[]> {
  const result = await apiRequest<Hotel[]>('/api/v1/hotels?size=100', { signal })
  return result.data
}
