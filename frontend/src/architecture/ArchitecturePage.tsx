import { Box, Container, SimpleGrid, Stack, Table, Tabs } from '@mantine/core'
import { DecisionSequence, ScreeningSequence, SchemaDiagram, SystemDiagram } from './diagrams'
import { Diagram, PageHeader, Point, Section } from './parts'

/**
 * Presentation view for screen sharing (http://localhost:5173/#/architecture). Not linked from the workbench.
 * Deliberately lean: architecture and data flow only.
 */
export default function ArchitecturePage() {
  return (
    <Box bg="gray.0" mih="100vh" pb="xl">
      <PageHeader title="Sentinel — architecture" subtitle="Components and how data flows through them" />
      <Container size="xl">
        <Tabs defaultValue="overview" keepMounted={false} variant="pills" radius="md">
          <Tabs.List mb="md">
            <Tabs.Tab value="overview">System overview</Tabs.Tab>
            <Tabs.Tab value="screening">Screening flow</Tabs.Tab>
            <Tabs.Tab value="decisions">Review flow</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="overview"><Overview /></Tabs.Panel>
          <Tabs.Panel value="screening"><ScreeningFlow /></Tabs.Panel>
          <Tabs.Panel value="decisions"><ReviewFlow /></Tabs.Panel>
        </Tabs>
      </Container>
    </Box>
  )
}

function Overview() {
  return (
    <Stack gap="md">
      <Section title="System architecture" note="Modular monolith: one Spring Boot app, two stores, each doing what it is best at.">
        <Diagram><SystemDiagram /></Diagram>
      </Section>
      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <Section title="Data ownership">
          <Stack gap="xs">
            <Point title="Elasticsearch — watchlist">Synthetic records, seeded at startup; used for fuzzy name retrieval.</Point>
            <Point title="PostgreSQL — cases & decisions">Screening cases, a snapshot of each candidate, and the analyst&apos;s decisions.</Point>
          </Stack>
        </Section>
        <Section title="Stack">
          <Table fz="sm" verticalSpacing={3}>
            <Table.Tbody>
              {[
                ['Backend', 'Java 21 · Spring Boot 4.1 · Spring MVC · Data JPA'],
                ['Search', 'Elasticsearch 9.4 · official Java client'],
                ['Database', 'PostgreSQL 18 · Flyway · Hibernate 7'],
                ['Frontend', 'React 19 · TypeScript · Vite · Mantine 9'],
                ['Tests', 'JUnit 6 · Mockito · Testcontainers 2'],
                ['Ops', 'Docker Compose · Actuator · GitHub Actions'],
              ].map(([k, v]) => (
                <Table.Tr key={k}><Table.Td fw={600} w={90}>{k}</Table.Td><Table.Td>{v}</Table.Td></Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Section>
      </SimpleGrid>
    </Stack>
  )
}

function ScreeningFlow() {
  return (
    <Section title="Screening a name — POST /api/screenings"
      note="Search and score first, with no database transaction open; then save the case in one short transaction.">
      <Diagram><ScreeningSequence /></Diagram>
    </Section>
  )
}

function ReviewFlow() {
  return (
    <Stack gap="md">
      <Section title="Recording a decision — POST …/decisions"
        note="One transaction: store the decision, update the candidate, complete the case when nothing is pending.">
        <Diagram><DecisionSequence /></Diagram>
      </Section>
      <Section title="Database schema">
        <Diagram><SchemaDiagram /></Diagram>
      </Section>
    </Stack>
  )
}
