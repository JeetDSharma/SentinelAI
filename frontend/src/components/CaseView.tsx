import { Badge, Group, Paper, Progress, Stack, Table, Text, Title } from '@mantine/core'
import { IconMoodEmpty } from '@tabler/icons-react'
import type { CaseDetail, Decision } from '../api'
import { bandColor, caseStatusColor, formatDateTime, statusColor, titleCase } from '../format'
import { CandidateDetail } from './CandidateDetail'

interface Props {
  screeningCase: CaseDetail
  selectedCandidateId: number | null
  deciding: boolean
  decisionError: string | null
  onSelectCandidate: (id: number) => void
  onDecide: (candidateId: number, decision: Decision, note: string) => Promise<boolean>
}

export function CaseView({ screeningCase: c, selectedCandidateId, deciding, decisionError, onSelectCandidate, onDecide }: Props) {
  const selected = c.candidates.find((k) => k.id === selectedCandidateId) ?? null
  const pending = c.candidates.filter((k) => k.status === 'PENDING').length

  return (
    <Stack gap="md">
      <Paper withBorder p="md" radius="md">
        <Group justify="space-between" align="flex-start" mb="sm">
          <div>
            <Text size="xs" c="dimmed" tt="uppercase" fw={600}>Screening case #{c.id}</Text>
            <Title order={3}>{c.queryName}</Title>
            <Text size="sm" c="dimmed">
              {titleCase(c.entityType)}
              {c.country && ` · ${c.country}`}
              {c.identifier && ` · ID ${c.identifier}`}
              {' · '}{formatDateTime(c.createdAt)}
            </Text>
          </div>
          <Stack gap={4} align="flex-end">
            <Badge size="lg" variant="light" color={caseStatusColor[c.status]}>{c.status}</Badge>
            {c.candidates.length > 0 && (
              <Text size="xs" c="dimmed">{pending} of {c.candidates.length} pending review</Text>
            )}
          </Stack>
        </Group>

        {c.candidates.length === 0 ? (
          <Group gap="xs" py="lg" justify="center" c="dimmed">
            <IconMoodEmpty size={20} />
            <Text size="sm">No watchlist candidates scored above the review threshold.</Text>
          </Group>
        ) : (
          <Table highlightOnHover verticalSpacing="xs" striped={false}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Watchlist record</Table.Th>
                <Table.Th>Country</Table.Th>
                <Table.Th>Program</Table.Th>
                <Table.Th w={220}>Match score</Table.Th>
                <Table.Th>Status</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {c.candidates.map((k) => (
                <Table.Tr
                  key={k.id}
                  onClick={() => onSelectCandidate(k.id)}
                  style={{ cursor: 'pointer' }}
                  bg={k.id === selectedCandidateId ? 'var(--mantine-color-indigo-0)' : undefined}
                >
                  <Table.Td>
                    <Text size="sm" fw={500}>{k.name}</Text>
                    <Text size="xs" c="dimmed">
                      {k.watchlistId} · {titleCase(k.entityType)}
                      {k.matchedName !== k.name && ` · matched alias “${k.matchedName}”`}
                    </Text>
                  </Table.Td>
                  <Table.Td><Text size="sm">{k.country ?? '—'}</Text></Table.Td>
                  <Table.Td><Text size="xs" ff="monospace">{k.program ?? '—'}</Text></Table.Td>
                  <Table.Td>
                    <Group gap="xs" wrap="nowrap">
                      <Text size="sm" fw={700} w={26} ta="right">{k.matchScore}</Text>
                      <Progress value={k.matchScore} color={bandColor[k.band]} size="sm" w={60} />
                      <Badge size="xs" variant="light" color={bandColor[k.band]}>{k.band}</Badge>
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Badge size="sm" variant={k.status === 'PENDING' ? 'outline' : 'filled'} color={statusColor[k.status]}>
                      {k.status}
                    </Badge>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Paper>

      {selected && (
        <CandidateDetail
          key={selected.id}
          candidate={selected}
          deciding={deciding}
          error={decisionError}
          onDecide={(decision, note) => onDecide(selected.id, decision, note)}
        />
      )}
    </Stack>
  )
}
