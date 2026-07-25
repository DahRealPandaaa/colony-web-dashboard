"use strict";

// Registered before Alpine starts (this script loads before alpine.min.js defer).
document.addEventListener("alpine:init", () => {
    Alpine.data("dashboard", () => ({
        colonies: [],
        colonyId: null,
        snap: { builders: [], workOrders: [], buildings: [], warehouse: { present: false, stacks: [] } },

        search: "",
        sort: "status",
        onlyInProgress: false,
        showDecorations: true,
        connection: "connecting",

        modal: null,
        modalSearch: "",

        whSearch: "",
        whSort: "count",
        warehouseOpen: false,
        whModalSearch: "",
        whModalSort: "count",

        init() {
            this.loadColonies();
            this.connectEvents();
        },

        // ---- helpers ----
        textureUrl(key) {
            return "/textures/" + encodeURIComponent(key) + ".png";
        },

        async fetchJson(url) {
            const res = await fetch(url, { cache: "no-store" });
            if (!res.ok) throw new Error(url + " -> " + res.status);
            return res.json();
        },

        // ---- data loading ----
        async loadColonies() {
            try {
                const colonies = await this.fetchJson("/api/colonies");
                this.colonies = colonies;
                if (this.colonyId == null && colonies.length) {
                    const hash = parseInt(location.hash.replace("#", ""), 10);
                    this.colonyId = colonies.some((c) => c.id === hash) ? hash : colonies[0].id;
                }
                if (this.colonyId != null) {
                    await this.loadColony(this.colonyId);
                }
            } catch (e) {
                console.error(e);
            }
        },

        async selectColony(id) {
            this.colonyId = id;
            location.hash = String(id);
            this.modal = null;
            await this.loadColony(id);
        },

        async loadColony(id) {
            try {
                const snap = await this.fetchJson("/api/colony/" + id);
                this.snap = snap;
                if (this.modal) {
                    this.modal = snap.buildings.find((b) => b.id === this.modal.id) || null;
                }
            } catch (e) {
                console.error(e);
            }
        },

        get currentColony() {
            return this.colonies.find((c) => c.id === this.colonyId) || null;
        },

        get colonyName() {
            const c = this.currentColony;
            return c ? c.name : "—";
        },

        // ---- resource / building status ----
        statusOf(r) {
            if (r.inHut >= r.needed) return "ok";
            if (r.deliverable) return "deliver";
            return "missing";
        },

        statusLabel(r) {
            const s = this.statusOf(r);
            return s === "ok" ? "Enough" : (s === "deliver" ? "Deliverable" : "Missing");
        },

        workOrder(b) {
            return this.snap.workOrders.find((w) => w.id === b.workOrderId) || null;
        },

        workOrderTargetLevel(b) {
            const wo = this.workOrder(b);
            return wo ? wo.targetLevel : 0;
        },

        actionOf(b) {
            const wo = this.workOrder(b);
            return wo ? wo.action : null;
        },

        buildingProgress(b) {
            const wo = this.workOrder(b);
            return wo ? Math.round((wo.progress || 0) * 100) : 0;
        },

        buildingCounts(b) {
            const c = { ok: 0, deliver: 0, missing: 0 };
            (b.required || []).forEach((r) => { c[this.statusOf(r)]++; });
            return c;
        },

        builtBy(b) {
            const wo = this.workOrder(b);
            return wo && wo.builderName ? wo.builderName : null;
        },

        // Building icon: MineColonies hut block for buildings, first material for decorations.
        buildingIconUrl(b) {
            if (b.kind === "decoration") {
                const r = (b.required || [])[0];
                return r ? this.textureUrl(r.itemKey) : this.textureUrl("minecolonies:blockhutbuilder");
            }
            const path = (b.type || "").split(":").pop().replace(/[^a-z0-9_]/g, "");
            return this.textureUrl("minecolonies:blockhut" + path);
        },

        matchesSearch(b, q) {
            if (!q) return true;
            q = q.toLowerCase();
            if ((b.name || "").toLowerCase().includes(q)) return true;
            return (b.required || []).some(
                (r) => (r.name || "").toLowerCase().includes(q) || (r.material || "").toLowerCase().includes(q)
            );
        },

        // ---- derived lists ----
        get filteredBuildings() {
            let list = (this.snap.buildings || []).slice();
            if (!this.showDecorations) list = list.filter((b) => b.kind !== "decoration");
            if (this.onlyInProgress) list = list.filter((b) => b.beingBuilt);
            const q = this.search.trim();
            if (q) list = list.filter((b) => this.matchesSearch(b, q));

            const key = this.sort;
            list.sort((a, b) => {
                if (key === "name") return (a.name || "").localeCompare(b.name || "");
                if (key === "progress") return this.buildingProgress(b) - this.buildingProgress(a);
                if (key === "level") return b.level - a.level;
                const ap = a.beingBuilt ? 1 : 0;
                const bp = b.beingBuilt ? 1 : 0;
                if (ap !== bp) return bp - ap;
                return this.buildingCounts(b).missing - this.buildingCounts(a).missing;
            });
            return list;
        },

        modalResources() {
            if (!this.modal) return [];
            let list = (this.modal.required || []).slice();
            const q = this.modalSearch.trim().toLowerCase();
            if (q) {
                list = list.filter(
                    (r) => (r.name || "").toLowerCase().includes(q) || (r.material || "").toLowerCase().includes(q)
                );
            }
            const rank = { missing: 0, deliver: 1, ok: 2 };
            list.sort((a, b) => (rank[this.statusOf(a)] - rank[this.statusOf(b)]) || (b.needed - a.needed));
            return list;
        },

        sortWarehouse(list, mode) {
            if (mode === "alpha") {
                return list.sort((a, b) => (a.name || "").localeCompare(b.name || ""));
            }
            return list.sort((a, b) => b.count - a.count);
        },

        get filteredWarehouse() {
            const wh = this.snap.warehouse || { stacks: [] };
            let list = (wh.stacks || []).slice();
            const q = this.whSearch.trim().toLowerCase();
            if (q) {
                list = list.filter(
                    (s) => (s.name || "").toLowerCase().includes(q) || (s.material || "").toLowerCase().includes(q)
                );
            }
            return this.sortWarehouse(list, this.whSort);
        },

        warehouseModalList() {
            const wh = this.snap.warehouse || { stacks: [] };
            let list = (wh.stacks || []).slice();
            const q = this.whModalSearch.trim().toLowerCase();
            if (q) {
                list = list.filter(
                    (s) => (s.name || "").toLowerCase().includes(q) || (s.material || "").toLowerCase().includes(q)
                );
            }
            return this.sortWarehouse(list, this.whModalSort);
        },

        builderInfo(builder) {
            const wo = this.snap.workOrders.find((w) => w.id === builder.assignedWorkOrderId);
            if (!wo) return { idle: true, pct: 0 };
            return {
                idle: false,
                action: wo.action,
                building: wo.buildingName || wo.buildingType || "?",
                current: wo.currentLevel,
                target: wo.targetLevel,
                pct: Math.round((wo.progress || 0) * 100),
            };
        },

        badgeClass(action) {
            if (!action) return "";
            const a = action.toUpperCase();
            if (a === "UPGRADE") return "b-upgrade";
            if (a === "BUILD") return "b-build";
            if (a === "REPAIR") return "b-repair";
            if (a === "REMOVE") return "b-remove";
            return "";
        },

        // ---- modals ----
        openBuilding(b) {
            this.modal = b;
            this.modalSearch = "";
        },
        closeModal() { this.modal = null; },

        openWarehouse() {
            this.warehouseOpen = true;
            this.whModalSearch = "";
        },
        closeWarehouse() { this.warehouseOpen = false; },

        // ---- live updates ----
        connectEvents() {
            const source = new EventSource("/events");
            source.addEventListener("open", () => { this.connection = "live"; });
            source.addEventListener("error", () => { this.connection = "down"; });
            source.addEventListener("update", (ev) => {
                let data;
                try { data = JSON.parse(ev.data); } catch (e) { return; }
                if (data.type === "colonies") {
                    this.loadColonies();
                } else if (data.type === "colony" && data.id === this.colonyId) {
                    this.loadColony(this.colonyId);
                }
            });
        },
    }));
});
