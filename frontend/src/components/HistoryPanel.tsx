import { Badge, Group, Paper, ScrollArea, Stack, Text, Title, UnstyledButton } from '@mantine/core'
import type { CaseSummary } from '../api'
import { caseStatusColor, formatRecent, titleCase } from '../format'
import classes from './HistoryPanel.module.css'

interface Props {
  cases: CaseSummary[]
  selectedId: number | null
  error: string | null
  onSelect: (id: number) => void
}

export function HistoryPanel({ cases, selectedId, error, onSelect }: Props) {
  return (
    <Paper withBorder p="md" radius="md">
      <Title order={5} mb="sm">Recent screenings</Title>
      {error && <Text size="sm" c="red">{error}</Text>}
      {!error && cases.length === 0 && <Text size="sm" c="dimmed">No screenings yet.</Text>}
      <ScrollArea.Autosize mah={420} offsetScrollbars>
        <Stack gap={4}>
          {cases.map((c) => (
            <UnstyledButton
              key={c.id}
              className={classes.row}
              data-active={c.id === selectedId || undefined}
              onClick={() => onSelect(c.id)}
            >
              <Group justify="space-between" wrap="nowrap" gap="xs">
                <div style={{ minWidth: 0 }}>
                  <Text size="sm" fw={500} truncate>{c.queryName}</Text>
                  <Text size="xs" c="dimmed">
                    #{c.id} · {titleCase(c.entityType)}{c.country ? ` · ${c.country}` : ''} · {formatRecent(c.createdAt)}
                  </Text>
                </div>
                <Stack gap={2} align="flex-end">
                  <Badge size="xs" variant="light" color={caseStatusColor[c.status]}>{c.status}</Badge>
                  <Text size="xs" c="dimmed">
                    {c.candidateCount === 0 ? 'no matches' : `${c.candidateCount} · top ${c.topScore}`}
                    {c.escalatedCount > 0 && <Text span size="xs" c="red" fw={600}> · {c.escalatedCount} esc.</Text>}
                  </Text>
                </Stack>
              </Group>
            </UnstyledButton>
          ))}
        </Stack>
      </ScrollArea.Autosize>
    </Paper>
  )
}
