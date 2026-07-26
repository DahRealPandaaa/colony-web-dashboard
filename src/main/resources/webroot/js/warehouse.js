import { matches } from "./format.js";

/**
 * Warehouse tab: search, sort and the Domum-only filter over aggregated colony stock.
 */
export function warehouseState() {
    return {
        whSearch: "",
        whSort: "count",
        whDomumOnly: false,

        /** Cap on rendered rows — the search box is how you reach the long tail. */
        whLimit: 400,

        get warehouseStacks() {
            let list = (this.snap.warehouse.stacks || []).slice();
            if (this.whDomumOnly) list = list.filter((s) => s.domum);

            const query = this.whSearch.trim();
            if (query) list = list.filter((s) => matches(query, s.name, s.material));

            list.sort(this.whSort === "alpha"
                ? (a, b) => (a.name || "").localeCompare(b.name || "")
                : (a, b) => b.count - a.count);

            return list.slice(0, this.whLimit);
        },

        get warehouseHidden() {
            const total = (this.snap.warehouse.stacks || []).length;
            return Math.max(0, total - this.whLimit);
        },
    };
}
