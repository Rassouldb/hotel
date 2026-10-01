import { apiRequest, fetchImageUrl, uploadFile } from './http'

export type OccupancyStatus = 'VACANT' | 'OCCUPIED'
export type HousekeepingStatus = 'DIRTY' | 'CLEANING' | 'CLEAN' | 'INSPECTED'
export type TechnicalStatus = 'IN_SERVICE' | 'MAINTENANCE' | 'OUT_OF_ORDER'
export type BlockType = 'MAINTENANCE' | 'OUT_OF_ORDER' | 'HOUSE_USE'

export interface Amenity {
  id: string
  code: string
  name: string
  active: boolean
}

export interface RoomType {
  id: string
  code: string
  name: string
  description: string | null
  baseOccupancy: number
  maxOccupancy: number
  maxAdults: number
  maxChildren: number
  basePrice: number
  sortOrder: number
  active: boolean
  amenityIds: string[]
  photoIds: string[]
  activeRoomCount: number
  version: number
}

export type RoomTypeInput = Omit<RoomType, 'id' | 'photoIds' | 'activeRoomCount' | 'version'> & { version?: number }

export interface Room {
  id: string
  number: string
  floor: number | null
  building: string | null
  roomTypeId: string
  roomTypeCode: string | null
  roomTypeName: string | null
  accessible: boolean
  features: string[]
  notes: string | null
  occupancyStatus: OccupancyStatus
  housekeepingStatus: HousekeepingStatus
  technicalStatus: TechnicalStatus
  saleable: boolean
  currentBlock: { id: string; type: BlockType; startDate: string; endDate: string; reason: string } | null
  active: boolean
  version: number
}

export interface RoomInput {
  number?: string
  roomTypeId: string
  floor: number | null
  building: string | null
  accessible: boolean
  features: string[]
  notes: string | null
  active: boolean
  version?: number
}

export interface RoomHistoryEntry {
  id: string
  dimension: 'OCCUPANCY' | 'HOUSEKEEPING' | 'TECHNICAL'
  oldValue: string | null
  newValue: string | null
  reason: string | null
  businessDate: string
  occurredAt: string
  userName: string | null
}

export interface RoomBlock {
  id: string
  roomId: string
  roomNumber: string | null
  type: BlockType
  startDate: string
  endDate: string
  reason: string
  inProgress: boolean
  releasedAt: string | null
}

const base = (hotelId: string) => `/api/v1/hotels/${hotelId}`

// --- Équipements et types ------------------------------------------------------------------------------------

export async function fetchAmenities(hotelId: string): Promise<Amenity[]> {
  return (await apiRequest<Amenity[]>(`${base(hotelId)}/amenities`)).data
}

export async function saveAmenity(hotelId: string, amenity: { id?: string; code?: string; name: string; active: boolean }): Promise<Amenity> {
  const path = amenity.id ? `${base(hotelId)}/amenities/${amenity.id}` : `${base(hotelId)}/amenities`
  return (await apiRequest<Amenity>(path, { method: amenity.id ? 'PUT' : 'POST', body: amenity })).data
}

export async function fetchRoomTypes(hotelId: string): Promise<RoomType[]> {
  return (await apiRequest<RoomType[]>(`${base(hotelId)}/room-types`)).data
}

export async function saveRoomType(hotelId: string, id: string | null, input: RoomTypeInput): Promise<RoomType> {
  const path = id ? `${base(hotelId)}/room-types/${id}` : `${base(hotelId)}/room-types`
  return (await apiRequest<RoomType>(path, { method: id ? 'PUT' : 'POST', body: input })).data
}

export async function uploadRoomTypePhoto(hotelId: string, roomTypeId: string, file: File): Promise<void> {
  await uploadFile(`${base(hotelId)}/room-types/${roomTypeId}/photos`, file)
}

export function fetchRoomTypePhotoUrl(hotelId: string, roomTypeId: string, photoId: string): Promise<string> {
  return fetchImageUrl(`${base(hotelId)}/room-types/${roomTypeId}/photos/${photoId}`)
}

export async function deleteRoomTypePhoto(hotelId: string, roomTypeId: string, photoId: string): Promise<void> {
  await apiRequest<void>(`${base(hotelId)}/room-types/${roomTypeId}/photos/${photoId}`, { method: 'DELETE' })
}

// --- Chambres ---------------------------------------------------------------------------------------------------

export async function fetchRooms(hotelId: string): Promise<Room[]> {
  return (await apiRequest<Room[]>(`${base(hotelId)}/rooms`)).data
}

export async function createRoom(hotelId: string, input: RoomInput): Promise<Room> {
  return (await apiRequest<Room>(`${base(hotelId)}/rooms`, { method: 'POST', body: input })).data
}

export async function createRooms(hotelId: string, input: { prefix: string; from: number; to: number; roomTypeId: string; floor: number | null; building: string | null }): Promise<Room[]> {
  return (await apiRequest<Room[]>(`${base(hotelId)}/rooms/batch`, { method: 'POST', body: input })).data
}

export async function updateRoom(hotelId: string, roomId: string, input: RoomInput): Promise<Room> {
  return (await apiRequest<Room>(`${base(hotelId)}/rooms/${roomId}`, { method: 'PUT', body: input })).data
}

export async function changeHousekeepingStatus(hotelId: string, roomId: string, status: HousekeepingStatus, reason?: string): Promise<Room> {
  return (await apiRequest<Room>(`${base(hotelId)}/rooms/${roomId}/housekeeping-status`, { method: 'PUT', body: { status, reason } })).data
}

export async function fetchRoomHistory(hotelId: string, roomId: string): Promise<RoomHistoryEntry[]> {
  return (await apiRequest<RoomHistoryEntry[]>(`${base(hotelId)}/rooms/${roomId}/history`)).data
}

// --- Blocages ---------------------------------------------------------------------------------------------------

export async function fetchBlocks(hotelId: string, from?: string, to?: string): Promise<RoomBlock[]> {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return (await apiRequest<RoomBlock[]>(`${base(hotelId)}/room-blocks${query ? `?${query}` : ''}`)).data
}

export async function createBlock(hotelId: string, input: { roomId: string; type: BlockType; startDate: string; endDate: string; reason: string }): Promise<RoomBlock> {
  return (await apiRequest<RoomBlock>(`${base(hotelId)}/room-blocks`, { method: 'POST', body: input })).data
}

export async function releaseBlock(hotelId: string, blockId: string): Promise<RoomBlock> {
  return (await apiRequest<RoomBlock>(`${base(hotelId)}/room-blocks/${blockId}/release`, { method: 'POST' })).data
}

export async function deleteBlock(hotelId: string, blockId: string): Promise<void> {
  await apiRequest<void>(`${base(hotelId)}/room-blocks/${blockId}`, { method: 'DELETE' })
}
