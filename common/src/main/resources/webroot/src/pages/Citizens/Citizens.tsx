import { useColony } from '../../contexts/ColonyContext'
import { useCitizens } from '../../hooks/useCitizens'
import type { CitizenSort } from '../../contexts/UiContext'
import CitizenCard from '../../components/CitizenCard/CitizenCard'
import SearchInput from '../../components/SearchInput/SearchInput'

export function CitizensTab() {
  const { citizens, loaded } = useColony()
  const c = useCitizens()

  const emptyMessage = () => {
    if (citizens.length) return 'No citizens match your filters.'
    return loaded.citizens ? 'This colony has no citizens.' : 'Loading citizens…'
  }

  return (
    <div className="animate-fade-up">
      <div className="toolbar">
        <SearchInput
          className="flex-1 min-w-[200px]"
          value={c.citizenSearch}
          onChange={c.setCitizenSearch}
          placeholder="Search citizens, jobs or buildings…"
        />
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
        <span className="chip on ml-auto">{c.visibleCitizens.length} shown</span>
      </div>

      <div className="grid grid-auto-wide gap-3.5">
        {c.visibleCitizens.map(citizen => (
          <CitizenCard
            key={citizen.id}
            citizen={citizen}
            skills={c.topSkills(citizen)}
            onOpen={() => c.openCitizen(citizen)}
          />
        ))}
      </div>

      {!c.visibleCitizens.length && (
        <div className="empty">
          <p className="empty-title">{emptyMessage()}</p>
        </div>
      )}
    </div>
  )
}
