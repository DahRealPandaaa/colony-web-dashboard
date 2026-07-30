import type { ResourceEntry } from '../types/building'

export function pct(value: number, max: number): number {
  if (!max) return 0
  return Math.max(0, Math.min(100, Math.round((value / max) * 100)))
}

export function num(value?: number): string {
  return (value || 0).toLocaleString()
}

export function stacks(value: number, maxStackSize?: number): string {
  const count = Math.max(0, Math.trunc(Number(value) || 0))
  const size = Math.max(1, Math.trunc(Number(maxStackSize) || 64))
  const fullStacks = Math.floor(count / size)
  const remainder = count % size

  if (fullStacks === 0) return `${num(remainder)} (${num(count)})`

  const label = fullStacks === 1 ? 'stack' : 'stacks'
  const afterStacks = remainder ? ` + ${num(remainder)}` : ''
  return `${num(fullStacks)} ${label}${afterStacks} (${num(count)})`
}

export function badgeClass(action?: string): string {
  switch ((action || '').toUpperCase()) {
    case 'UPGRADE': return 'b-upgrade'
    case 'BUILD': return 'b-build'
    case 'REPAIR': return 'b-repair'
    case 'REMOVE': return 'b-remove'
    default: return ''
  }
}

export function statusOf(resource: ResourceEntry): 'ok' | 'deliver' | 'missing' {
  if (resource.inHut >= resource.needed) return 'ok'
  if (resource.deliverable) return 'deliver'
  return 'missing'
}

export function statusLabel(resource: ResourceEntry): string {
  switch (statusOf(resource)) {
    case 'ok': return 'Enough'
    case 'deliver': return 'Deliverable'
    default: return 'Missing'
  }
}

export function stateClass(state: string): string {
  if (state === 'COMPLETED') return 'completed'
  if (state === 'IN_PROGRESS') return 'in-progress'
  return 'not-started'
}

export function stateLabel(state: string): string {
  if (state === 'COMPLETED') return 'Done'
  if (state === 'IN_PROGRESS') return 'Researching'
  return 'Not started'
}

/** Case-insensitive "does any of these fields contain the query" test. */
export function matches(query: string, ...fields: (string | null | undefined)[]): boolean {
  if (!query) return true
  const needle = query.toLowerCase()
  return fields.some((field) => (field || '').toLowerCase().includes(needle))
}
