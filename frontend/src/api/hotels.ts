import { apiRequest, fetchImageUrl, uploadFile } from './http'

export interface Hotel {
  id: string
  organizationId: string
  code: string
  name: string
  legalName: string | null
  address: string | null
  phone: string | null
  email: string | null
  /** Identifiants légaux affichés sur les factures (libellé → valeur). */
  legalIds: Record<string, string>
  currencyCode: string
  timezone: string
  language: string
  /** Date d'exploitation (AAAA-MM-JJ), avancée par la clôture journalière. */
  businessDate: string
  status: 'ACTIVE' | 'INACTIVE'
  hasLogo: boolean
  version: number
}

export type CheckInGuarantee = 'NONE' | 'FIRST_NIGHT' | 'FULL_STAY' | 'FIXED_DEPOSIT'

export interface HotelSettings {
  pricesIncludeTax: boolean
  checkInTime: string
  checkOutTime: string
  inspectionRequired: boolean
  idDocumentRequired: boolean
  checkInGuarantee: CheckInGuarantee
  guaranteeAmount: number | null
  walkInFirstNightPayment: boolean
  cancellationPolicy: { freeUntilHours: number; lateCancellationPenaltyNights: number; noShowPenaltyNights: number }
  discountLimits: Record<string, number>
}

export interface CreateHotelInput {
  code: string
  name: string
  legalName?: string
  currencyCode: string
  timezone: string
  language: 'fr' | 'en'
  businessDate?: string
}

export interface UpdateHotelInput {
  name: string
  legalName: string | null
  address: string | null
  phone: string | null
  email: string | null
  legalIds: Record<string, string>
  language: string
  version: number
}

export async function fetchHotels(signal?: AbortSignal): Promise<Hotel[]> {
  const result = await apiRequest<Hotel[]>('/api/v1/hotels?size=100', { signal })
  return result.data
}

export async function createHotel(input: CreateHotelInput): Promise<Hotel> {
  return (await apiRequest<Hotel>('/api/v1/hotels', { method: 'POST', body: input })).data
}

export async function updateHotel(hotelId: string, input: UpdateHotelInput): Promise<Hotel> {
  return (await apiRequest<Hotel>(`/api/v1/hotels/${hotelId}`, { method: 'PUT', body: input })).data
}

export async function fetchSettings(hotelId: string): Promise<HotelSettings> {
  return (await apiRequest<HotelSettings>(`/api/v1/hotels/${hotelId}/settings`)).data
}

export async function updateSettings(hotelId: string, settings: HotelSettings): Promise<HotelSettings> {
  return (await apiRequest<HotelSettings>(`/api/v1/hotels/${hotelId}/settings`, { method: 'PUT', body: settings })).data
}

export function fetchLogoUrl(hotelId: string): Promise<string> {
  return fetchImageUrl(`/api/v1/hotels/${hotelId}/logo`)
}

export async function uploadLogo(hotelId: string, file: File): Promise<void> {
  await uploadFile<void>(`/api/v1/hotels/${hotelId}/logo`, file, 'PUT')
}

export async function deleteLogo(hotelId: string): Promise<void> {
  await apiRequest<void>(`/api/v1/hotels/${hotelId}/logo`, { method: 'DELETE' })
}
