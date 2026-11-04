import type { Band, CandidateStatus, CaseStatus, SignalType } from './api'

export const bandColor: Record<Band, string> = { HIGH: 'red', MEDIUM: 'orange', LOW: 'gray' }

export const statusColor: Record<CandidateStatus, string> = {
  PENDING: 'gray',
  REVIEWED: 'blue',
  DISMISSED: 'teal',
  ESCALATED: 'red',
}

export const caseStatusColor: Record<CaseStatus, string> = { OPEN: 'yellow', COMPLETED: 'green' }

export const signalLabel: Record<SignalType, string> = {
  NAME_EXACT: 'Name — exact',
  NAME_SIMILAR: 'Name — similar',
  IDENTIFIER_MATCH: 'Identifier match',
  IDENTIFIER_MISMATCH: 'Identifier conflict',
  COUNTRY_MATCH: 'Country agrees',
  COUNTRY_MISMATCH: 'Country conflict',
  ENTITY_TYPE_MATCH: 'Entity type agrees',
  ENTITY_TYPE_MISMATCH: 'Entity type conflict',
}

const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
const time = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' })

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

/** "14:02" for today, otherwise "1 Nov 2026, 14:02". */
export function formatRecent(iso: string): string {
  const date = new Date(iso)
  return date.toDateString() === new Date().toDateString() ? time.format(date) : dateTime.format(date)
}

export function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase()
}
