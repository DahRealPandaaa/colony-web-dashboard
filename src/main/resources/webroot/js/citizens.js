import { api, textureUrl } from "./api.js";
import { matches } from "./format.js";

/**
 * Citizens tab: the roster, its filters, and the per-citizen detail modal (skills, perks and
 * inventory). Inventories are fetched on demand so the roster stays small.
 */
export function citizensState() {
    return {
        citizenSearch: "",
        citizenJob: "",
        citizenSort: "job",

        citizen: null,
        citizenInventory: [],

        get citizenJobs() {
            const jobs = new Set();
            this.citizens.forEach((c) => c.job && jobs.add(c.job));
            return Array.from(jobs).sort();
        },

        get visibleCitizens() {
            let list = this.citizens.slice();
            if (this.citizenJob) list = list.filter((c) => c.job === this.citizenJob);

            const query = this.citizenSearch.trim();
            if (query) {
                list = list.filter((c) =>
                    matches(query, c.name, c.job, c.workBuilding, c.homeBuilding));
            }
            return this.sortCitizens(list);
        },

        sortCitizens(list) {
            const key = this.citizenSort;
            return list.sort((a, b) => {
                if (key === "name") return (a.name || "").localeCompare(b.name || "");
                if (key === "skills") return b.skillTotal - a.skillTotal;
                if (key === "happiness") return b.happiness - a.happiness;
                if (key === "health") {
                    return (a.health / (a.maxHealth || 1)) - (b.health / (b.maxHealth || 1));
                }
                return (a.job || "").localeCompare(b.job || "")
                    || (a.name || "").localeCompare(b.name || "");
            });
        },

        /** The citizen's job skills, or their three best when they have no job. */
        topSkills(citizen) {
            const roled = (citizen.skills || []).filter((s) => s.role);
            if (roled.length) return roled;
            return (citizen.skills || []).slice().sort((a, b) => b.level - a.level).slice(0, 3);
        },

        citizenIcon(citizen) {
            return textureUrl(citizen.jobIcon || "minecolonies:blockhuttownhall");
        },

        healthPct(citizen) {
            return citizen.maxHealth ? (citizen.health / citizen.maxHealth) * 100 : 0;
        },

        // ---- detail modal ----

        async openCitizen(citizen) {
            this.citizen = citizen;
            this.citizenInventory = [];
            await this.loadCitizenDetail();
        },

        closeCitizen() {
            this.citizen = null;
            this.citizenInventory = [];
        },

        async loadCitizenDetail() {
            if (!this.citizen || this.colonyId == null) return;
            await this.guarded(async () => {
                const data = await api.citizen(this.colonyId, this.citizen.id);
                // Keep object identity so the modal transition does not replay.
                Object.assign(this.citizen, data.citizen);
                this.citizenInventory = data.inventory || [];
            });
        },
    };
}
