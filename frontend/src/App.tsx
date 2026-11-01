import { AppShell, Badge, Container, Group, Paper, Text, Title } from '@mantine/core'
import { useEffect, useState } from 'react'

type ComponentStatus = { status: string }
type Health = { status: string; components?: Record<string, ComponentStatus> }

// Phase 1 placeholder: proves the browser -> Vite proxy -> Spring Boot -> Postgres/Elasticsearch path works.
export default function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // Actuator returns 503 with the same JSON body when something is DOWN, so parse regardless of status.
    fetch('/actuator/health')
      .then((res) => res.json() as Promise<Health>)
      .then(setHealth)
      .catch(() => setError('Backend unreachable — is Spring Boot running on :8080?'))
  }, [])

  return (
    <AppShell header={{ height: 56 }} padding="md">
      <AppShell.Header px="md">
        <Group h="100%" justify="space-between">
          <Title order={4}>Sentinel · Entity Screening</Title>
          <Text size="xs" c="dimmed">Synthetic data · demo only</Text>
        </Group>
      </AppShell.Header>
      <AppShell.Main>
        <Container size="sm">
          <Paper withBorder p="lg" mt="lg">
            <Title order={5} mb="sm">System status</Title>
            {error && <Text c="red">{error}</Text>}
            {!error && !health && <Text c="dimmed">Checking…</Text>}
            {health && (
              <Group>
                {['db', 'elasticsearch'].map((name) => (
                  <StatusBadge key={name} name={name} status={health.components?.[name]?.status ?? 'UNKNOWN'} />
                ))}
              </Group>
            )}
          </Paper>
        </Container>
      </AppShell.Main>
    </AppShell>
  )
}

function StatusBadge({ name, status }: { name: string; status: string }) {
  return (
    <Badge color={status === 'UP' ? 'green' : 'red'} variant="light" size="lg">
      {name}: {status}
    </Badge>
  )
}
