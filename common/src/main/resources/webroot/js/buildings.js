import { textureUrl } from "./api.js";
import { matches, statusOf } from "./format.js";

/**
 * Buildings tab: the card grid, its filters, and the per-building detail modal.
 */
export function buildingsState() {
    return {
        search: "",
        sort: "status",
        onlyInProgress: false,
        showDecorations: true,

        building: null,
        buildingSearch: "",

        // ---- work-order lookups ----

        /** The work order for a building, via the index built when the snapshot loads. */
        workOrder(building) {
            return this.snap.workOrdersById[building.workOrderId] || null;
        },

        workOrderTargetLevel(building) {
            const order = this.workOrder(building);
            return order ? order.targetLevel : 0;
        },

        actionOf(building) {
            const order = this.workOrder(building);
            return order ? order.action : null;
        },

        buildingProgress(building) {
            const order = this.workOrder(building);
            return order ? Math.round((order.progress || 0) * 100) : 0;
        },

        builtBy(building) {
            const order = this.workOrder(building);
            return order && order.builderName ? order.builderName : null;
        },

        /** How many required resources are satisfied / deliverable / missing. */
        resourceCounts(building) {
            const counts = { ok: 0, deliver: 0, missing: 0 };
            (building.required || []).forEach((r) => counts[statusOf(r)]++);
            return counts;
        },

        /** Prefer the real MineColonies hut block placed at the site. */
        buildingIcon(building) {
            if (building.blockId) return textureUrl(building.blockId);
            if (building.kind === "decoration") {
                const first = (building.required || [])[0];
                return textureUrl(first ? first.itemKey : "minecolonies:blockhutbuilder");
            }
            const path = (building.type || "").split(":").pop().replace(/[^a-z0-9_]/g, "");
            return textureUrl("minecolonies:blockhut" + path);
        },

        // ---- list ----

        get visibleBuildings() {
            let list = (this.snap.buildings || []).slice();
            if (!this.showDecorations) list = list.filter((b) => b.kind !== "decoration");
            if (this.onlyInProgress) list = list.filter((b) => b.beingBuilt);

            const query = this.search.trim();
            if (query) {
                list = list.filter((b) => matches(query, b.name)
                    || (b.required || []).some((r) => matches(query, r.name, r.material, r.variant)));
            }
            return this.sortBuildings(list);
        },

        sortBuildings(list) {
            const key = this.sort;
            return list.sort((a, b) => {
                if (key === "name") return (a.name || "").localeCompare(b.name || "");
                if (key === "progress") return this.buildingProgress(b) - this.buildingProgress(a);
                if (key === "level") return b.level - a.level;
                // "status": in-progress first, then whatever is missing the most.
                const inProgress = (b.beingBuilt ? 1 : 0) - (a.beingBuilt ? 1 : 0);
                if (inProgress !== 0) return inProgress;
                return this.resourceCounts(b).missing - this.resourceCounts(a).missing;
            });
        },

        // ---- detail modal ----

        openBuilding(building) {
            this.building = building;
            this.buildingSearch = "";
        },

        closeBuilding() {
            this.building = null;
        },

        /** Requirements for the open building, searched and sorted missing-first. */
        buildingResources() {
            if (!this.building) return [];
            let list = (this.building.required || []).slice();
            const query = this.buildingSearch.trim();
            if (query) list = list.filter((r) => matches(query, r.name, r.material, r.variant));

            const rank = { missing: 0, deliver: 1, ok: 2 };
            return list.sort((a, b) =>
                (rank[statusOf(a)] - rank[statusOf(b)]) || (b.needed - a.needed));
        },

        /** Keep the open modal in sync when a live update replaces the snapshot. */
        refreshOpenBuilding(snapshot) {
            if (!this.building) return;
            const updated = snapshot.buildings.find((b) => b.id === this.building.id);
            if (updated) {
                // Preserve object identity so the modal transition does not replay.
                Object.assign(this.building, updated);
            } else {
                this.building = null;
            }
        },
    };
}
