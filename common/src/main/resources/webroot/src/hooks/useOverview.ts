import { useMemo } from 'react'
import { useColony } from '../contexts/ColonyContext'
import type { BuilderInfo } from '../types/building'

/** What a builder is currently doing, flattened for the template. */
export type BuilderTask =
  | { idle: true }
  | { idle: false; action: string; building: string; current: number; target: number; pct: number }

/**
 * Overview tab: the builder roster panel.
 *
 * A builder's headline is really their work order, so this resolves the two into one flat
 * object the card can read without any branching of its own.
 */
export function useOverview() {
  const { snap } = useColony()

  const builderInfo = (builder: BuilderInfo): BuilderTask => {
    const order = snap.workOrdersById[builder.assignedWorkOrderId]
    if (!order) return { idle: true }
    return {
      idle: false,
      action: order.action,
      building: order.buildingName || order.buildingType || 'Structure',
      current: order.currentLevel,
      target: order.targetLevel,
      pct: Math.round((order.progress || 0) * 100),
    }
  }

  /** Work orders with a builder on them first — those are the ones actually moving. */
  const activeWorkOrders = useMemo(() =>
    (snap.workOrders || []).slice().sort((a, b) =>
      (b.builderId >= 0 ? 1 : 0) - (a.builderId >= 0 ? 1 : 0)
      || (b.progress || 0) - (a.progress || 0)),
  [snap.workOrders])

  return { builderInfo, activeWorkOrders }
}
