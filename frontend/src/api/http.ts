/**
 * Client HTTP de l'API PMS. Déballe l'enveloppe standard { success, data, meta }, transforme les
 * erreurs au format §34.2 en ApiError (avec le traceId à communiquer au support), joint le jeton
 * d'accès et le renouvelle automatiquement une fois quand il a expiré.
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
  /** Point d’entrée public (connexion, mot de passe oublié) : ni jeton envoyé, ni renouvellement de session. */
  publicEndpoint?: boolean
  /** Réponse binaire (image) renvoyée telle quelle sous forme de Blob. */
  binary?: boolean
}

// --- Jeton d'accès : en mémoire uniquement (jamais dans localStorage), §7 ---------------------------------

let accessToken: string | null = null
let refreshHandler: (() => Promise<boolean>) | null = null
let pendingRefresh: Promise<boolean> | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

/** Fonction appelée pour renouveler la session quand l'API répond 401 ; renvoie vrai en cas de succès. */
export function setRefreshHandler(handler: (() => Promise<boolean>) | null) {
  refreshHandler = handler
}

function refreshOnce(): Promise<boolean> {
  if (!refreshHandler) {
    return Promise.resolve(false)
  }
  if (!pendingRefresh) {
    pendingRefresh = refreshHandler().finally(() => {
      pendingRefresh = null
    })
  }
  return pendingRefresh
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  try {
    return await send<T>(path, options)
  } catch (error) {
    const expired = error instanceof ApiError && error.status === 401 && error.code === 'UNAUTHENTICATED'
    if (expired && !options.publicEndpoint && (await refreshOnce())) {
      return send<T>(path, options)
    }
    throw error
  }
}

async function send<T>(path: string, options: RequestOptions): Promise<ApiResult<T>> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const isForm = options.body instanceof FormData
  if (options.body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey
  }
  if (accessToken && !options.publicEndpoint) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
      signal: options.signal,
      credentials: 'same-origin',
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE)
  }

  const traceId = response.headers.get('X-Trace-Id') ?? undefined
  if (options.binary && response.ok) {
    return { data: (await response.blob()) as T }
  }
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

/** Erreur de validation propre à un champ (details[].field), pour l'afficher sous le bon champ. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) {
    return {}
  }
  const result: Record<string, string> = {}
  for (const detail of error.details) {
    if (typeof detail.field === 'string') {
      result[detail.field] = error.code === 'VALIDATION_ERROR' && typeof detail.issue === 'string' ? detail.issue : error.message
    }
  }
  return result
}

/** Envoie un fichier (multipart/form-data, champ « file »). */
export async function uploadFile<T>(path: string, file: File, method: 'POST' | 'PUT' = 'POST'): Promise<ApiResult<T>> {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<T>(path, { method, body: form })
}

/** Télécharge une image protégée et renvoie une URL locale utilisable dans <img src>. */
export async function fetchImageUrl(path: string): Promise<string> {
  const result = await apiRequest<Blob>(path, { binary: true })
  return URL.createObjectURL(result.data)
}
