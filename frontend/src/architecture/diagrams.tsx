import { Arrow, Box, SequenceDiagram } from './diagram'
import { useMarkers } from './useMarkers'

/** Component view: who talks to whom, numbered along the screening path. */
export function SystemDiagram() {
  const { defs, arrow, accent } = useMarkers()
  return (
    <svg viewBox="0 0 1120 510" width="100%" style={{ minWidth: 900, display: 'block' }}>
      {defs}
      {/* outside the app */}
      <Box x={20} y={40} w={200} h={64} tone="blue" title="React UI" lines={['Mantine · TypeScript · Vite']} />
      <Box x={20} y={130} w={200} h={56} tone="blue" title="Vite dev proxy" lines={['/api, /actuator → :8080']} />
      <Arrow points={[[120, 104], [120, 130]]} marker={arrow} />
      <Box x={20} y={270} w={200} h={210} tone="orange" title="Elasticsearch 9.4"
        lines={['alias  watchlist', '   → watchlist-<timestamp>', '60 synthetic records', 'BM25 _score = rank only', 'single node · 1 shard']} />
      <Box x={860} y={380} w={240} h={120} tone="green" title="PostgreSQL 18"
        lines={['screening_case', 'case_candidate  (@Version)', 'review_decision  (append-only)', 'schema: Flyway V1 · V2']} />

      {/* the Spring Boot application */}
      <rect x={290} y={14} width={530} height={480} rx={12} fill="var(--mantine-color-white)" stroke="var(--mantine-color-gray-4)" strokeWidth={1.5} />
      <text x={304} y={34} fontSize={12} fontWeight={700} fill="var(--mantine-color-dark-3)">SPRING BOOT 4.1 · JAVA 21 · ONE DEPLOYABLE</text>

      <rect x={563} y={274} width={239} height={208} rx={10} fill="none" stroke="var(--mantine-color-green-6)" strokeDasharray="6 4" />
      <text x={683} y={478} fontSize={11} fontWeight={700} textAnchor="middle" fill="var(--mantine-color-green-7)">transaction boundary</text>

      <Box x={310} y={56} w={220} h={58} tone="indigo" title="ScreeningController" lines={['REST · @Valid · 201 + Location']} />
      <Box x={575} y={56} w={215} h={58} tone="red" title="ApiExceptionHandler" lines={['ProblemDetail 400·404·409·503']} />
      <Box x={310} y={168} w={220} h={58} tone="violet" title="ScreeningService" lines={['orchestrates · NO transaction']} />
      <Box x={575} y={168} w={215} h={58} tone="teal" title="MatchScoringService" lines={['pure Java · 0–100 + signals']} />
      <Box x={310} y={286} w={220} h={58} tone="orange" title="EntitySearchService" lines={['bool query · fuzzy · normalizers']} />
      <Box x={575} y={286} w={215} h={58} tone="green" title="CaseReviewService" lines={['@Transactional · version check']} />
      <Box x={310} y={410} w={220} h={58} tone="orange" title="WatchlistSeeder" lines={['startup · new index + alias swap']} />
      <Box x={575} y={410} w={215} h={58} tone="green" title="JPA repositories" lines={['Spring Data · Hibernate 7']} />

      <Arrow points={[[220, 158], [260, 158], [260, 85], [310, 85]]} marker={accent} accent />
      <Arrow points={[[400, 114], [400, 168]]} marker={accent} accent label="① screen" labelAt={[408, 146]} labelAnchor="start" />
      <Arrow points={[[530, 85], [575, 85]]} marker={arrow} dashed label="throws" labelAt={[552, 77]} />
      <Arrow points={[[400, 226], [400, 286]]} marker={accent} accent label="② search" labelAt={[408, 262]} labelAnchor="start" />
      <Arrow points={[[310, 315], [220, 315]]} marker={accent} accent label="③ query" labelAt={[265, 307]} />
      <Arrow points={[[530, 197], [575, 197]]} marker={accent} accent label="④ score" labelAt={[552, 158]} />
      <Arrow points={[[480, 226], [480, 256], [680, 256], [680, 286]]} marker={accent} accent label="⑤ save case" labelAt={[585, 250]} />
      <Arrow points={[[680, 344], [680, 410]]} marker={arrow} />
      <Arrow points={[[790, 439], [860, 439]]} marker={arrow} label="SQL" labelAt={[825, 431]} />
      <Arrow points={[[310, 439], [220, 439]]} marker={arrow} dashed label="bulk + alias" labelAt={[265, 431]} />
      <Arrow points={[[480, 56], [480, 42], [805, 42], [805, 315], [790, 315]]} marker={arrow}
        label="history · decisions" labelAt={[817, 190]} vertical />
      <text x={316} y={374} fontSize={11} fontStyle="italic" fill="var(--mantine-color-dark-3)">③ runs with no DB transaction open</text>
    </svg>
  )
}

export function ScreeningSequence() {
  return (
    <SequenceDiagram
      colWidth={138}
      participants={[
        { id: 'ui', label: 'React UI', tone: 'blue' },
        { id: 'ctl', label: 'Controller', sub: '@Valid', tone: 'indigo' },
        { id: 'svc', label: 'ScreeningService', sub: 'no @Transactional', tone: 'violet' },
        { id: 'search', label: 'EntitySearch', sub: 'Service', tone: 'orange' },
        { id: 'es', label: 'Elasticsearch', tone: 'orange' },
        { id: 'score', label: 'MatchScoring', sub: 'Service', tone: 'teal' },
        { id: 'review', label: 'CaseReview', sub: '@Transactional', tone: 'green' },
        { id: 'pg', label: 'PostgreSQL', tone: 'green' },
      ]}
      bands={[
        { from: 3, to: 8, label: 'NO TRANSACTION · NO DB CONNECTION', tone: 'gray' },
        { from: 9, to: 11, label: 'ONE SHORT TRANSACTION', tone: 'green' },
      ]}
      messages={[
        { from: 'ui', to: 'ctl', label: 'POST /api/screenings', accent: true },
        { from: 'ctl', to: 'ctl', label: 'validate → 400 + field errors' },
        { from: 'ctl', to: 'svc', label: 'screen(query)', accent: true },
        { from: 'svc', to: 'search', label: 'findCandidates', accent: true },
        { from: 'search', to: 'es', label: 'bool query, top 10', accent: true },
        { from: 'es', to: 'search', label: 'hits + BM25 _score', reply: true },
        { from: 'svc', to: 'score', label: 'score(query, record) × N' },
        { from: 'score', to: 'svc', label: '0–100 + signals', reply: true },
        { from: 'svc', to: 'svc', label: 'drop < 50, sort' },
        { from: 'svc', to: 'review', label: 'createCase(...)', accent: true },
        { from: 'review', to: 'pg', label: 'INSERT case + candidates · COMMIT', accent: true },
        { from: 'review', to: 'svc', label: 'CaseDetail (DTO)', reply: true },
        { from: 'ctl', to: 'ui', label: '201 Created + Location', reply: true },
      ]}
    />
  )
}

export function DecisionSequence() {
  return (
    <SequenceDiagram
      colWidth={200}
      participants={[
        { id: 'ui', label: 'React UI', tone: 'blue' },
        { id: 'ctl', label: 'Controller', tone: 'indigo' },
        { id: 'proxy', label: 'CaseReviewService', sub: 'via Spring proxy', tone: 'green' },
        { id: 'hib', label: 'Hibernate', sub: 'persistence context', tone: 'violet' },
        { id: 'pg', label: 'PostgreSQL', tone: 'green' },
      ]}
      bands={[{ from: 2, to: 10, label: 'ONE TRANSACTION — ALL OR NOTHING', tone: 'green' }]}
      messages={[
        { from: 'ui', to: 'ctl', label: 'POST …/decisions {ESCALATED, note}', accent: true },
        { from: 'ctl', to: 'proxy', label: 'recordDecision()', accent: true },
        { from: 'proxy', to: 'pg', label: 'proxy: BEGIN (pooled connection)' },
        { from: 'proxy', to: 'pg', label: 'SELECT candidate WHERE id AND case_id' },
        { from: 'proxy', to: 'pg', label: 'INSERT review_decision', accent: true },
        { from: 'proxy', to: 'hib', label: 'candidate.status = ESCALATED (memory)' },
        { from: 'proxy', to: 'hib', label: 'case.refreshStatus() (memory)' },
        { from: 'hib', to: 'pg', label: 'flush: UPDATE candidate … AND version = ?', accent: true },
        { from: 'hib', to: 'pg', label: 'UPDATE screening_case (only if changed)' },
        { from: 'pg', to: 'hib', label: 'rows updated: 1 (ok) or 0 (stale version)', reply: true },
        { from: 'hib', to: 'proxy', label: '1 → COMMIT · 0 → ROLLBACK + exception', reply: true },
        { from: 'ctl', to: 'ui', label: '201 + case  ·  or 409 Conflict', reply: true },
      ]}
    />
  )
}

/** Three tables, two one-to-many relationships. */
export function SchemaDiagram() {
  const { defs, arrow } = useMarkers()
  return (
    <svg viewBox="0 0 1000 190" width="100%" style={{ minWidth: 760, display: 'block' }}>
      {defs}
      <Box x={10} y={10} w={280} h={170} tone="green" title="screening_case" mono
        lines={['id  BIGSERIAL PK', 'query_name, entity_type', 'country, identifier', "status  OPEN | COMPLETED", 'created_at  timestamptz']} />
      <Box x={360} y={10} w={290} h={170} tone="green" title="case_candidate" mono
        lines={['id PK · case_id FK', 'watchlist_id, record_* (snapshot)', 'score, signals  JSONB', 'search_relevance', 'status PENDING|REVIEWED|…', 'version  (optimistic lock)']} />
      <Box x={720} y={10} w={270} h={170} tone="green" title="review_decision" mono
        lines={['id PK · candidate_id FK', 'decision', 'note  VARCHAR(2000)', 'decided_at', 'append-only audit trail']} />
      <Arrow points={[[290, 95], [360, 95]]} marker={arrow} label="1 → N" labelAt={[325, 87]} />
      <Arrow points={[[650, 95], [720, 95]]} marker={arrow} label="1 → N" labelAt={[685, 87]} />
    </svg>
  )
}
