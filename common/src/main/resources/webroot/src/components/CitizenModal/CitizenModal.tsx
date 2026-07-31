import { pct } from '../../format/format'
import { citizenArt, citizenIconFallback } from '../../hooks/useIcons'
import { useCitizens } from '../../hooks/useCitizens'
import ModalShell from '../Modal/ModalShell'
import ItemTooltip from '../ItemTooltip/ItemTooltip'
import EquipmentStrip from '../EquipmentStrip/EquipmentStrip'
import { Meter } from '../Meter/Meter'
import Close from '../icons/Close'
import Shield from '../icons/Shield'

/** The per-citizen detail modal: mood, skills, perks, kit and what they are carrying. */
export default function CitizenModal() {
  const c = useCitizens()
  const citizen = c.citizen
  if (!citizen) return null

  const equipment = c.citizenEquipment
  const inventory = c.citizenInventory
  const armour = c.armorPointsOf(equipment)

  return (
    <ModalShell onClose={c.closeCitizen}>
      <div className="modal-head">
        <div className="flex items-center gap-3.5 min-w-0">
          <img
            className="w-16 h-20 object-cover object-top rounded-lg bg-black/30 border border-line shrink-0"
            src={citizenArt(citizen)} alt={citizen.job}
            onError={e => citizenIconFallback(e.currentTarget, citizen)}
          />
          <div className="min-w-0">
            <h3 className="text-xl font-bold tracking-tight flex items-center gap-2.5 flex-wrap">
              <span>{citizen.name}</span>
              <span className="chip on">{citizen.job}</span>
              {citizen.child && <span className="ba b-repair">Child</span>}
            </h3>
            <div className="flex gap-3 text-text-secondary text-sm mt-1 flex-wrap">
              {citizen.workBuilding && <span>Works at {citizen.workBuilding}</span>}
              {citizen.homeBuilding && <span>Lives at {citizen.homeBuilding}</span>}
              <span className="tabular-nums">{citizen.x}, {citizen.y}, {citizen.z}</span>
            </div>
          </div>
        </div>
        <button className="btn-icon shrink-0" onClick={c.closeCitizen} aria-label="Close">
          <Close size={16} />
        </button>
      </div>

      <div className="modal-body">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5">
          <div className="tile p-2.5!">
            <div className="tile-label">Health</div>
            <div className="text-lg font-bold font-display tabular-nums mt-1">{citizen.health.toFixed(0)}</div>
            <Meter variant="hp" pct={pct(citizen.health, citizen.maxHealth)} className="mt-1.5" />
          </div>
          <div className="tile p-2.5!">
            <div className="tile-label">Saturation</div>
            <div className="text-lg font-bold font-display tabular-nums mt-1">{citizen.saturation.toFixed(1)}</div>
            <Meter variant="food" pct={pct(citizen.saturation, 20)} className="mt-1.5" />
          </div>
          <div className="tile p-2.5!">
            <div className="tile-label">Happiness</div>
            <div className="text-lg font-bold font-display tabular-nums mt-1">{citizen.happiness.toFixed(1)}</div>
            <Meter variant="happy" pct={pct(citizen.happiness, 10)} className="mt-1.5" />
          </div>
          <div className="tile p-2.5!">
            <div className="tile-label">Total skill</div>
            <div className="text-lg font-bold font-display tabular-nums mt-1">{citizen.skillTotal}</div>
            <div className="tile-sub mt-1!">{citizen.status || (citizen.spawned ? 'In the world' : 'Not loaded')}</div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 mt-5">
          <section className="min-w-0">
            <h4 className="panel-title mb-2.5">Skills</h4>
            <div className="space-y-2">
              {(citizen.skills || []).map(s => (
                <div key={s.name}>
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="flex items-center gap-1.5">
                      <span>{s.name}</span>
                      {s.role && <span className="ba b-upgrade">{s.role}</span>}
                    </span>
                    <b className="tabular-nums text-violet-300">{s.level}</b>
                  </div>
                  <Meter variant="xp" pct={pct(s.level, 99)} />
                </div>
              ))}
              {!(citizen.skills || []).length && <p className="empty py-3!">No skill data.</p>}
            </div>

            <h4 className="panel-title mt-6 mb-2.5">Perks &amp; grievances</h4>
            <div className="flex flex-wrap gap-1.5">
              {(citizen.modifiers || []).map((m, i) => (
                <span key={i} className={`chip ${m.factor > 1 ? 'good' : m.factor < 1 ? 'bad' : ''}`}>
                  <span>{m.name}</span>
                  <b className="tabular-nums">×{m.factor.toFixed(2)}</b>
                </span>
              ))}
              {!(citizen.modifiers || []).length && <p className="empty py-3!">Nothing notable.</p>}
            </div>
          </section>

          <section className="min-w-0">
            {equipment.length > 0 && (
              <>
                <div className="flex items-center justify-between mb-2.5">
                  <h4 className="panel-title">Equipped</h4>
                  {armour > 0 && (
                    <span className="chip">
                      <Shield size={12} strokeWidth={2} />
                      <b className="tabular-nums">{armour}</b>
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-1.5 flex-wrap mb-5">
                  <EquipmentStrip equipment={equipment} />
                </div>
              </>
            )}

            <div className="flex items-center justify-between mb-2.5">
              <h4 className="panel-title">Inventory</h4>
              <span className="chip">{citizen.inventoryUsed} / {citizen.inventorySize} slots</span>
            </div>
            <div className="space-y-2 max-h-[46vh] overflow-y-auto pr-1">
              {inventory.map((item, i) => (
                <ItemTooltip
                  key={i}
                  item={item}
                  defaultExpanded
                  lines={<div className="mc-slot">slot {item.slot}</div>}
                  right={<div className="mc-count">x{item.count}</div>}
                />
              ))}
              {!inventory.length && <p className="empty">Carrying nothing.</p>}
            </div>
          </section>
        </div>
      </div>
    </ModalShell>
  )
}
