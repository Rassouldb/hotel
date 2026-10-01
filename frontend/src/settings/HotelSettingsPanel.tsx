import { Alert, Stack, Tab, Tabs } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { Channel, PaymentMethod, PaymentMethodType, Reason, ReasonDomain } from '../api/configuration'
import {
  fetchChannels,
  fetchPaymentMethods,
  fetchReasons,
  REASON_DOMAINS,
  saveChannel,
  savePaymentMethod,
  saveReason,
} from '../api/configuration'
import { useAuth } from '../auth/AuthContext'
import { useActiveHotel } from '../hotel/ActiveHotelContext'
import { HotelInformationForm } from './HotelInformationForm'
import { OperationsForm } from './OperationsForm'
import { ReferenceListPanel } from './ReferenceListPanel'
import { TaxesPanel } from './TaxesPanel'

type SettingsTab = 'information' | 'operations' | 'taxes' | 'payments' | 'channels' | 'reasons'
const TABS: SettingsTab[] = ['information', 'operations', 'taxes', 'payments', 'channels', 'reasons']
const PAYMENT_TYPES: PaymentMethodType[] = ['CASH', 'CARD', 'TRANSFER', 'CHEQUE', 'MOBILE_MONEY', 'OTHER']

/** Paramétrage de l'hôtel actif (US-HOTEL-02, 03, US-TAX-01, US-PAY-01, US-REF-01). */
export function HotelSettingsPanel() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const { activeHotel } = useActiveHotel()
  const [tab, setTab] = useState<SettingsTab>('information')

  if (!activeHotel) {
    return <Alert severity="warning">{t('errors.noHotel')}</Alert>
  }
  const hotelId = activeHotel.id
  const editable = {
    information: can('HOTEL_UPDATE', hotelId),
    operations: can('SETTINGS_UPDATE', hotelId),
    taxes: can('TAX_MANAGE', hotelId),
    payments: can('PAYMENT_METHOD_MANAGE', hotelId),
    channels: can('SETTINGS_UPDATE', hotelId),
    reasons: can('SETTINGS_UPDATE', hotelId),
  }

  return (
    <Stack spacing={2}>
      <Tabs value={tab} onChange={(_, value: SettingsTab) => setTab(value)} variant="scrollable" allowScrollButtonsMobile>
        {TABS.map((key) => <Tab key={key} value={key} label={t(`settings.tabs.${key}`)} />)}
      </Tabs>
      {!editable[tab] && <Alert severity="info">{t('settings.readOnly')}</Alert>}
      {tab === 'information' && <HotelInformationForm key={`${hotelId}-${activeHotel.version}`} hotel={activeHotel} canEdit={editable.information} />}
      {tab === 'operations' && <OperationsForm hotelId={hotelId} canEdit={editable.operations} />}
      {tab === 'taxes' && (
        <TaxesPanel hotelId={hotelId} currency={activeHotel.currencyCode} businessDate={activeHotel.businessDate} canEdit={editable.taxes} />
      )}
      {tab === 'payments' && (
        <ReferenceListPanel<PaymentMethod>
          queryKey={['payment-methods', hotelId]}
          load={() => fetchPaymentMethods(hotelId)}
          save={(item) => savePaymentMethod(hotelId, item as unknown as PaymentMethod)}
          canEdit={editable.payments}
          addLabel={t('settings.payments.add')}
          emptyItem={{ code: '', name: '', type: 'MOBILE_MONEY', requiresCashSession: false, requiresReference: true, active: true, sortOrder: 10 }}
          fields={[
            { key: 'code', label: t('settings.code'), kind: 'text', createOnly: true, column: true },
            { key: 'name', label: t('settings.name'), kind: 'text', column: true },
            {
              key: 'type', label: t('settings.payments.type'), kind: 'select', createOnly: true, column: true,
              options: PAYMENT_TYPES.map((type) => ({ value: type, label: t(`settings.payments.types.${type}`) })),
            },
            { key: 'requiresCashSession', label: t('settings.payments.cashSession'), kind: 'boolean', column: true },
            { key: 'requiresReference', label: t('settings.payments.reference'), kind: 'boolean', column: true },
            { key: 'sortOrder', label: t('settings.order'), kind: 'number' },
          ]}
        />
      )}
      {tab === 'channels' && (
        <ReferenceListPanel<Channel>
          queryKey={['channels', hotelId]}
          load={() => fetchChannels(hotelId)}
          save={(item) => saveChannel(hotelId, item as unknown as Channel)}
          canEdit={editable.channels}
          addLabel={t('settings.channels.add')}
          emptyItem={{ code: '', name: '', commissionRate: null, active: true, sortOrder: 10 }}
          fields={[
            { key: 'code', label: t('settings.code'), kind: 'text', createOnly: true, column: true },
            { key: 'name', label: t('settings.name'), kind: 'text', column: true },
            {
              key: 'commissionRate', label: t('settings.channels.commission'), kind: 'number', column: true,
              display: (value) => (value == null ? '' : `${value} %`),
            },
            { key: 'sortOrder', label: t('settings.order'), kind: 'number' },
          ]}
        />
      )}
      {tab === 'reasons' && (
        <ReferenceListPanel<Reason>
          queryKey={['reasons', hotelId]}
          load={() => fetchReasons(hotelId)}
          save={(item) => saveReason(hotelId, item as unknown as Reason)}
          canEdit={editable.reasons}
          addLabel={t('settings.reasons.add')}
          emptyItem={{ domain: 'CANCELLATION', code: '', label: '', active: true, sortOrder: 10 }}
          fields={[
            {
              key: 'domain', label: t('settings.reasons.domain'), kind: 'select', createOnly: true, column: true,
              options: REASON_DOMAINS.map((domain: ReasonDomain) => ({ value: domain, label: t(`settings.reasons.domains.${domain}`) })),
            },
            { key: 'code', label: t('settings.code'), kind: 'text', createOnly: true, column: true },
            { key: 'label', label: t('settings.name'), kind: 'text', column: true },
            { key: 'sortOrder', label: t('settings.order'), kind: 'number' },
          ]}
        />
      )}
    </Stack>
  )
}
