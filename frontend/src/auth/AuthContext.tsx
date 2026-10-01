import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'

import * as authApi from '../api/auth'
import type { AuthResponse, Profile } from '../api/auth'
import { ApiError, setAccessToken, setRefreshHandler } from '../api/http'

type Status = 'loading' | 'anonymous' | 'authenticated'

/** Raison de la fin de session, affichée sur l'écran de connexion. */
export type SessionEnd = 'expired' | 'logout' | null

interface AuthState {
  status: Status
  profile: Profile | null
  sessionEnd: SessionEnd
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Après un changement de mot de passe : nouveau jeton (sans mot de passe temporaire) et profil à jour. */
  completePasswordChange: (response: AuthResponse) => Promise<void>
  reloadProfile: () => Promise<void>
  /** Permission dans l'hôtel donné, ou dans au moins un hôtel si aucun n'est précisé. */
  can: (permission: string, hotelId?: string | null) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

const REFRESH_RETRY_DELAY_MS = 600

async function refreshWithRetry(): Promise<AuthResponse> {
  try {
    return await authApi.refreshSession()
  } catch (error) {
    // Un autre onglet renouvelle la session au même moment : on réessaie avec le cookie qu'il a reçu.
    if (error instanceof ApiError && error.code === 'REFRESH_IN_PROGRESS') {
      await new Promise((resolve) => setTimeout(resolve, REFRESH_RETRY_DELAY_MS))
      return authApi.refreshSession()
    }
    throw error
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<Status>('loading')
  const [profile, setProfile] = useState<Profile | null>(null)
  const [sessionEnd, setSessionEnd] = useState<SessionEnd>(null)
  const started = useRef(false)

  const endSession = useCallback((reason: SessionEnd) => {
    setAccessToken(null)
    setProfile(null)
    setSessionEnd(reason)
    setStatus('anonymous')
    queryClient.clear()
  }, [queryClient])

  const startSession = useCallback(async (response: AuthResponse) => {
    setAccessToken(response.accessToken)
    setProfile(await authApi.fetchProfile())
    setSessionEnd(null)
    setStatus('authenticated')
  }, [])

  useEffect(() => {
    setRefreshHandler(async () => {
      try {
        const response = await refreshWithRetry()
        setAccessToken(response.accessToken)
        return true
      } catch {
        endSession('expired')
        return false
      }
    })
    return () => setRefreshHandler(null)
  }, [endSession])

  // Au chargement : la session est restaurée à partir du cookie de rafraîchissement s'il est encore valide.
  useEffect(() => {
    if (started.current) {
      return
    }
    started.current = true
    refreshWithRetry()
      .then(startSession)
      .catch(() => {
        setAccessToken(null)
        setStatus('anonymous')
      })
  }, [startSession])

  const value = useMemo<AuthState>(() => ({
    status,
    profile,
    sessionEnd,
    login: async (username, password) => {
      await startSession(await authApi.login(username, password))
    },
    logout: async () => {
      try {
        await authApi.logout()
      } finally {
        endSession('logout')
      }
    },
    completePasswordChange: startSession,
    reloadProfile: async () => {
      setProfile(await authApi.fetchProfile())
    },
    can: (permission, hotelId) => {
      if (!profile) {
        return false
      }
      if (profile.superAdmin) {
        return true
      }
      return profile.hotels.some((access) =>
        (hotelId == null || access.hotelId === hotelId) && access.permissions.includes(permission))
    },
  }), [status, profile, sessionEnd, startSession, endSession])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// oxlint-disable-next-line react/only-export-components
export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth doit être utilisé dans AuthProvider')
  }
  return context
}
