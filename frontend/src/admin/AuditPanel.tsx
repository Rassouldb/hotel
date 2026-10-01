import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp'
import {
  Box,
  Chip,
  Collapse,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Fragment, useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { AuditFilters, AuditLog } from '../api/administration'
import { fetchAuditLogs } from '../api/administration'
import { ErrorAlert } from '../components/ErrorAlert'

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value))
}

/** Convertit une date du sélecteur (AAAA-MM-JJ, heure locale) en borne ISO pour l'API. */
function dayBoundary(day: string, endOfDay: boolean): string | undefined {
  if (!day) {
    return undefined
  }
  const date = new Date(`${day}T00:00:00`)
  if (endOfDay) {
    date.setDate(date.getDate() + 1)
  }
  return date.toISOString()
}

/** Consultation du journal d'audit (US-AUD-01). */
export function AuditPanel() {
  const { t } = useTranslation()
  const [filters, setFilters] = useState<AuditFilters>({ action: '', entityType: '', result: '' })
  const [fromDay, setFromDay] = useState('')
  const [toDay, setToDay] = useState('')
  const deferred = useDeferredValue(filters)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(25)
  const [expanded, setExpanded] = useState<string | null>(null)

  const logs = useQuery({
    queryKey: ['audit', deferred, fromDay, toDay, page, size],
    queryFn: () => fetchAuditLogs({
      ...deferred,
      action: deferred.action?.trim().toUpperCase(),
      entityType: deferred.entityType?.trim(),
      from: dayBoundary(fromDay, false),
      to: dayBoundary(toDay, true),
      page: page + 1,
      size,
    }),
    placeholderData: keepPreviousData,
  })

  const update = (patch: Partial<AuditFilters>) => {
    setFilters((current) => ({ ...current, ...patch }))
    setPage(0)
  }

  return (
    <Stack spacing={2}>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <TextField size="small" fullWidth label={t('audit.action')} placeholder="USER_CREATE" value={filters.action}
            onChange={(event) => update({ action: event.target.value })} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <TextField size="small" fullWidth label={t('audit.entityType')} placeholder="User" value={filters.entityType}
            onChange={(event) => update({ entityType: event.target.value })} />
        </Grid>
        <Grid size={{ xs: 12, sm: 4, md: 2 }}>
          <TextField select size="small" fullWidth label={t('audit.result')} value={filters.result}
            onChange={(event) => update({ result: event.target.value as AuditFilters['result'] })}>
            <MenuItem value="">{t('audit.results.all')}</MenuItem>
            <MenuItem value="SUCCESS">{t('audit.results.SUCCESS')}</MenuItem>
            <MenuItem value="FAILURE">{t('audit.results.FAILURE')}</MenuItem>
          </TextField>
        </Grid>
        <Grid size={{ xs: 6, sm: 4, md: 2.5 }}>
          <TextField size="small" fullWidth type="date" label={t('audit.from')} value={fromDay}
            onChange={(event) => { setFromDay(event.target.value); setPage(0) }} slotProps={{ inputLabel: { shrink: true } }} />
        </Grid>
        <Grid size={{ xs: 6, sm: 4, md: 2.5 }}>
          <TextField size="small" fullWidth type="date" label={t('audit.to')} value={toDay}
            onChange={(event) => { setToDay(event.target.value); setPage(0) }} slotProps={{ inputLabel: { shrink: true } }} />
        </Grid>
      </Grid>

      {logs.error ? <ErrorAlert error={logs.error} onRetry={() => void logs.refetch()} /> : null}

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell padding="checkbox" />
              <TableCell>{t('audit.date')}</TableCell>
              <TableCell>{t('audit.user')}</TableCell>
              <TableCell>{t('audit.action')}</TableCell>
              <TableCell>{t('audit.entityType')}</TableCell>
              <TableCell>{t('audit.result')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(logs.data?.data ?? []).map((log) => (
              <Fragment key={log.id}>
                <TableRow hover>
                  <TableCell padding="checkbox">
                    <IconButton size="small" aria-label={t('audit.details')}
                      onClick={() => setExpanded(expanded === log.id ? null : log.id)}>
                      {expanded === log.id ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                    </IconButton>
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(log.occurredAt)}</TableCell>
                  <TableCell>{log.userName ?? t('audit.system')}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{log.action}</TableCell>
                  <TableCell>{log.entityType}</TableCell>
                  <TableCell>
                    <Chip size="small" color={log.result === 'SUCCESS' ? 'success' : 'error'} label={t(`audit.results.${log.result}`)} />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell colSpan={6} sx={{ py: 0, borderBottom: expanded === log.id ? undefined : 'none' }}>
                    <Collapse in={expanded === log.id} unmountOnExit>
                      <AuditDetails log={log} />
                    </Collapse>
                  </TableCell>
                </TableRow>
              </Fragment>
            ))}
            {logs.data && logs.data.data.length === 0 && (
              <TableRow><TableCell colSpan={6}><Typography color="text.secondary">{t('audit.empty')}</Typography></TableCell></TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={logs.data?.meta?.total ?? 0}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, value) => setPage(value)}
          onRowsPerPageChange={(event) => { setSize(Number(event.target.value)); setPage(0) }}
          rowsPerPageOptions={[25, 50, 100]}
          labelRowsPerPage={t('common.rowsPerPage')}
        />
      </TableContainer>
    </Stack>
  )
}

function AuditDetails({ log }: { log: AuditLog }) {
  const { t } = useTranslation()
  const json = (value: unknown) => (value == null ? '—' : JSON.stringify(value, null, 2))

  return (
    <Box sx={{ py: 2 }}>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        {t('audit.entityId')} : {log.entityId ?? '—'} · IP : {log.ip ?? '—'} · traceId : {log.traceId ?? '—'}
        {log.errorCode ? ` · ${log.errorCode}` : ''}
      </Typography>
      {log.reason && <Typography variant="body2" gutterBottom>{t('audit.reason')} : {log.reason}</Typography>}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Typography variant="caption" color="text.secondary">{t('audit.before')}</Typography>
          <Box component="pre" sx={{ m: 0, p: 1, bgcolor: 'action.hover', borderRadius: 1, overflow: 'auto', fontSize: 12 }}>
            {json(log.oldValue)}
          </Box>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Typography variant="caption" color="text.secondary">{t('audit.after')}</Typography>
          <Box component="pre" sx={{ m: 0, p: 1, bgcolor: 'action.hover', borderRadius: 1, overflow: 'auto', fontSize: 12 }}>
            {json(log.newValue)}
          </Box>
        </Grid>
      </Grid>
    </Box>
  )
}
