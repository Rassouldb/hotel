import AssessmentIcon from '@mui/icons-material/Assessment'
import BedIcon from '@mui/icons-material/Bed'
import BusinessIcon from '@mui/icons-material/Business'
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth'
import CleaningServicesIcon from '@mui/icons-material/CleaningServices'
import ConciergeIcon from '@mui/icons-material/RoomService'
import DashboardIcon from '@mui/icons-material/Dashboard'
import EventNoteIcon from '@mui/icons-material/EventNote'
import HandymanIcon from '@mui/icons-material/Handyman'
import InventoryIcon from '@mui/icons-material/Inventory2'
import LocalShippingIcon from '@mui/icons-material/LocalShipping'
import NightsStayIcon from '@mui/icons-material/NightsStay'
import PaymentsIcon from '@mui/icons-material/Payments'
import PeopleIcon from '@mui/icons-material/People'
import PointOfSaleIcon from '@mui/icons-material/PointOfSale'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import RestaurantIcon from '@mui/icons-material/Restaurant'
import SettingsIcon from '@mui/icons-material/Settings'
import SpaIcon from '@mui/icons-material/Spa'
import WalletIcon from '@mui/icons-material/AccountBalanceWallet'
import type { ReactElement } from 'react'

export interface NavItem {
  /** Clé de traduction sous "nav". */
  key: string
  path: string
  icon: ReactElement
  /** Itération du MVP 1 qui livre le module (§45.1), ou phase ultérieure. */
  availableIn: number | 'later'
}

export interface NavSection {
  key: string | null
  items: NavItem[]
}

/** Menu principal du cahier des charges (§37.1). */
export const navigation: NavSection[] = [
  {
    key: null,
    items: [
      { key: 'dashboard', path: '/', icon: <DashboardIcon />, availableIn: 0 },
      { key: 'reception', path: '/reception', icon: <ConciergeIcon />, availableIn: 6 },
      { key: 'reservations', path: '/reservations', icon: <EventNoteIcon />, availableIn: 4 },
      { key: 'planning', path: '/planning', icon: <CalendarMonthIcon />, availableIn: 5 },
    ],
  },
  {
    key: 'operations',
    items: [
      { key: 'customers', path: '/clients', icon: <PeopleIcon />, availableIn: 3 },
      { key: 'companies', path: '/entreprises', icon: <BusinessIcon />, availableIn: 3 },
      { key: 'rooms', path: '/chambres', icon: <BedIcon />, availableIn: 2 },
      { key: 'housekeeping', path: '/housekeeping', icon: <CleaningServicesIcon />, availableIn: 6 },
      { key: 'maintenance', path: '/maintenance', icon: <HandymanIcon />, availableIn: 'later' },
      { key: 'restaurant', path: '/restaurant', icon: <RestaurantIcon />, availableIn: 'later' },
      { key: 'services', path: '/services', icon: <SpaIcon />, availableIn: 6 },
    ],
  },
  {
    key: 'finance',
    items: [
      { key: 'invoicing', path: '/facturation', icon: <ReceiptLongIcon />, availableIn: 8 },
      { key: 'payments', path: '/paiements', icon: <PaymentsIcon />, availableIn: 7 },
      { key: 'cash', path: '/caisse', icon: <PointOfSaleIcon />, availableIn: 7 },
      { key: 'expenses', path: '/depenses', icon: <WalletIcon />, availableIn: 'later' },
    ],
  },
  {
    key: 'management',
    items: [
      { key: 'stock', path: '/stocks', icon: <InventoryIcon />, availableIn: 'later' },
      { key: 'suppliers', path: '/fournisseurs', icon: <LocalShippingIcon />, availableIn: 'later' },
      { key: 'reports', path: '/rapports', icon: <AssessmentIcon />, availableIn: 9 },
      { key: 'nightAudit', path: '/cloture', icon: <NightsStayIcon />, availableIn: 8 },
      { key: 'administration', path: '/administration', icon: <SettingsIcon />, availableIn: 1 },
    ],
  },
]

export const allNavItems: NavItem[] = navigation.flatMap((section) => section.items)
