// Layout pieces for the architecture page(s).
import { Anchor, Box, Container, Group, Paper, ScrollArea, Text, ThemeIcon, Title } from '@mantine/core'
import { IconArrowLeft, IconShieldSearch } from '@tabler/icons-react'
import type { ReactNode } from 'react'

export function PageHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <Box bg="white" style={{ borderBottom: '1px solid var(--mantine-color-gray-3)' }} py="sm" mb="md">
      <Container size="xl">
        <Group justify="space-between">
          <Group gap="sm">
            <ThemeIcon size="lg" radius="md"><IconShieldSearch size={20} /></ThemeIcon>
            <div>
              <Title order={4} lh={1.1}>{title}</Title>
              <Text size="xs" c="dimmed" lh={1.1}>{subtitle}</Text>
            </div>
          </Group>
          <Anchor href="#/" size="sm"><Group gap={4}><IconArrowLeft size={14} />Back to workbench</Group></Anchor>
        </Group>
      </Container>
    </Box>
  )
}

export function Section({ title, children, note }: { title: string; children: ReactNode; note?: ReactNode }) {
  return (
    <Paper withBorder radius="md" p="md">
      <Title order={5} mb={note ? 2 : 'sm'}>{title}</Title>
      {note && <Text size="sm" c="dimmed" mb="sm">{note}</Text>}
      {children}
    </Paper>
  )
}

export function Diagram({ children }: { children: ReactNode }) {
  return <ScrollArea type="auto" offsetScrollbars>{children}</ScrollArea>
}

export function Point({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <Text size="sm" fw={600}>{title}</Text>
      <Text size="sm" c="dimmed">{children}</Text>
    </div>
  )
}
