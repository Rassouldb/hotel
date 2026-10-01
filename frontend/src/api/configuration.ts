import { apiRequest } from './http'

export type ChargeCategory = 'ACCOMMODATION' | 'FOOD' | 'BEVERAGE' | 'SERVICE' | 'OTHER'
export const CHARGE_CATEGORIES: ChargeCategory[] = ['ACCOMMODATION', 'FOOD', 'BEVERAGE', 'SERVICE', 'OTHER']

export type TaxType = 'PERCENTAGE' | 'FIXED_PER_PERSON_NIGHT' | 'FIXED_PER_UNIT'

export interface TaxPeriod {
  validFrom: string
  validTo: string | null
  rate: number | null
  amount: number | null
}

export interface Tax {
  id: string
  code: string
  name: string
  type: TaxType
  vat: boolean
  appliesTo: ChargeCategory[]
  exemptChildrenUnderAge: number | null
  exemptResidents: boolean
  active: boolean
  currentRate: number | null
  currentAmount: number | null
  periods: TaxPeriod[]
  version: number
}

export interface CreateTaxInput {
  code: string
  name: string
  type: TaxType
  vat: boolean
  appliesTo: ChargeCategory[]
  exemptChildrenUnderAge: number | null
  exemptResidents: boolean
  validFrom: string
  rate: number | null
  amount: number | null
}

export type PaymentMethodType = 'CASH' | 'CARD' | 'TRANSFER' | 'CHEQUE' | 'MOBILE_MONEY' | 'OTHER'

export interface PaymentMethod {
  id: string
  code: string
  name: string
  type: PaymentMethodType
  requiresCashSession: boolean
  requiresReference: boolean
  active: boolean
  sortOrder: number
}

export interface Channel {
  id: string
  code: string
  name: string
  commissionRate: number | null
  active: boolean
  sortOrder: number
}

export type ReasonDomain = 'CANCELLATION' | 'DISCOUNT' | 'ROOM_CHANGE' | 'ROOM_BLOCK' | 'ADJUSTMENT' | 'HOUSEKEEPING'
export const REASON_DOMAINS: ReasonDomain[] = ['CANCELLATION', 'DISCOUNT', 'ROOM_CHANGE', 'ROOM_BLOCK', 'ADJUSTMENT', 'HOUSEKEEPING']

export interface Reason {
  id: string
  domain: ReasonDomain
  code: string
  label: string
  active: boolean
  sortOrder: number
}

const base = (hotelId: string) => `/api/v1/hotels/${hotelId}`

// --- Taxes ----------------------------------------------------------------------------------------------------

export async function fetchTaxes(hotelId: string): Promise<Tax[]> {
  return (await apiRequest<Tax[]>(`${base(hotelId)}/taxes`)).data
}

export async function createTax(hotelId: string, input: CreateTaxInput): Promise<Tax> {
  return (await apiRequest<Tax>(`${base(hotelId)}/taxes`, { method: 'POST', body: input })).data
}

export async function updateTax(hotelId: string, tax: Tax, changes: Pick<Tax, 'name' | 'appliesTo' | 'exemptChildrenUnderAge' | 'exemptResidents' | 'active'>): Promise<Tax> {
  return (await apiRequest<Tax>(`${base(hotelId)}/taxes/${tax.id}`, { method: 'PUT', body: { ...changes, version: tax.version } })).data
}

export async function changeTaxRate(hotelId: string, taxId: string, input: { validFrom: string; rate: number | null; amount: number | null }): Promise<Tax> {
  return (await apiRequest<Tax>(`${base(hotelId)}/taxes/${taxId}/rates`, { method: 'POST', body: input })).data
}

// --- Moyens de paiement, canaux, motifs -------------------------------------------------------------------------

export async function fetchPaymentMethods(hotelId: string): Promise<PaymentMethod[]> {
  return (await apiRequest<PaymentMethod[]>(`${base(hotelId)}/payment-methods`)).data
}

export async function savePaymentMethod(hotelId: string, method: Omit<PaymentMethod, 'id'> & { id?: string }): Promise<PaymentMethod> {
  const path = method.id ? `${base(hotelId)}/payment-methods/${method.id}` : `${base(hotelId)}/payment-methods`
  return (await apiRequest<PaymentMethod>(path, { method: method.id ? 'PUT' : 'POST', body: method })).data
}

export async function fetchChannels(hotelId: string): Promise<Channel[]> {
  return (await apiRequest<Channel[]>(`${base(hotelId)}/channels`)).data
}

export async function saveChannel(hotelId: string, channel: Omit<Channel, 'id'> & { id?: string }): Promise<Channel> {
  const path = channel.id ? `${base(hotelId)}/channels/${channel.id}` : `${base(hotelId)}/channels`
  return (await apiRequest<Channel>(path, { method: channel.id ? 'PUT' : 'POST', body: channel })).data
}

export async function fetchReasons(hotelId: string, domain?: ReasonDomain): Promise<Reason[]> {
  const query = domain ? `?domain=${domain}` : ''
  return (await apiRequest<Reason[]>(`${base(hotelId)}/reasons${query}`)).data
}

export async function saveReason(hotelId: string, reason: Omit<Reason, 'id'> & { id?: string }): Promise<Reason> {
  const path = reason.id ? `${base(hotelId)}/reasons/${reason.id}` : `${base(hotelId)}/reasons`
  return (await apiRequest<Reason>(path, { method: reason.id ? 'PUT' : 'POST', body: reason })).data
}
