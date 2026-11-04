import { Alert, Badge, Button, Divider, Grid, Group, Paper, Stack, Table, Text, Textarea, Timeline, Title, Tooltip } from '@mantine/core'
import { IconAlertTriangle, IconCheck, IconInfoCircle, IconX } from '@tabler/icons-react'
import { useState } from 'react'
import type { Candidate, Decision } from '../api'
import { bandColor, formatDateTime, signalLabel, statusColor, titleCase } from '../format'

interface Props {
  candidate: Candidate
  deciding: boolean
  error: string | null
  /** Resolves true when the decision was saved (the note is then cleared). */
  onDecide: (decision: Decision, note: string) => Promise<boolean>
}

const ACTIONS: { decision: Decision; label: string; color: string; icon: React.ReactNode }[] = [
  { decision: 'DISMISSED', label: 'Dismiss', color: 'teal', icon: <IconX size={16} /> },
  { decision: 'REVIEWED', label: 'Mark reviewed', color: 'blue', icon: <IconCheck size={16} /> },
  { decision: 'ESCALATED', label: 'Escalate', color: 'red', icon: <IconAlertTriangle size={16} /> },
]

export function CandidateDetail({ candidate: k, deciding, error, onDecide }: Props) {
  const [note, setNote] = useState('')
  const [pendingAction, setPendingAction] = useState<Decision | null>(null)
  const total = k.signals.reduce((sum, s) => sum + s.points, 0)

  const decide = async (decision: Decision) => {
    setPendingAction(decision)
    if (await onDecide(decision, note)) {
      setNote('')
    }
    setPendingAction(null)
  }

  return (
    <Paper withBorder p="md" radius="md">
      <Group justify="space-between" align="flex-start">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>Candidate evidence</Text>
          <Title order={4}>{k.name}</Title>
          <Text size="sm" c="dimmed">
            {k.watchlistId} · {titleCase(k.entityType)} · {k.country ?? 'no country'} · {k.program ?? 'no program'}
          </Text>
        </div>
        <Stack gap={2} align="flex-end">
          <Group gap={6}>
            <Text fz={32} fw={700} lh={1}>{k.matchScore}</Text>
            <Text c="dimmed" size="sm">/ 100</Text>
          </Group>
          <Badge variant="light" color={bandColor[k.band]}>{k.band} match</Badge>
        </Stack>
      </Group>

      <Grid mt="md" gap="lg">
        <Grid.Col span={{ base: 12, lg: 7 }}>
          <Text size="sm" fw={600} mb={4}>Why this score</Text>
          <Table verticalSpacing={6} withRowBorders>
            <Table.Tbody>
              {k.signals.map((s, i) => (
                <Table.Tr key={i}>
                  <Table.Td w={64}>
                    <Text size="sm" fw={700} ff="monospace" c={s.points > 0 ? 'green.8' : s.points < 0 ? 'red.7' : 'dimmed'}>
                      {s.points > 0 ? `+${s.points}` : s.points}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" fw={500}>{signalLabel[s.type]}</Text>
                    <Text size="xs" c="dimmed">{s.detail}</Text>
                  </Table.Td>
                </Table.Tr>
              ))}
              <Table.Tr>
                <Table.Td><Text size="sm" fw={700} ff="monospace">={Math.min(100, Math.max(0, total))}</Text></Table.Td>
                <Table.Td><Text size="xs" c="dimmed">Sum of signals, clamped to 0–100</Text></Table.Td>
              </Table.Tr>
            </Table.Tbody>
          </Table>

          <Alert mt="sm" variant="light" color="gray" icon={<IconInfoCircle size={18} />} p="xs">
            <Text size="xs">
              Search relevance <b>{k.searchRelevance.toFixed(1)}</b> (Elasticsearch BM25) only ranked this record
              among results for this search. It is not part of the match score and is not a risk measure.
            </Text>
          </Alert>
          {k.remarks && (
            <Text size="xs" c="dimmed" mt="sm"><b>Record remarks:</b> {k.remarks}</Text>
          )}
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 5 }}>
          <Group justify="space-between" mb={4}>
            <Text size="sm" fw={600}>Analyst review</Text>
            <Badge size="sm" variant={k.status === 'PENDING' ? 'outline' : 'filled'} color={statusColor[k.status]}>
              {k.status}
            </Badge>
          </Group>
          <Textarea
            placeholder="Optional note (e.g. reason for dismissal)"
            autosize
            minRows={2}
            maxRows={5}
            maxLength={2000}
            value={note}
            onChange={(e) => setNote(e.currentTarget.value)}
          />
          <Group gap="xs" mt="xs" grow>
            {ACTIONS.map((a) => (
              <Tooltip key={a.decision} label={`Record decision: ${a.decision}`} openDelay={500}>
                <Button
                  size="xs"
                  color={a.color}
                  variant={k.status === a.decision ? 'filled' : 'light'}
                  leftSection={a.icon}
                  loading={deciding && pendingAction === a.decision}
                  disabled={deciding && pendingAction !== a.decision}
                  onClick={() => decide(a.decision)}
                >
                  {a.label}
                </Button>
              </Tooltip>
            ))}
          </Group>
          {error && <Text size="xs" c="red" mt="xs">{error}</Text>}

          <Divider my="sm" label="Decision history" labelPosition="left" />
          {k.decisions.length === 0 ? (
            <Text size="xs" c="dimmed">No decisions recorded yet.</Text>
          ) : (
            <Timeline bulletSize={12} lineWidth={2} active={k.decisions.length - 1}>
              {k.decisions.map((d) => (
                <Timeline.Item key={d.id} color={statusColor[d.decision]}
                  title={<Text size="sm" fw={500}>{titleCase(d.decision)}</Text>}>
                  {d.note && <Text size="xs">{d.note}</Text>}
                  <Text size="xs" c="dimmed">{formatDateTime(d.decidedAt)}</Text>
                </Timeline.Item>
              ))}
            </Timeline>
          )}
        </Grid.Col>
      </Grid>
    </Paper>
  )
}
