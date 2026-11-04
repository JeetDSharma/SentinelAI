import { Button, Group, Paper, SegmentedControl, Stack, Text, TextInput, Title, UnstyledButton } from '@mantine/core'
import { IconSearch } from '@tabler/icons-react'
import { type FormEvent, useState } from 'react'
import type { EntityType, ScreeningRequest } from '../api'

interface Props {
  loading: boolean
  fieldErrors: Record<string, string>
  onScreen: (request: ScreeningRequest) => void
}

/** Known synthetic examples, each showing one matching behaviour. Keeps the live demo predictable. */
const EXAMPLES: { label: string; request: ScreeningRequest }[] = [
  { label: 'Viktor Petrof', request: { name: 'Viktor Petrof', entityType: 'PERSON', country: 'RU' } },
  { label: 'Li Wei', request: { name: 'Li Wei', entityType: 'PERSON' } },
  { label: 'Orion Global Trading Corp', request: { name: 'Orion Global Trading Corp', entityType: 'ORGANIZATION', country: 'AE' } },
  { label: 'Jose Garcia', request: { name: 'Jose Garcia', entityType: 'PERSON' } },
  { label: 'Petrova Consulting (as person)', request: { name: 'Petrova Consulting', entityType: 'PERSON' } },
]

export function ScreeningForm({ loading, fieldErrors, onScreen }: Props) {
  const [name, setName] = useState('')
  const [entityType, setEntityType] = useState<EntityType>('PERSON')
  const [country, setCountry] = useState('')
  const [identifier, setIdentifier] = useState('')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onScreen({
      name,
      entityType,
      country: country.trim() || undefined,
      identifier: identifier.trim() || undefined,
    })
  }

  const fill = (example: ScreeningRequest) => {
    setName(example.name)
    setEntityType(example.entityType)
    setCountry(example.country ?? '')
    setIdentifier(example.identifier ?? '')
    onScreen(example)
  }

  return (
    <Paper withBorder p="md" radius="md">
      <form onSubmit={submit} noValidate>
        <Stack gap="sm">
          <Title order={5}>Screen an entity</Title>
          <SegmentedControl
            fullWidth
            value={entityType}
            onChange={(value) => setEntityType(value as EntityType)}
            data={[
              { label: 'Person', value: 'PERSON' },
              { label: 'Organization', value: 'ORGANIZATION' },
            ]}
          />
          <TextInput
            label="Name"
            placeholder={entityType === 'PERSON' ? 'e.g. Viktor Petrov' : 'e.g. Northwind Maritime Holdings Ltd'}
            required
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
            error={fieldErrors.name}
            maxLength={200}
            data-autofocus
          />
          <Group grow align="flex-start">
            <TextInput
              label="Country"
              description="ISO code, optional"
              placeholder="RU"
              value={country}
              onChange={(e) => setCountry(e.currentTarget.value.toUpperCase())}
              error={fieldErrors.country}
              maxLength={2}
            />
            <TextInput
              label="Identifier"
              description="Passport / registration"
              placeholder="optional"
              value={identifier}
              onChange={(e) => setIdentifier(e.currentTarget.value)}
              error={fieldErrors.identifier}
              maxLength={100}
            />
          </Group>
          <Button type="submit" leftSection={<IconSearch size={16} />} loading={loading} disabled={!name.trim()}>
            Screen
          </Button>
          <div>
            <Text size="xs" c="dimmed" mb={4}>Demo examples</Text>
            <Group gap={6}>
              {EXAMPLES.map((example) => (
                <UnstyledButton key={example.label} onClick={() => fill(example.request)} disabled={loading}>
                  <Text size="xs" c="indigo" td="underline">{example.label}</Text>
                </UnstyledButton>
              ))}
            </Group>
          </div>
        </Stack>
      </form>
    </Paper>
  )
}
