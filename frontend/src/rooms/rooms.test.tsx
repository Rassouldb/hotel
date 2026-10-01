import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { Room } from '../api/rooms'
import { authResponse, demoHotels, installFakeApi, profile } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'
import { nextHousekeepingStatuses, roomState } from './roomStatus'

afterEach(() => {
  vi.unstubAllGlobals()
})

function room(overrides: Partial<Room>): Room {
  return {
    id: overrides.number ?? 'r', number: '101', floor: 1, building: null, roomTypeId: 't1', roomTypeCode: 'STD',
    roomTypeName: 'Standard', accessible: false, features: [], notes: null, occupancyStatus: 'VACANT',
    housekeepingStatus: 'CLEAN', technicalStatus: 'IN_SERVICE', saleable: true, currentBlock: null, active: true, version: 0,
    ...overrides,
  }
}

describe('statut synthétique des chambres', () => {
  it('gives priority to technical status, then occupancy, then housekeeping', () => {
    expect(roomState(room({ technicalStatus: 'OUT_OF_ORDER', occupancyStatus: 'OCCUPIED' }))).toBe('outOfOrder')
    expect(roomState(room({ occupancyStatus: 'OCCUPIED', housekeepingStatus: 'DIRTY' }))).toBe('occupied')
    expect(roomState(room({ housekeepingStatus: 'DIRTY' }))).toBe('dirty')
    expect(roomState(room({ housekeepingStatus: 'INSPECTED' }))).toBe('availableClean')
  })

  it('offers only the housekeeping transitions allowed by the backend', () => {
    expect(nextHousekeepingStatuses('DIRTY')).toEqual(['CLEANING', 'CLEAN'])
    expect(nextHousekeepingStatuses('INSPECTED')).toEqual(['DIRTY'])
  })
})

describe('état des chambres', () => {
  it('counts rooms by state and lets housekeeping mark a room clean', async () => {
    let rooms = [
      room({ id: 'a', number: '101', housekeepingStatus: 'DIRTY' }),
      room({ id: 'b', number: '102' }),
      room({ id: 'c', number: '201', floor: 2, technicalStatus: 'MAINTENANCE', saleable: false }),
    ]
    const hkCalls: unknown[] = []
    installFakeApi({
      'POST /api/v1/auth/refresh': () => ({ status: 200, body: authResponse() }),
      'GET /api/v1/me': () => ({
        status: 200,
        body: profile({ hotels: [{ hotelId: 'h1', roles: [], permissions: ['ROOM_VIEW', 'HK_STATUS_UPDATE'] }] }),
      }),
      'GET /api/v1/hotels': () => ({ status: 200, body: demoHotels }),
      'GET /api/v1/hotels/h1/rooms': () => ({ status: 200, body: rooms }),
      'GET /api/v1/hotels/h1/rooms/a/history': () => ({ status: 200, body: [] }),
      'PUT /api/v1/hotels/h1/rooms/a/housekeeping-status': (init) => {
        hkCalls.push(JSON.parse(String(init?.body)))
        rooms = rooms.map((r) => (r.id === 'a' ? { ...r, housekeepingStatus: 'CLEAN' } : r))
        return { status: 200, body: rooms[0] }
      },
    })
    const user = userEvent.setup()

    renderApp('/chambres/etat')

    expect(await screen.findByText('Libres et prêtes : 1')).toBeInTheDocument()
    expect(screen.getByText('Sales : 1')).toBeInTheDocument()
    expect(screen.getByText('Maintenance : 1')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Chambre 101' }))
    const details = await screen.findByRole('dialog', { name: 'Chambre 101' })
    await user.click(within(details).getByRole('button', { name: 'Passer en « Propre »' }))

    await waitFor(() => expect(hkCalls).toEqual([{ status: 'CLEAN' }]))
    expect(await screen.findByText('Libres et prêtes : 2')).toBeInTheDocument()
  })
})
