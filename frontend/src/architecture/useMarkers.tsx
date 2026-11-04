import { useId } from 'react'

/** Arrowhead marker definitions; returns ids to reference in markerEnd. */
export function useMarkers() {
  const id = useId().replace(/:/g, '')
  const defs = (
    <defs>
      <marker id={`${id}-a`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M0,0 L10,5 L0,10 z" fill="var(--mantine-color-dark-4)" />
      </marker>
      <marker id={`${id}-b`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
        <path d="M0,0 L10,5 L0,10 z" fill="var(--mantine-color-indigo-6)" />
      </marker>
    </defs>
  )
  return { defs, arrow: `url(#${id}-a)`, accent: `url(#${id}-b)` }
}
