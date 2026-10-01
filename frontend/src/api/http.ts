/**
 * Client HTTP de l'API PMS. Déballe l'enveloppe standard { success, data, meta } et transforme
 * les erreurs au format §34.2 en ApiError, avec le traceId à communiquer au support.
 */

export interface PageMeta {
  page: number
  size: number
  total: number
}

export interface ApiResult<T> {
  data: T
  meta?: PageMeta
}

interface ErrorBody {
  success: false
  error: { code: string; message: string; details?: Array<Record<string, unknown>> }
  timestamp: string
  traceId?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string
  readonly details: Array<Record<string, unknown>>

  constructor(status: number, code: string, message: string, traceId?: string, details: Array<Record<string, unknown>> = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.traceId = traceId
    this.details = details
  }
}

const NETWORK_ERROR_MESSAGE = 'Le serveur est injoignable. Vérifiez votre connexion puis réessayez.'
const UNEXPECTED_ERROR_MESSAGE = 'Réponse inattendue du serveur.'

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** Obligatoire pour les opérations critiques (§32.4) : réutiliser la même clé lors d'un nouvel essai. */
  idempotencyKey?: string
  signal?: AbortSignal
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey
  }

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE)
  }

  const traceId = response.headers.get('X-Trace-Id') ?? undefined
  const payload: unknown = response.status === 204 ? null : await response.json().catch(() => null)

  if (!response.ok) {
    if (isErrorBody(payload)) {
      throw new ApiError(response.status, payload.error.code, payload.error.message, payload.traceId ?? traceId, payload.error.details ?? [])
    }
    throw new ApiError(response.status, 'UNEXPECTED_RESPONSE', UNEXPECTED_ERROR_MESSAGE, traceId)
  }

  const envelope = payload as { data: T; meta?: PageMeta } | null
  return { data: envelope?.data as T, meta: envelope?.meta }
}

function isErrorBody(payload: unknown): payload is ErrorBody {
  return typeof payload === 'object' && payload !== null && 'error' in payload
    && typeof (payload as ErrorBody).error?.code === 'string'
}
