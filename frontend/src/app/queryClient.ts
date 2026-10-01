import { QueryClient } from '@tanstack/react-query'

import { ApiError } from '../api/http'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Une erreur métier (4xx) ne se corrige pas en réessayant ; une panne réseau ou serveur, si.
        retry: (failureCount, error) =>
          failureCount < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  })
}
