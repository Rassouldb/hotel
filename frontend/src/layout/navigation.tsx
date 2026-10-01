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
  /** Permissions dont l'une suffit à afficher l'entrée (aucune : visible par tous). */
  anyOf?: string[]
  /** 'hotel' : permission vérifiée dans l'hôtel actif ; 'organization' : dans au moins un hôtel. */
  scope?: 'hotel' | 'organization'
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
      { key: 'reception', path: '/reception', icon: <ConciergeIcon />, availableIn: 6, anyOf: ['CHECKIN_CREATE', 'CHECKOUT_CREATE'] },
      { key: 'reservations', path: '/reservations', icon: <EventNoteIcon />, availableIn: 4, anyOf: ['RESERVATION_VIEW'] },
      { key: 'planning', path: '/planning', icon: <CalendarMonthIcon />, availableIn: 5, anyOf: ['RESERVATION_VIEW'] },
    ],
  },
  {
    key: 'operations',
    items: [
      { key: 'customers', path: '/clients', icon: <PeopleIcon />, availableIn: 3, anyOf: ['CUSTOMER_VIEW'] },
      { key: 'companies', path: '/entreprises', icon: <BusinessIcon />, availableIn: 3, anyOf: ['COMPANY_VIEW'] },
      { key: 'rooms', path: '/chambres', icon: <BedIcon />, availableIn: 2, anyOf: ['ROOM_VIEW'] },
      { key: 'housekeeping', path: '/housekeeping', icon: <CleaningServicesIcon />, availableIn: 6, anyOf: ['HK_VIEW'] },
      { key: 'maintenance', path: '/maintenance', icon: <HandymanIcon />, availableIn: 'later', anyOf: ['MAINTENANCE_VIEW'] },
      { key: 'restaurant', path: '/restaurant', icon: <RestaurantIcon />, availableIn: 'later', anyOf: ['POS_VIEW'] },
      { key: 'services', path: '/services', icon: <SpaIcon />, availableIn: 6, anyOf: ['FOLIO_CHARGE_POST'] },
    ],
  },
  {
    key: 'finance',
    items: [
      { key: 'invoicing', path: '/facturation', icon: <ReceiptLongIcon />, availableIn: 8, anyOf: ['INVOICE_VIEW'] },
      { key: 'payments', path: '/paiements', icon: <PaymentsIcon />, availableIn: 7, anyOf: ['PAYMENT_VIEW'] },
      { key: 'cash', path: '/caisse', icon: <PointOfSaleIcon />, availableIn: 7, anyOf: ['CASH_SESSION_OPEN', 'CASH_SUPERVISE'] },
      { key: 'expenses', path: '/depenses', icon: <WalletIcon />, availableIn: 'later', anyOf: ['EXPENSE_VIEW'] },
    ],
  },
  {
    key: 'management',
    items: [
      { key: 'stock', path: '/stocks', icon: <InventoryIcon />, availableIn: 'later', anyOf: ['STOCK_VIEW'] },
      { key: 'suppliers', path: '/fournisseurs', icon: <LocalShippingIcon />, availableIn: 'later', anyOf: ['SUPPLIER_MANAGE'] },
      { key: 'reports', path: '/rapports', icon: <AssessmentIcon />, availableIn: 9, anyOf: ['REPORT_OPERATIONS_VIEW', 'REPORT_FINANCE_VIEW'] },
      { key: 'nightAudit', path: '/cloture', icon: <NightsStayIcon />, availableIn: 8, anyOf: ['NIGHT_AUDIT_VIEW', 'NIGHT_AUDIT_RUN'] },
      {
        key: 'administration',
        path: '/administration',
        icon: <SettingsIcon />,
        availableIn: 1,
        anyOf: ['USER_VIEW', 'ROLE_MANAGE', 'AUDIT_VIEW', 'HOTEL_UPDATE', 'SETTINGS_UPDATE', 'TAX_MANAGE', 'PAYMENT_METHOD_MANAGE'],
        scope: 'organization',
      },
    ],
  },
]

export const allNavItems: NavItem[] = navigation.flatMap((section) => section.items)

/** Dernière itération livrée : les modules prévus plus tard affichent la page « en préparation ». */
export const CURRENT_ITERATION = 2

export function isDelivered(item: NavItem): boolean {
  return item.availableIn !== 'later' && item.availableIn <= CURRENT_ITERATION
}

type Can = (permission: string, hotelId?: string | null) => boolean

export function isNavItemVisible(item: NavItem, can: Can, activeHotelId: string | null): boolean {
  if (!item.anyOf) {
    return true
  }
  const hotelId = item.scope === 'organization' ? null : activeHotelId
  return item.anyOf.some((permission) => can(permission, hotelId))
}
