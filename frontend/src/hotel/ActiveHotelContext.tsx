import { useQuery } from '@tanstack/react-query'
import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'

import { fetchHotels, type Hotel } from '../api/hotels'

const STORAGE_KEY = 'pms.activeHotelId'

interface ActiveHotelState {
  hotels: Hotel[]
  activeHotel: Hotel | null
  isLoading: boolean
  error: unknown
  selectHotel: (hotelId: string) => void
  refetch: () => void
}

const ActiveHotelContext = createContext<ActiveHotelState | null>(null)

function readStoredHotelId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function storeHotelId(hotelId: string) {
  try {
    localStorage.setItem(STORAGE_KEY, hotelId)
  } catch {
    // Stockage indisponible (navigation privée) : le choix vaut pour la session en cours seulement.
  }
}

/**
 * Hôtel sur lequel travaille l'utilisateur. Toutes les données métier sont rattachées à un hôtel (§42).
 */
export function ActiveHotelProvider({ children }: { children: ReactNode }) {
  const [selectedId, setSelectedId] = useState<string | null>(readStoredHotelId)
  const query = useQuery({ queryKey: ['hotels'], queryFn: ({ signal }) => fetchHotels(signal) })

  const value = useMemo<ActiveHotelState>(() => {
    const hotels = query.data ?? []
    const activeHotel = hotels.find((hotel) => hotel.id === selectedId) ?? hotels[0] ?? null
    return {
      hotels,
      activeHotel,
      isLoading: query.isLoading,
      error: query.error,
      selectHotel: (hotelId: string) => {
        storeHotelId(hotelId)
        setSelectedId(hotelId)
      },
      refetch: () => void query.refetch(),
    }
  }, [query, selectedId])

  return <ActiveHotelContext.Provider value={value}>{children}</ActiveHotelContext.Provider>
}

// oxlint-disable-next-line react/only-export-components
export function useActiveHotel(): ActiveHotelState {
  const context = useContext(ActiveHotelContext)
  if (!context) {
    throw new Error('useActiveHotel doit être utilisé dans ActiveHotelProvider')
  }
  return context
}
