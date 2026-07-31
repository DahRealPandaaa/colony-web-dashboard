import type { TabId } from './contexts/ColonyContext'

export interface TabDef {
  id: TabId
  label: string
  title: string
  subtitle: string
  tint: string
  iconEmoji: string
  /** Raw SVG path data */
  icon: string
}

export const TABS: TabDef[] = [
  {
    id: 'overview', label: 'Overview',
    title: 'Colony overview', subtitle: 'Everything at a glance',
    tint: 'overview', iconEmoji: '▦',
    icon: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/>'
      + '<rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
  },
  {
    id: 'map', label: 'Map',
    title: 'Colony map', subtitle: 'Where everything stands, and who is where',
    tint: 'map', iconEmoji: '🗺',
    icon: '<path d="M9 3.5 3.5 6v14.5L9 18l6 2.5 5.5-2.5V3.5L15 6z"/><path d="M9 3.5V18"/><path d="M15 6v14.5"/>',
  },
  {
    id: 'buildings', label: 'Buildings',
    title: 'Buildings & decorations', subtitle: 'What is built and what each site still needs',
    tint: 'buildings', iconEmoji: '🏛',
    icon: '<path d="M3 21h18"/><path d="M5 21V8l7-5 7 5v13"/><path d="M9 21v-6h6v6"/>',
  },
  {
    id: 'citizens', label: 'Citizens',
    title: 'Citizens', subtitle: 'Skills, mood and what everyone is carrying',
    tint: 'citizens', iconEmoji: '👤',
    icon: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/>'
      + '<path d="M17 11a3 3 0 1 0-1.6-5.5"/><path d="M18.5 20c0-2.2-.8-4.1-2.2-5.3"/>',
  },
  {
    id: 'research', label: 'Research',
    title: 'University research', subtitle: 'Finished, running and still locked',
    tint: 'research', iconEmoji: '🔬',
    icon: '<path d="M9 3h6"/><path d="M10 3v6.5L4.6 18A2 2 0 0 0 6.3 21h11.4a2 2 0 0 0 1.7-3L14 9.5V3"/>',
  },
  {
    id: 'combat', label: 'Combat',
    title: 'Colony defence', subtitle: 'Raid pressure, guards and guard posts',
    tint: 'combat', iconEmoji: '⚔',
    icon: '<path d="M12 3l7.5 3v5.7c0 4.5-3.2 8.4-7.5 9.8-4.3-1.4-7.5-5.3-7.5-9.8V6z"/>',
  },
  {
    id: 'warehouse', label: 'Warehouse',
    title: 'Warehouse stock', subtitle: 'Everything stored across the colony',
    tint: 'warehouse', iconEmoji: '📦',
    icon: '<path d="M3 8l9-5 9 5v8l-9 5-9-5z"/><path d="M3 8l9 5 9-5"/><path d="M12 13v8"/>',
  },
]
