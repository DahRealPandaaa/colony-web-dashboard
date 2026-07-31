import { useColony } from '../../contexts/ColonyContext'
import { useCitizens } from '../../hooks/useCitizens'
import type { CitizenSort } from '../../contexts/UiContext'
import CitizenCard from '../../components/CitizenCard/CitizenCard'
import Panel from '../../components/Panel/Panel'
import SearchInput from '../../components/SearchInput/SearchInput'

export function CitizensTab() {
  const { citizens, stats, loaded } = useColony()
  const c = useCitizens()

  const emptyMessage = () => {
    if (citizens.length) return 'No citizens match your filters.'
    return loaded.citizens ? 'This colony has no citizens.' : 'Loading citizens…'
  }

  return (
    <div className="animate-fade-up flex flex-col gap-4">
      {/* Toolbar */}
      <div className="toolbar">
        <SearchInput className="flex-1 min-w-[180px]" value={c.citizenSearch} onChange={c.setCitizenSearch}
          placeholder="Search citizens, jobs or buildings…" />
        <select className="field py-1.5" aria-label="Filter by job"
          value={c.citizenJob} onChange={e => c.setCitizenJob(e.target.value)}>
          <option value="">All jobs</option>
          {c.citizenJobs.map(j => <option key={j} value={j}>{j}</option>)}
        </select>
        <select className="field py-1.5" aria-label="Sort citizens"
          value={c.citizenSort} onChange={e => c.setCitizenSort(e.target.value as CitizenSort)}>
          <option value="job">Job</option>
          <option value="name">Name</option>
          <option value="skills">Total skill</option>
          <option value="happiness">Happiness</option>
          <option value="health">Lowest health</option>
        </select>
        <span className="chip on">{c.visibleCitizens.length} shown</span>
      </div>

      {/* Summary bar */}
      <div className="flex items-center gap-4 flex-wrap text-sm">
        <span className="text-text-secondary">
          <span className="text-text-primary font-semibold">{stats.citizens}</span> total
        </span>
        <span className="text-text-secondary">
          <span className="text-text-primary font-semibold">{stats.citizens - stats.unemployed - stats.children}</span> employed
        </span>
        <span className="text-text-secondary">
          <span className="text-text-primary font-semibold">{stats.unemployed}</span> idle
        </span>
        {stats.children > 0 && (
          <span className="text-text-secondary">
            <span className="text-text-primary font-semibold">{stats.children}</span> children
          </span>
        )}
      </div>

      <Panel title="Citizen roster" subtitle={`${citizens.length} total`} count={c.visibleCitizens.length}>
        <div className="grid grid-auto-wide gap-3">
          {c.visibleCitizens.map(citizen => (
            <CitizenCard key={citizen.id} citizen={citizen} skills={c.topSkills(citizen)}
              onOpen={() => c.openCitizen(citizen)} />
          ))}
        </div>
        {!c.visibleCitizens.length && (
          <div className="empty"><p className="empty-title">{emptyMessage()}</p></div>
        )}
      </Panel>
    </div>
  )
}
