import { Unauthorized, api, textureUrl } from "./api.js";
import * as fmt from "./format.js";
import { authState } from "./auth.js";
import { iconsState } from "./icons.js";
import { overviewState } from "./overview.js";
import { mapState } from "./map.js";
import { buildingsState } from "./buildings.js";
import { citizensState } from "./citizens.js";
import { researchState } from "./research.js";
import { combatState } from "./combat.js";
import { warehouseState } from "./warehouse.js";

/** Zeroed stats so the overview renders before the first snapshot arrives. */
function emptyStats() {
    return {
        citizens: 0, maxCitizens: 0, children: 0, unemployed: 0,
        happiness: 0, saturation: 0,
        buildings: 0, decorations: 0, workOrders: 0, builders: 0, guards: 0,
        warehouseTypes: 0, warehouseItems: 0,
        researchCompleted: 0, researchInProgress: 0,
        raided: false, nightsSinceRaid: 0,
    };
}

function emptySnapshot() {
    return {
        builders: [], workOrders: [], buildings: [],
        warehouse: { present: false, stacks: [] },
        stats: emptyStats(),
        workOrdersById: {},
    };
}

/** Sidebar navigation. `icon` is injected into an <svg> via x-html. */
const TABS = [
    {
        id: "overview", label: "Overview", title: "Colony overview",
        subtitle: "Everything at a glance",
        icon: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/>'
            + '<rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
    },
    {
        id: "map", label: "Map", title: "Colony map",
        subtitle: "Where everything stands, and who is where",
        icon: '<path d="M9 3.5 3.5 6v14.5L9 18l6 2.5 5.5-2.5V3.5L15 6z"/><path d="M9 3.5V18"/><path d="M15 6v14.5"/>',
    },
    {
        id: "buildings", label: "Buildings", title: "Buildings & decorations",
        subtitle: "What is built and what each site still needs",
        icon: '<path d="M3 21h18"/><path d="M5 21V8l7-5 7 5v13"/><path d="M9 21v-6h6v6"/>',
    },
    {
        id: "citizens", label: "Citizens", title: "Citizens",
        subtitle: "Skills, mood and what everyone is carrying",
        icon: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/>'
            + '<path d="M17 11a3 3 0 1 0-1.6-5.5"/><path d="M18.5 20c0-2.2-.8-4.1-2.2-5.3"/>',
    },
    {
        id: "research", label: "Research", title: "University research",
        subtitle: "Finished, running and still locked",
        icon: '<path d="M9 3h6"/><path d="M10 3v6.5L4.6 18A2 2 0 0 0 6.3 21h11.4a2 2 0 0 0 1.7-3L14 9.5V3"/>',
    },
    {
        id: "combat", label: "Combat", title: "Colony defence",
        subtitle: "Raid pressure, guards and guard posts",
        icon: '<path d="M12 3l7.5 3v5.7c0 4.5-3.2 8.4-7.5 9.8-4.3-1.4-7.5-5.3-7.5-9.8V6z"/>',
    },
    {
        id: "warehouse", label: "Warehouse", title: "Warehouse stock",
        subtitle: "Everything stored across the colony",
        icon: '<path d="M3 8l9-5 9 5v8l-9 5-9-5z"/><path d="M3 8l9 5 9-5"/><path d="M12 13v8"/>',
    },
];

/** Which lazily-loaded section each tab needs. */
const TAB_SECTIONS = {
    // The map plots the citizen roster, so it needs that section as well as its own.
    map: ["map", "citizens"],
    citizens: ["citizens"],
    research: ["research"],
    combat: ["combat"],
};

/** Merge mixins while preserving getters (a plain spread would evaluate them once). */
function compose(...parts) {
    const merged = {};
    for (const part of parts) {
        Object.defineProperties(merged, Object.getOwnPropertyDescriptors(part));
    }
    return merged;
}

/** Core state: colony selection, tab routing, loading and the live event stream. */
function coreState() {
    return {
        tabs: TABS,
        tab: "overview",

        colonies: [],
        colonyId: null,
        snap: emptySnapshot(),
        citizens: [],
        research: null,
        combat: null,
        loaded: { citizens: false, research: false, combat: false, map: false },

        connection: "connecting",
        events: null,

        // Presentation helpers, exposed to the templates.
        pct: fmt.pct,
        num: fmt.num,
        stacks: fmt.stacks,
        badgeClass: fmt.badgeClass,
        statusOf: fmt.statusOf,
        statusLabel: fmt.statusLabel,
        stateClass: fmt.stateClass,
        stateLabel: fmt.stateLabel,
        textureUrl,

        async init() {
            this.readHash();
            if (await this.loadSession()) {
                await this.startDashboard();
            }
        },

        /** Run a request, bouncing to the sign-in screen if the session has gone. */
        async guarded(work) {
            try {
                return await work();
            } catch (e) {
                if (e instanceof Unauthorized) {
                    this.onUnauthorized();
                } else {
                    console.error(e);
                }
                return null;
            }
        },

        // ---- colony + tab routing ----

        get colony() {
            return this.colonies.find((c) => c.id === this.colonyId) || null;
        },

        get currentTab() {
            return this.tabs.find((t) => t.id === this.tab) || this.tabs[0];
        },

        get stats() {
            return this.snap.stats || emptyStats();
        },

        setTab(id) {
            this.tab = id;
            this.writeHash();
            this.ensureSections();
        },

        tabCount(id) {
            switch (id) {
                case "buildings": return this.snap.buildings.length;
                case "warehouse": return this.snap.warehouse.stacks.length;
                case "citizens": return this.loaded.citizens ? this.citizens.length : this.stats.citizens;
                case "research": return this.research ? this.research.completed : null;
                case "combat": return this.combat ? this.combat.guardCount : this.stats.guards;
                default: return null;
            }
        },

        readHash() {
            const [rawId, rawTab] = location.hash.replace("#", "").split("/");
            const id = parseInt(rawId, 10);
            if (!isNaN(id)) this.colonyId = id;
            if (rawTab && this.tabs.some((t) => t.id === rawTab)) this.tab = rawTab;
        },

        writeHash() {
            if (this.colonyId != null) location.hash = `${this.colonyId}/${this.tab}`;
        },

        // ---- loading ----

        /** Called after sign-in (or on load with a live session). */
        async startDashboard() {
            await this.loadColonies();
            this.connectEvents();
            this.startMapPolling();
        },

        async loadColonies() {
            await this.guarded(async () => {
                const colonies = await api.colonies();
                this.colonies = colonies;
                if (!colonies.length) {
                    this.colonyId = null;
                    return;
                }
                if (this.colonyId == null || !colonies.some((c) => c.id === this.colonyId)) {
                    this.colonyId = colonies[0].id;
                }
                this.writeHash();
                await this.refresh();
            });
        },

        async selectColony(id) {
            this.colonyId = id;
            this.building = null;
            this.citizen = null;
            this.citizens = [];
            this.research = null;
            this.combat = null;
            this.resetMap();
            this.loaded = { citizens: false, research: false, combat: false, map: false };
            this.writeHash();
            await this.refresh();
        },

        /** Reload the snapshot plus whatever the visible tab needs. */
        async refresh() {
            await this.loadSnapshot();
            await this.ensureSections(true);
            if (this.citizen) await this.loadCitizenDetail();
        },

        async loadSnapshot() {
            if (this.colonyId == null) return;
            await this.guarded(async () => {
                const snapshot = await api.snapshot(this.colonyId);
                // Index work orders once per refresh, so rendering never scans the list.
                snapshot.workOrdersById = {};
                (snapshot.workOrders || []).forEach((w) => { snapshot.workOrdersById[w.id] = w; });
                this.snap = snapshot;
                this.refreshOpenBuilding(snapshot);
            });
        },

        /**
         * Load the sections the current tab needs. Already-loaded sections are only re-fetched
         * when `force` is set — i.e. the colony data actually changed.
         */
        async ensureSections(force) {
            for (const section of (TAB_SECTIONS[this.tab] || [])) {
                if (this.loaded[section] && !force) continue;
                await this.loadSection(section);
            }
        },

        async loadSection(section) {
            if (this.colonyId == null) return;
            await this.guarded(async () => {
                const data = await api.section(this.colonyId, section);
                if (section === "citizens") this.citizens = data;
                if (section === "research") this.research = data;
                if (section === "combat") this.combat = data;
                if (section === "map") this.applyMap(data);
                this.loaded[section] = true;
            });
        },

        // ---- live updates ----

        connectEvents() {
            this.closeEvents();
            const source = new EventSource("/events");
            this.events = source;
            source.addEventListener("open", () => { this.connection = "live"; });
            source.addEventListener("error", () => { this.connection = "down"; });
            source.addEventListener("update", (event) => {
                let payload;
                try {
                    payload = JSON.parse(event.data);
                } catch (e) {
                    return;
                }
                if (payload.type === "colonies") {
                    this.loadColonies();
                } else if (payload.type === "colony" && payload.id === this.colonyId) {
                    this.refresh();
                }
            });
        },

        closeEvents() {
            if (this.events) {
                this.events.close();
                this.events = null;
            }
            this.connection = "connecting";
        },
    };
}

/** The Alpine component backing the whole page. */
export function dashboard() {
    return compose(
        coreState(),
        authState(),
        iconsState(),
        overviewState(),
        mapState(),
        buildingsState(),
        citizensState(),
        researchState(),
        combatState(),
        warehouseState(),
    );
}
