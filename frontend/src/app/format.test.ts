import { describe, expect, it } from 'vitest'

import { formatAmount, formatBusinessDate, formatDate } from './format'

const normalizeSpaces = (value: string) => value.replace(/\s/g, ' ')

describe('formats locaux', () => {
  it('formats XOF amounts without decimals', () => {
    expect(normalizeSpaces(formatAmount('146000', 'XOF'))).toBe('146 000 XOF')
  })

  it('formats EUR amounts with two decimals', () => {
    expect(normalizeSpaces(formatAmount(12.5, 'EUR'))).toBe('12,50 EUR')
  })

  it('formats dates as DD/MM/YYYY', () => {
    expect(formatDate('2026-10-01')).toBe('01/10/2026')
  })

  it('spells out the business date in French', () => {
    expect(formatBusinessDate('2026-10-01')).toBe('jeudi 1 octobre 2026')
  })
})
