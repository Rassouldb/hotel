import { vi } from 'vitest'

import type { Profile } from '../api/auth'

/** Faux serveur pour les tests : chaque route renvoie une réponse au format de l'API PMS. */
export type Route = (init: RequestInit | undefined) => { status: number; body?: unknown } | undefined

export const demoHotels = [
  {
    id: 'h1', organizationId: 'o1', code: 'DKR01', name: 'Hôtel Démo Dakar', legalName: null, address: 'Corniche Ouest',
    phone: null, email: null, currencyCode: 'XOF', timezone: 'Africa/Dakar', language: 'fr', businessDate: '2026-10-01', status: 'ACTIVE',
  },
]

export function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: 'u1', username: 'reception', firstName: 'Moussa', lastName: 'Diallo', email: null, phone: null, language: 'fr',
    superAdmin: false, mustChangePassword: false,
    hotels: [{ hotelId: 'h1', roles: [{ id: 'r1', code: 'RECEPTIONIST', name: 'Réceptionniste' }], permissions: ['HOTEL_VIEW', 'RESERVATION_VIEW'] }],
    ...overrides,
  }
}

export function authResponse(token = 'token-1', passwordChangeRequired = false) {
  return {
    accessToken: token, tokenType: 'Bearer', expiresIn: 900, passwordChangeRequired,
    user: { id: 'u1', username: 'reception', firstName: 'Moussa', lastName: 'Diallo', superAdmin: false },
  }
}

export function errorBody(code: string, message: string, details: unknown[] = []) {
  return { success: false, error: { code, message, details }, timestamp: '2026-10-01T10:00:00Z', traceId: 'trace-1' }
}

/** Installe un faux fetch : `routes` associe « MÉTHODE /chemin » à une réponse. */
export function installFakeApi(routes: Record<string, Route>) {
  const fetchMock = vi.fn((input: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    const path = input.split('?')[0]
    const route = routes[`${method} ${path}`]
    const result = route?.(init) ?? { status: 404, body: errorBody('NOT_FOUND', 'Ressource introuvable.') }
    const payload = result.status === 204 ? null : JSON.stringify(
      result.body && typeof result.body === 'object' && 'success' in result.body ? result.body : { success: true, data: result.body },
    )
    return Promise.resolve(new Response(payload, { status: result.status, headers: { 'Content-Type': 'application/json' } }))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
