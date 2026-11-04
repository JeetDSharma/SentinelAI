// Small SVG building blocks for hand-drawn architecture and sequence diagrams.
import { useMarkers } from './useMarkers'

export type Tone = 'blue' | 'indigo' | 'violet' | 'teal' | 'orange' | 'gray' | 'green' | 'red'

const fill = (tone: Tone) => `var(--mantine-color-${tone}-0)`
const stroke = (tone: Tone) => `var(--mantine-color-${tone}-5)`
const ink = 'var(--mantine-color-dark-7)'
const dim = 'var(--mantine-color-dark-3)'

interface BoxProps {
  x: number; y: number; w: number; h: number
  title: string
  lines?: string[]
  tone?: Tone
  mono?: boolean
}

export function Box({ x, y, w, h, title, lines = [], tone = 'gray', mono }: BoxProps) {
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx={8} fill={fill(tone)} stroke={stroke(tone)} strokeWidth={1.5} />
      <text x={x + 12} y={y + 22} fontSize={14} fontWeight={700} fill={ink} fontFamily={mono ? 'monospace' : undefined}>
        {title}
      </text>
      {lines.map((line, i) => (
        <text key={i} x={x + 12} y={y + 40 + i * 16} fontSize={11.5} fill={dim}>{line}</text>
      ))}
    </g>
  )
}

interface ArrowProps {
  points: [number, number][]
  marker: string
  dashed?: boolean
  accent?: boolean
  label?: string
  labelAt?: [number, number]
  labelAnchor?: 'start' | 'middle' | 'end'
  vertical?: boolean
}

export function Arrow({ points, marker, dashed, accent, label, labelAt, labelAnchor = 'middle', vertical }: ArrowProps) {
  const d = points.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x},${y}`).join(' ')
  return (
    <g>
      <path d={d} fill="none" stroke={accent ? 'var(--mantine-color-indigo-6)' : 'var(--mantine-color-dark-4)'}
        strokeWidth={accent ? 2 : 1.5} strokeDasharray={dashed ? '5 4' : undefined} markerEnd={marker} />
      {label && labelAt && (
        <text x={labelAt[0]} y={labelAt[1]} fontSize={11.5} fontWeight={600} textAnchor={labelAnchor}
          fill={accent ? 'var(--mantine-color-indigo-7)' : dim}
          transform={vertical ? `rotate(-90 ${labelAt[0]} ${labelAt[1]})` : undefined}>
          {label}
        </text>
      )}
    </g>
  )
}

// ---- sequence diagram ---------------------------------------------------------------------------

export interface Participant { id: string; label: string; sub?: string; tone?: Tone }
export interface Message { from: string; to: string; label: string; reply?: boolean; accent?: boolean }
export interface Band { from: number; to: number; label: string; tone: Tone }

interface SequenceProps { participants: Participant[]; messages: Message[]; bands?: Band[]; colWidth?: number }

/** Lifelines generated from data: one row per message, optional shaded bands spanning message ranges. */
export function SequenceDiagram({ participants, messages, bands = [], colWidth = 140 }: SequenceProps) {
  const { defs, arrow, accent } = useMarkers()
  const headerH = 58
  const rowH = 42
  const width = participants.length * colWidth
  const height = headerH + messages.length * rowH + 30
  const x = (id: string) => participants.findIndex((p) => p.id === id) * colWidth + colWidth / 2
  const rowY = (i: number) => headerH + 26 + i * rowH

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ minWidth: width * 0.8, display: 'block' }}>
      {defs}
      {bands.map((b, i) => (
        <g key={i}>
          <rect x={4} y={rowY(b.from) - 26} width={width - 8} height={(b.to - b.from + 1) * rowH + 4} rx={6}
            fill={fill(b.tone)} stroke={stroke(b.tone)} strokeDasharray="4 4" />
          <text x={14} y={rowY(b.from) - 10} fontSize={11} fontWeight={700} fill={stroke(b.tone)}>
            {b.label}
          </text>
        </g>
      ))}
      {participants.map((p, i) => (
        <g key={p.id}>
          <line x1={x(p.id)} y1={headerH} x2={x(p.id)} y2={height - 8} stroke="var(--mantine-color-gray-4)" strokeDasharray="3 4" />
          <rect x={i * colWidth + 8} y={6} width={colWidth - 16} height={headerH - 14} rx={7}
            fill={fill(p.tone ?? 'gray')} stroke={stroke(p.tone ?? 'gray')} />
          <text x={x(p.id)} y={p.sub ? 26 : 33} fontSize={12.5} fontWeight={700} textAnchor="middle" fill={ink}>{p.label}</text>
          {p.sub && <text x={x(p.id)} y={41} fontSize={10.5} textAnchor="middle" fill={dim}>{p.sub}</text>}
        </g>
      ))}
      {messages.map((m, i) => {
        const y = rowY(i)
        const x1 = x(m.from)
        const x2 = x(m.to)
        const marker = m.accent ? accent : arrow
        const color = m.accent ? 'var(--mantine-color-indigo-6)' : 'var(--mantine-color-dark-4)'
        if (x1 === x2) {
          return (
            <g key={i}>
              <path d={`M${x1},${y - 6} h26 v14 h-24`} fill="none" stroke={color} strokeWidth={1.5} markerEnd={marker} />
              <text x={x1 + 32} y={y + 4} fontSize={11.5} fill={ink}>{m.label}</text>
            </g>
          )
        }
        const mid = (x1 + x2) / 2
        return (
          <g key={i}>
            <line x1={x1} y1={y} x2={x2 + (x2 > x1 ? -2 : 2)} y2={y} stroke={color} strokeWidth={m.accent ? 2 : 1.5}
              strokeDasharray={m.reply ? '5 4' : undefined} markerEnd={marker} />
            <text x={mid} y={y - 6} fontSize={11.5} textAnchor="middle" fill={m.reply ? dim : ink}
              fontWeight={m.accent ? 700 : 500}>{m.label}</text>
          </g>
        )
      })}
    </svg>
  )
}
