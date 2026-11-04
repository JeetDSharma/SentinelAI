// Typed client for the Sentinel REST API. Types mirror the Java records (CaseDetail, CaseSummary, ...).

export type EntityType = 'PERSON' | 'ORGANIZATION'
export type Band = 'HIGH' | 'MEDIUM' | 'LOW'
export type CandidateStatus = 'PENDING' | 'REVIEWED' | 'DISMISSED' | 'ESCALATED'
export type Decision = Exclude<CandidateStatus, 'PENDING'>
export type CaseStatus = 'OPEN' | 'COMPLETED'

export type SignalType =
  | 'NAME_EXACT' | 'NAME_SIMILAR'
  | 'IDENTIFIER_MATCH' | 'IDENTIFIER_MISMATCH'
  | 'COUNTRY_MATCH' | 'COUNTRY_MISMATCH'
  | 'ENTITY_TYPE_MATCH' | 'ENTITY_TYPE_MISMATCH'

export interface MatchSignal { type: SignalType; points: number; detail: string }

export interface DecisionEntry { id: number; decision: Decision; note: string | null; decidedAt: string }

export interface Candidate {
  id: number
  watchlistId: string
  name: string
  entityType: EntityType
  country: string | null
  program: string | null
  remarks: string | null
  matchedName: string
  matchScore: number
  band: Band
  signals: MatchSignal[]
  /** Elasticsearch BM25 _score: ranks hits within one query only. Not a risk score. */
  searchRelevance: number
  status: CandidateStatus
  decisions: DecisionEntry[]
}

export interface CaseDetail {
  id: number
  queryName: string
  entityType: EntityType
  country: string | null
  identifier: string | null
  status: CaseStatus
  createdAt: string
  candidates: Candidate[]
}

export interface CaseSummary {
  id: number
  queryName: string
  entityType: EntityType
  country: string | null
  status: CaseStatus
  createdAt: string
  candidateCount: number
  topScore: number | null
  pendingCount: number
  escalatedCount: number
}

export interface ScreeningRequest {
  name: string
  entityType: EntityType
  country?: string
  identifier?: string
}

/** RFC 9457 problem details as returned by ApiExceptionHandler. */
interface ProblemDetail { status?: number; title?: string; detail?: string; errors?: Record<string, string> }

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...init?.headers },
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the Sentinel backend. Is it running on port 8080?')
  }
  if (response.ok) {
    return (await response.json()) as T
  }
  const problem = (await response.json().catch(() => ({}))) as ProblemDetail
  // Vite's dev proxy answers 500/502 with an empty body when the backend is down.
  const message = problem.detail ?? (response.status >= 500
    ? 'Cannot reach the Sentinel backend. Is it running on port 8080?'
    : `Request failed (${response.status})`)
  throw new ApiError(response.status, message, problem.errors)
}

export const api = {
  screen: (body: ScreeningRequest) =>
    request<CaseDetail>('/api/screenings', { method: 'POST', body: JSON.stringify(body) }),

  getCase: (id: number) => request<CaseDetail>(`/api/screenings/${id}`),

  recentCases: (limit = 15) => request<CaseSummary[]>(`/api/screenings?limit=${limit}`),

  decide: (caseId: number, candidateId: number, decision: Decision, note: string) =>
    request<CaseDetail>(`/api/screenings/${caseId}/candidates/${candidateId}/decisions`, {
      method: 'POST',
      body: JSON.stringify({ decision, note: note.trim() || undefined }),
    }),

  health: async (): Promise<Record<string, string>> => {
    // Actuator replies 503 with the same JSON body when a component is DOWN, so read it regardless of status.
    const res = await fetch('/actuator/health')
    const body = (await res.json()) as { components?: Record<string, { status: string }> }
    return Object.fromEntries(Object.entries(body.components ?? {}).map(([k, v]) => [k, v.status]))
  },
}
