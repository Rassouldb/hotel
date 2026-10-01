import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, apiRequest } from './http'

function mockFetch(status: number, body: unknown, headers: Record<string, string> = {}) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body === null ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...headers },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiRequest', () => {
  it('unwraps the standard envelope', async () => {
    mockFetch(200, { success: true, data: [{ code: 'DKR01' }], meta: { page: 1, size: 25, total: 1 } })

    const result = await apiRequest<Array<{ code: string }>>('/api/v1/hotels')

    expect(result.data).toEqual([{ code: 'DKR01' }])
    expect(result.meta).toEqual({ page: 1, size: 25, total: 1 })
  })

  it('turns a standard error body into an ApiError with code and traceId', async () => {
    mockFetch(409, {
      success: false,
      error: { code: 'ROOM_NOT_AVAILABLE', message: 'La chambre n’est pas disponible.' },
      timestamp: '2026-10-01T10:00:00Z',
      traceId: 'abc123def456',
    })

    const error = await apiRequest('/api/v1/reservations', { method: 'POST', body: {} }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'ROOM_NOT_AVAILABLE', traceId: 'abc123def456' })
  })

  it('falls back to the response header traceId when the body is not a standard error', async () => {
    mockFetch(502, null, { 'X-Trace-Id': 'gateway-trace-1' })

    const error = await apiRequest('/api/v1/hotels').catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 502, code: 'UNEXPECTED_RESPONSE', traceId: 'gateway-trace-1' })
  })

  it('reports an unreachable server as a network error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = await apiRequest('/api/v1/hotels').catch((e: unknown) => e)

    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('sends the idempotency key and JSON body for critical operations', async () => {
    const fetchMock = mockFetch(201, { success: true, data: { id: '1' } })

    await apiRequest('/api/v1/payments', { method: 'POST', body: { amount: '96000' }, idempotencyKey: 'key-12345678' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.headers).toMatchObject({ 'Idempotency-Key': 'key-12345678', 'Content-Type': 'application/json' })
    expect(init.body).toBe('{"amount":"96000"}')
  })
})
