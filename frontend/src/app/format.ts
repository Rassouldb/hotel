/** Formats d'affichage locaux (§37.2) : dates JJ/MM/AAAA, montants « 146 000 XOF ». */

export function formatBusinessDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
    .format(new Date(year, month - 1, day))
}

export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

export function formatAmount(amount: string | number, currencyCode: string): string {
  const fractionDigits = currencyCode === 'XOF' || currencyCode === 'XAF' ? 0 : 2
  const formatted = new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(Number(amount))
  return `${formatted} ${currencyCode}`
}
