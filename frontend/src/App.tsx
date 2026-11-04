import { Alert, AppShell, Badge, Grid, Group, Loader, Paper, Stack, Text, ThemeIcon, Title, Tooltip } from '@mantine/core'
import { IconAlertCircle, IconShieldSearch } from '@tabler/icons-react'
import { useCallback, useEffect, useState } from 'react'
import { ApiError, api, type CaseDetail, type CaseSummary, type Decision, type ScreeningRequest } from './api'
import { CaseView } from './components/CaseView'
import { HistoryPanel } from './components/HistoryPanel'
import { ScreeningForm } from './components/ScreeningForm'

const message = (e: unknown) => (e instanceof Error ? e.message : 'Unexpected error')

export default function App() {
  const [currentCase, setCurrentCase] = useState<CaseDetail | null>(null)
  const [selectedCandidateId, setSelectedCandidateId] = useState<number | null>(null)
  const [loadingCase, setLoadingCase] = useState(false)
  const [caseError, setCaseError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const [history, setHistory] = useState<CaseSummary[]>([])
  const [historyError, setHistoryError] = useState<string | null>(null)

  const [deciding, setDeciding] = useState(false)
  const [decisionError, setDecisionError] = useState<string | null>(null)

  /** Bumped after a failed request so the health badges re-check immediately instead of on the next poll. */
  const [healthCheck, setHealthCheck] = useState(0)

  const refreshHistory = useCallback(() => {
    api.recentCases()
      .then((cases) => { setHistory(cases); setHistoryError(null) })
      .catch((e) => setHistoryError(message(e)))
  }, [])

  useEffect(refreshHistory, [refreshHistory])

  const showCase = (c: CaseDetail, keepSelection = false) => {
    setCurrentCase(c)
    setDecisionError(null)
    if (!keepSelection) {
      setSelectedCandidateId(c.candidates[0]?.id ?? null)
    }
  }

  const screen = async (request: ScreeningRequest) => {
    setLoadingCase(true)
    setCaseError(null)
    setFieldErrors({})
    try {
      showCase(await api.screen(request))
      refreshHistory()
    } catch (e) {
      setFieldErrors(e instanceof ApiError ? e.fieldErrors : {})
      setCaseError(message(e))
      // Don't leave the previous case on screen under a new query: it would read as this search's result.
      setCurrentCase(null)
      setHealthCheck((n) => n + 1)
    } finally {
      setLoadingCase(false)
    }
  }

  const openCase = async (id: number) => {
    setLoadingCase(true)
    setCaseError(null)
    try {
      showCase(await api.getCase(id))
    } catch (e) {
      setCaseError(message(e))
    } finally {
      setLoadingCase(false)
    }
  }

  const decide = async (candidateId: number, decision: Decision, note: string): Promise<boolean> => {
    if (!currentCase) return false
    setDeciding(true)
    setDecisionError(null)
    try {
      const updated = await api.decide(currentCase.id, candidateId, decision, note)
      showCase(updated, true)
      // Move the analyst on to the next candidate that still needs a decision.
      const nextPending = updated.candidates.find((k) => k.status === 'PENDING')
      if (nextPending) setSelectedCandidateId(nextPending.id)
      refreshHistory()
      return true
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        // Someone else changed this candidate: show their decision, then let the analyst decide again.
        showCase(await api.getCase(currentCase.id), true)
      }
      setDecisionError(message(e))
      setHealthCheck((n) => n + 1)
      return false
    } finally {
      setDeciding(false)
    }
  }

  return (
    <AppShell header={{ height: 60 }} padding="md" bg="gray.0">
      <AppShell.Header px="md">
        <Group h="100%" justify="space-between">
          <Group gap="sm">
            <ThemeIcon size="lg" radius="md" variant="filled"><IconShieldSearch size={20} /></ThemeIcon>
            <div>
              <Title order={4} lh={1.1}>Sentinel</Title>
              <Text size="xs" c="dimmed" lh={1.1}>Entity screening workbench</Text>
            </div>
          </Group>
          <Group gap="sm">
            <Tooltip label="All watchlist data is synthetic. This tool does not make compliance decisions.">
              <Badge variant="outline" color="gray">Synthetic data · demo</Badge>
            </Tooltip>
            <HealthBadges trigger={healthCheck} />
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Main>
        <Grid gap="md">
          <Grid.Col span={{ base: 12, md: 4, xl: 3 }}>
            <Stack gap="md">
              <ScreeningForm loading={loadingCase} fieldErrors={fieldErrors} onScreen={screen} />
              <HistoryPanel cases={history} selectedId={currentCase?.id ?? null} error={historyError} onSelect={openCase} />
            </Stack>
          </Grid.Col>

          <Grid.Col span={{ base: 12, md: 8, xl: 9 }}>
            <Stack gap="md">
              {caseError && (
                <Alert color="red" variant="light" icon={<IconAlertCircle />} title="Screening failed"
                  withCloseButton onClose={() => setCaseError(null)}>
                  {caseError}
                </Alert>
              )}
              {loadingCase && !currentCase && <Group justify="center" py="xl"><Loader /></Group>}
              {currentCase ? (
                <div style={{ opacity: loadingCase ? 0.5 : 1, transition: 'opacity 150ms' }}>
                  <CaseView
                    screeningCase={currentCase}
                    selectedCandidateId={selectedCandidateId}
                    deciding={deciding}
                    decisionError={decisionError}
                    onSelectCandidate={(id) => { setSelectedCandidateId(id); setDecisionError(null) }}
                    onDecide={decide}
                  />
                </div>
              ) : !loadingCase && <EmptyState />}
            </Stack>
          </Grid.Col>
        </Grid>
      </AppShell.Main>
    </AppShell>
  )
}

function EmptyState() {
  return (
    <Paper withBorder p="xl" radius="md">
      <Title order={4} mb="xs">How screening works</Title>
      <Stack gap={6}>
        <Text size="sm"><b>1. Retrieve</b> — Elasticsearch finds watchlist records with similar names (typos, word order, accents, abbreviations) or the same identifier.</Text>
        <Text size="sm"><b>2. Score</b> — a deterministic scorer rates each record 0–100 and lists every point it awarded or deducted.</Text>
        <Text size="sm"><b>3. Review</b> — the analyst dismisses, reviews or escalates each candidate; every decision is kept as an audit trail.</Text>
      </Stack>
      <Text size="xs" c="dimmed" mt="md">Start with a demo example on the left.</Text>
    </Paper>
  )
}

function HealthBadges({ trigger }: { trigger: number }) {
  const [status, setStatus] = useState<Record<string, string> | null>(null)

  useEffect(() => {
    const check = () => api.health().then(setStatus).catch(() => setStatus({}))
    check()
    const timer = setInterval(check, 15_000)
    return () => clearInterval(timer)
  }, [trigger]) // re-runs (checks now, restarts the poll) whenever trigger changes

  if (!status) return null
  return (
    <Group gap={6}>
      {[['db', 'PostgreSQL'], ['elasticsearch', 'Elasticsearch']].map(([key, label]) => {
        const up = status[key] === 'UP'
        return (
          <Badge key={key} size="sm" variant="dot" color={up ? 'green' : 'red'}>
            {label}{up ? '' : ': down'}
          </Badge>
        )
      })}
    </Group>
  )
}
