import { matches } from "./format.js";

/**
 * Needed tab: what the colony is short of, summed across every building site.
 *
 * The per-building modal answers "what does this site still want"; this answers "what does the
 * colony have to go and get". Only genuinely missing items appear — anything the hut already holds,
 * or the warehouse can deliver, is already somebody's job.
 */
export function neededState() {
    return {
        needSearch: "",
        needSort: "shortfall",

        /**
         * Every required resource grouped by item, keeping only what nothing in the colony covers.
         *
         * Warehouse stock is *not* summed: the server fills `inWarehouse` from a colony-wide count
         * repeated onto each building's copy of the entry, so adding them up would credit the same
         * chest once per site.
         */
        get neededAll() {
            const byKey = new Map();
            for (const building of (this.snap.buildings || [])) {
                for (const r of (building.required || [])) {
                    if (!r.itemKey) continue;
                    const item = byKey.get(r.itemKey);
                    if (!item) {
                        byKey.set(r.itemKey, { ...r, sites: 1 });
                        continue;
                    }
                    item.needed += r.needed;
                    item.inHut += r.inHut;
                    item.inWarehouse = Math.max(item.inWarehouse, r.inWarehouse);
                    item.craftable = item.craftable || r.craftable;
                    item.sites++;
                }
            }

            const missing = [];
            for (const item of byKey.values()) {
                item.shortfall = item.needed - item.inHut - item.inWarehouse;
                if (item.shortfall > 0) missing.push(item);
            }
            return missing;
        },

        /** The same list as the viewer sees it: searched, and sorted biggest gap first. */
        get neededItems() {
            let list = this.neededAll;

            const query = this.needSearch.trim();
            if (query) list = list.filter((i) => matches(query, i.name, i.material, i.variant));

            return list.sort(this.needSort === "alpha"
                ? (a, b) => (a.name || "").localeCompare(b.name || "")
                : (a, b) => b.shortfall - a.shortfall);
        },

        /** Total items still to find, regardless of the search box. */
        get neededTotal() {
            return this.neededAll.reduce((sum, i) => sum + i.shortfall, 0);
        },
    };
}
