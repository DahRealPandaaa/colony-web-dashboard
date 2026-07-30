import { textureUrl } from '../../api'
import { badgeClass, stacks, statusLabel, statusOf } from '../../format/format'
import { buildingArt, buildingIcon, buildingIconFallback } from '../../hooks/useIcons'
import { useBuildings } from '../../hooks/useBuildings'
import ModalShell from '../Modal/ModalShell'
import ItemTooltip from '../ItemTooltip/ItemTooltip'
import SearchInput from '../SearchInput/SearchInput'
import EmptyState from '../EmptyState/EmptyState'
import { Progress } from '../Meter/Meter'
import Close from '../icons/Close'

/** The per-building detail modal: what it is, who is building it, and what it still needs. */
export default function BuildingModal() {
  const b = useBuildings()
  const building = b.building
  if (!building) return null

  const required = building.required || []
  const targetLevel = b.workOrderTargetLevel(building)
  const action = b.actionOf(building)
  const progress = b.buildingProgress(building)
  const builder = b.builtBy(building)
  const resources = b.buildingResources

  return (
    <ModalShell onClose={b.closeBuilding}>
      <div className="modal-head">
        <div className="flex items-center gap-3.5 min-w-0">
          <img
            className="w-16 h-16 object-contain pixelated rounded-lg bg-black/25 border border-line p-1 shrink-0"
            loading="lazy" src={buildingArt(building) || buildingIcon(building)} alt={building.name}
            onError={e => buildingIconFallback(e.currentTarget, building)}
          />
          <div className="min-w-0">
            <h3 className="text-[21px] font-bold tracking-tight flex items-center gap-2.5 flex-wrap">
              <span>{building.name}</span>
              {action && <span className={`ba ${badgeClass(action)}`}>{action}</span>}
              {building.kind === 'decoration' && <span className="ba b-repair">Decoration</span>}
            </h3>
            <div className="flex gap-3 text-slate-400 text-[13.5px] mt-1 flex-wrap">
              <span className="tabular-nums">
                {targetLevel ? `Level ${building.level} → ${targetLevel}` : `Level ${building.level}`}
              </span>
              <span className="tabular-nums">{building.x}, {building.y}, {building.z}</span>
            </div>
          </div>
        </div>
        <button className="btn-icon shrink-0" onClick={b.closeBuilding} aria-label="Close">
          <Close size={16} />
        </button>
      </div>

      <div className="modal-body">
        {building.beingBuilt && (
          <div className="card flex items-center gap-3.5 mb-4">
            <img className="w-14 h-14 pixelated rounded-lg bg-black/25 border border-line p-1 shrink-0"
              src={textureUrl('minecolonies:blockhutbuilder')} alt="" />
            <div className="flex-1">
              <Progress pct={progress} big className="mt-0!" />
              <div className="flex justify-between text-[12.5px] mt-2">
                <span className="text-slate-400">
                  {builder && <>Being built by <b className="text-accent-soft">{builder}</b></>}
                </span>
                <b className="text-accent-soft tabular-nums">{progress}%</b>
              </div>
            </div>
          </div>
        )}

        <div className="flex items-center justify-between gap-3 flex-wrap mb-3">
          <div>
            <h4 className="panel-title">Required resources</h4>
            <p className="panel-sub">Missing items first</p>
          </div>
          {required.length > 0 && (
            <SearchInput value={b.buildingSearch} onChange={b.setBuildingSearch}
              placeholder="Search" iconSize={14} />
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {/* Keyed by index: two required stacks can collapse to one item key. */}
          {resources.map((r, i) => (
            <ItemTooltip
              key={i}
              item={r}
              lines={
                <div className="mc-line">
                  Hut <span className="mc-val">{r.inHut}</span> ·
                  Warehouse <span className="mc-val">{r.inWarehouse}</span>
                </div>
              }
              right={
                <div className="text-right shrink-0">
                  <div className="mc-count">{stacks(r.needed, r.maxStackSize)}</div>
                  <span className={`pill mt-1.5 ${statusOf(r)}`}>{statusLabel(r)}</span>
                </div>
              }
            />
          ))}
        </div>

        {!resources.length && required.length > 0 && (
          <p className="empty">No items match your search.</p>
        )}
        {!required.length && <p className="empty">No pending requirements.</p>}
      </div>
    </ModalShell>
  )
}
