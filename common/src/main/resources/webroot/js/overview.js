/**
 * Overview tab: the builder roster panel.
 *
 * A builder's headline is really their work order, so this resolves the two into one flat
 * object the template can read without any branching of its own.
 */
export function overviewState() {
    return {
        /** What a builder is currently doing, flattened for the template. */
        builderInfo(builder) {
            const order = this.snap.workOrdersById[builder.assignedWorkOrderId];
            if (!order) return { idle: true };
            return {
                idle: false,
                action: order.action,
                building: order.buildingName || order.buildingType || "Structure",
                current: order.currentLevel,
                target: order.targetLevel,
                pct: Math.round((order.progress || 0) * 100),
            };
        },

        /** Work orders with a builder on them first — those are the ones actually moving. */
        get activeWorkOrders() {
            return (this.snap.workOrders || []).slice().sort((a, b) =>
                (b.builderId >= 0 ? 1 : 0) - (a.builderId >= 0 ? 1 : 0)
                || (b.progress || 0) - (a.progress || 0));
        },
    };
}
