import { useResearch } from '../../hooks/useResearch'
import { pct } from '../../format/format'
import type { ResearchFilter } from '../../contexts/UiContext'
import ResearchCard from '../../components/ResearchCard/ResearchCard'
import Panel from '../../components/Panel/Panel'
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

  // Deepest completed tier across all branches
  const maxDepth = Math.max(0, ...research.branches.map(b =>
    Math.max(0, ...b.researches.filter(e => e.state === 'COMPLETED').map(e => e.depth))
  ))

  return (
    <div className="animate-fade-up flex flex-col gap-4">
      {/* Stat row — compact */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5">
        <div className="tile p-2.5!">
          <div className="tile-label">Completed</div>
          <div className="text-lg font-bold font-display tabular-nums mt-1 text-success">{research.completed}</div>
          <Meter variant="done" pct={pct(research.completed, research.total)} className="mt-1.5" />
        </div>
        <div className="tile p-2.5!">
          <div className="tile-label">In progress</div>
          <div className="text-lg font-bold font-display tabular-nums mt-1 text-violet">{research.inProgress}</div>
          <div className="tile-sub mt-1!">{research.total} researches total</div>
        </div>
        <div className="tile p-2.5!">
          <div className="tile-label">Branches</div>
          <div className="text-lg font-bold font-display tabular-nums mt-1">{research.branches.length}</div>
          {maxDepth > 0 && <div className="tile-sub mt-1!">Deepest tier: {maxDepth}</div>}
        </div>
        <div className="tile p-2.5!">
          <div className="tile-label">Remaining</div>
          <div className="text-lg font-bold font-display tabular-nums mt-1">{research.total - research.completed}</div>
        </div>
      </div>

      {/* Underway */}
      {r.researchInProgress.length > 0 && (
        <Panel title="Underway" subtitle="Across every branch">
          <div className="grid grid-auto-wide gap-2.5">
            {r.researchInProgress.map(entry => (
              <div key={entry.id} className="card card-active p-3!">
                <div className="font-semibold text-sm">{entry.name}</div>
                <div className="text-xs text-text-secondary">{entry.branch} · tier {entry.depth}</div>
                <Progress pct={pct(entry.progress, entry.maxProgress)} />
                <div className="text-xs text-violet-300 font-bold tabular-nums mt-1.5">
                  {pct(entry.progress, entry.maxProgress)}%
                </div>
              </div>
            ))}
          </div>
        </Panel>
      )}

      {/* Branch filters */}
      <div className="toolbar">
        <button className={`chip chip-btn${r.researchBranch === '' ? ' on' : ''}`}
          onClick={() => r.setResearchBranch('')}>All branches</button>
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

      {/* Branch panels */}
      <div className="space-y-4">
        {r.researchBranches.map(branch => {
          const entries = r.researchIn(branch)
          return (
            <Panel key={branch.id} title={branch.name}
              subtitle={`${branch.completed} completed · ${branch.inProgress} in progress · ${branch.total} total`}>
              <div className="grid grid-auto-wide gap-2.5">
                {entries.map(entry => <ResearchCard key={entry.id} entry={entry} />)}
                {!entries.length && <p className="empty">Nothing here matches the filter.</p>}
              </div>
            </Panel>
          )
        })}
      </div>
    </div>
  )
}
