import { useResearch } from '../../hooks/useResearch'
import { pct } from '../../format/format'
import type { ResearchFilter } from '../../contexts/UiContext'
import StatTile from '../../components/StatTile/StatTile'
import ResearchCard from '../../components/ResearchCard/ResearchCard'
import { Meter, Progress } from '../../components/Meter/Meter'

export function ResearchTab() {
  const r = useResearch()
  const research = r.research

  if (!research) return <div className="empty">Loading research…</div>

  if (!research.available) {
    return (
      <div className="empty">
        <p className="empty-title">No research tree available.</p>
        <p className="mt-1">Build a university, or MineColonies exposed nothing to read.</p>
      </div>
    )
  }

  return (
    <div className="animate-fade-up">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
        <StatTile label="Completed" value={research.completed} valueClass="text-emerald-300">
          <Meter variant="done" pct={pct(research.completed, research.total)} className="mt-2" />
        </StatTile>
        <StatTile
          label="In progress"
          value={research.inProgress}
          valueClass="text-accent-soft"
          sub={`${research.total} researches total`}
        />
        <StatTile label="Branches" value={research.branches.length} />
        <StatTile label="Remaining" value={research.total - research.completed} />
      </div>

      {/* Anything the university is actually working on right now. */}
      {r.researchInProgress.length > 0 && (
        <section className="panel mb-4">
          <div className="panel-head">
            <div>
              <h2 className="panel-title">Underway</h2>
              <p className="panel-sub">Across every branch</p>
            </div>
          </div>
          <div className="panel-body grid grid-auto-wide gap-2.5">
            {r.researchInProgress.map(entry => (
              <div key={entry.id} className="card card-active p-3!">
                <div className="font-semibold text-[14.5px]">{entry.name}</div>
                <div className="text-[12.5px] text-slate-400">{entry.branch} · tier {entry.depth}</div>
                <Progress pct={pct(entry.progress, entry.maxProgress)} />
                <div className="text-[11.5px] text-accent-soft font-bold tabular-nums mt-1.5">
                  {pct(entry.progress, entry.maxProgress)}%
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="toolbar">
        <button className={`chip chip-btn${r.researchBranch === '' ? ' on' : ''}`}
          onClick={() => r.setResearchBranch('')}>
          All branches
        </button>
        {research.branches.map(b => (
          <button key={b.id} className={`chip chip-btn${r.researchBranch === b.id ? ' on' : ''}`}
            onClick={() => r.setResearchBranch(b.id)}>
            <span>{b.name}</span>
            <b className="tabular-nums">{b.completed}/{b.total}</b>
          </button>
        ))}
        <select className="field py-1.5 ml-auto" aria-label="Filter research"
          value={r.researchFilter} onChange={e => r.setResearchFilter(e.target.value as ResearchFilter)}>
          <option value="all">Everything</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="NOT_STARTED">Not started</option>
        </select>
      </div>

      <div className="space-y-4">
        {r.researchBranches.map(branch => {
          const entries = r.researchIn(branch)
          return (
            <section key={branch.id} className="panel">
              <div className="panel-head">
                <div>
                  <h2 className="panel-title">{branch.name}</h2>
                  <p className="panel-sub">
                    {branch.completed} completed · {branch.inProgress} in progress · {branch.total} total
                  </p>
                </div>
                <div className="w-28 shrink-0 pt-2">
                  <Meter variant="done" pct={pct(branch.completed, branch.total)} />
                </div>
              </div>
              <div className="panel-body grid grid-auto-wide gap-2.5">
                {entries.map(entry => <ResearchCard key={entry.id} entry={entry} />)}
                {!entries.length && <p className="empty">Nothing here matches the filter.</p>}
              </div>
            </section>
          )
        })}
      </div>
    </div>
  )
}
