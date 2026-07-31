import type { TabId } from '../../contexts/ColonyContext'

interface Props {
  tabs: { id: TabId; label: string }[]
  activeTab: TabId
  onTabChange: (id: TabId) => void
}

/** Sidebar replacement on narrow screens. */
export function TabNav({ tabs, activeTab, onTabChange }: Props) {
  return (
    <nav className="tabstrip">
      {tabs.map(t => (
        <button key={t.id} className={`tabchip${activeTab === t.id ? ' on' : ''}`}
          onClick={() => onTabChange(t.id)}>
          {t.label}
        </button>
      ))}
    </nav>
  )
}
