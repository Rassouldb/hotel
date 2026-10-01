import type { HousekeepingStatus, Room } from '../api/rooms'

/** Couleurs de l'état des chambres, identiques dans toute l'application (§37.2). */
export const housekeepingColor: Record<HousekeepingStatus, 'error' | 'warning' | 'success' | 'info'> = {
  DIRTY: 'error',
  CLEANING: 'warning',
  CLEAN: 'success',
  INSPECTED: 'info',
}

export type RoomState = 'availableClean' | 'dirty' | 'cleaning' | 'occupied' | 'maintenance' | 'outOfOrder'

/** Statut synthétique d'une chambre (§9.3), utilisé pour les compteurs et le code couleur. */
export function roomState(room: Room): RoomState {
  if (room.technicalStatus === 'OUT_OF_ORDER') return 'outOfOrder'
  if (room.technicalStatus === 'MAINTENANCE') return 'maintenance'
  if (room.occupancyStatus === 'OCCUPIED') return 'occupied'
  if (room.housekeepingStatus === 'DIRTY') return 'dirty'
  if (room.housekeepingStatus === 'CLEANING') return 'cleaning'
  return 'availableClean'
}

export const stateColor: Record<RoomState, string> = {
  availableClean: 'success.main',
  dirty: 'error.main',
  cleaning: 'warning.main',
  occupied: 'primary.main',
  maintenance: 'grey.600',
  outOfOrder: 'grey.900',
}

export const ROOM_STATES: RoomState[] = ['availableClean', 'dirty', 'cleaning', 'occupied', 'maintenance', 'outOfOrder']

/** Statuts ménage accessibles à la main depuis le statut actuel (aligné sur le backend). */
export function nextHousekeepingStatuses(current: HousekeepingStatus): HousekeepingStatus[] {
  switch (current) {
    case 'DIRTY': return ['CLEANING', 'CLEAN']
    case 'CLEANING': return ['CLEAN', 'DIRTY']
    case 'CLEAN': return ['INSPECTED', 'DIRTY']
    case 'INSPECTED': return ['DIRTY']
  }
}
