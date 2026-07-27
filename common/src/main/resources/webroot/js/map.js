/**
 * Map tab: a pannable, zoomable top-down view of the colony.
 *
 * The terrain layer is a PNG the server draws from the world at one pixel per block, so world
 * coordinates and image pixels are the same thing — a marker at block X sits at pixel
 * `X - minX`. Everything (terrain and markers alike) therefore lives inside one transformed
 * layer, and panning is a single CSS transform rather than a position recalculated per marker.
 * Markers counter-scale by `1 / zoom` so they stay a readable size at any magnification.
 *
 * The server draws the map incrementally, a few chunks per scan, so this polls the (tiny)
 * metadata document while the tab is open and swaps the image in whenever its version changes.
 */

/** How often to ask the server how far the map has got, while the tab is open. */
const POLL_MS = 2500;

const MIN_ZOOM = 0.2;
const MAX_ZOOM = 8;

/** Marker size in screen pixels — kept constant by counter-scaling against the zoom. */
const BUILDING_MARKER = 26;

export function mapState() {
    return {
        map: null,
        mapTimer: null,

        zoom: 1,
        panX: 0,
        panZ: 0,
        mapFitted: false,

        dragging: false,
        dragFromX: 0,
        dragFromZ: 0,
        /** Set once a drag has actually moved, so releasing over a marker does not open it. */
        dragMoved: false,

        hoverX: null,
        hoverZ: null,

        showBuildings: true,
        showCitizens: true,
        showLabels: false,

        // ---- loading ----

        /**
         * Poll the map document while the tab is open.
         *
         * Colony data arrives over SSE, but the map fills in on the server's own schedule and
         * would otherwise only refresh when something else about the colony happened to change.
         */
        startMapPolling() {
            if (this.mapTimer) return;
            this.mapTimer = setInterval(() => {
                if (this.signedIn && this.tab === "map" && this.colonyId != null) {
                    this.loadSection("map");
                }
            }, POLL_MS);
        },

        /** Called by loadSection once the payload is in. */
        applyMap(info) {
            const previous = this.map;
            this.map = info;
            // Re-fit whenever the map first appears or its footprint changes under us.
            if (!previous || previous.minX !== info.minX || previous.minZ !== info.minZ
                || previous.width !== info.width || previous.height !== info.height) {
                this.mapFitted = false;
            }
        },

        /** Dropped when the colony changes, so the next one starts framed on its own centre. */
        resetMap() {
            this.map = null;
            this.mapFitted = false;
            this.zoom = 1;
            this.panX = 0;
            this.panZ = 0;
        },

        get mapReady() {
            return !!(this.map && this.map.available && this.map.ready);
        },

        get mappedPct() {
            if (!this.map || !this.map.chunksTotal) return 0;
            return Math.min(100, Math.round((this.map.chunksMapped / this.map.chunksTotal) * 100));
        },

        /** The terrain image, versioned so a redraw is never served from the browser cache. */
        get mapImageUrl() {
            if (!this.mapReady) return null;
            return `/map/${this.colonyId}.png?v=${this.map.version}`;
        },

        // ---- view transform ----

        get mapTransform() {
            return `transform: translate(${this.panX}px, ${this.panZ}px) scale(${this.zoom});`;
        },

        /** Markers are placed in block space and un-scaled, so they keep their size. */
        markerStyle(x, z, size) {
            if (!this.map) return "display:none";
            const left = x - this.map.minX;
            const top = z - this.map.minZ;
            return `left:${left}px; top:${top}px; width:${size}px; height:${size}px;`
                + ` margin-left:${-size / 2}px; margin-top:${-size / 2}px;`
                + ` transform: scale(${1 / this.zoom});`;
        },

        buildingMarkerStyle(building) {
            return this.markerStyle(building.x, building.z, BUILDING_MARKER);
        },

        citizenMarkerStyle(citizen) {
            return this.markerStyle(citizen.x, citizen.z, 12);
        },

        /** Frame the whole map in the stage the first time it is shown. */
        fitMap(stage) {
            if (!stage || !this.map || !this.map.width) return;
            const box = stage.getBoundingClientRect();
            if (!box.width || !box.height) return;
            const fit = Math.min(box.width / this.map.width, box.height / this.map.height);
            this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fit * 0.94));
            this.panX = (box.width - this.map.width * this.zoom) / 2;
            this.panZ = (box.height - this.map.height * this.zoom) / 2;
            this.mapFitted = true;
        },

        /** Re-fit on first paint and whenever the footprint changed. */
        maybeFitMap(stage) {
            if (!this.mapFitted) this.fitMap(stage);
        },

        /** Put the colony centre in the middle of the stage at the current zoom. */
        centreMap(stage) {
            if (!stage || !this.map) return;
            const box = stage.getBoundingClientRect();
            this.panX = box.width / 2 - (this.map.centerX - this.map.minX) * this.zoom;
            this.panZ = box.height / 2 - (this.map.centerZ - this.map.minZ) * this.zoom;
        },

        /** Zoom about a point in stage coordinates, so what is under it stays put. */
        zoomAt(factor, stageX, stageZ) {
            const next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoom * factor));
            if (next === this.zoom) return;
            const ratio = next / this.zoom;
            this.panX = stageX - (stageX - this.panX) * ratio;
            this.panZ = stageZ - (stageZ - this.panZ) * ratio;
            this.zoom = next;
        },

        zoomBy(factor, stage) {
            const box = stage.getBoundingClientRect();
            this.zoomAt(factor, box.width / 2, box.height / 2);
        },

        onMapWheel(event, stage) {
            const box = stage.getBoundingClientRect();
            this.zoomAt(event.deltaY < 0 ? 1.15 : 1 / 1.15,
                event.clientX - box.left, event.clientY - box.top);
        },

        // ---- pointer handling ----

        onMapDown(event) {
            this.dragging = true;
            this.dragMoved = false;
            this.dragFromX = event.clientX - this.panX;
            this.dragFromZ = event.clientY - this.panZ;
        },

        onMapMove(event, stage) {
            if (this.dragging) {
                const nextX = event.clientX - this.dragFromX;
                const nextZ = event.clientY - this.dragFromZ;
                if (Math.abs(nextX - this.panX) + Math.abs(nextZ - this.panZ) > 3) {
                    this.dragMoved = true;
                }
                this.panX = nextX;
                this.panZ = nextZ;
            }
            if (!this.map) return;
            const box = stage.getBoundingClientRect();
            this.hoverX = Math.floor((event.clientX - box.left - this.panX) / this.zoom) + this.map.minX;
            this.hoverZ = Math.floor((event.clientY - box.top - this.panZ) / this.zoom) + this.map.minZ;
        },

        onMapUp() {
            this.dragging = false;
        },

        onMapLeave() {
            this.dragging = false;
            this.hoverX = null;
            this.hoverZ = null;
        },

        // ---- markers ----

        /** Buildings, with the ones being worked on drawn last so they sit on top. */
        get mapBuildings() {
            if (!this.showBuildings) return [];
            return (this.snap.buildings || []).slice()
                .sort((a, b) => (a.beingBuilt ? 1 : 0) - (b.beingBuilt ? 1 : 0));
        },

        /** Only citizens the server could actually place — an unloaded citizen has no position. */
        get mapCitizens() {
            if (!this.showCitizens) return [];
            return (this.citizens || []).filter((c) =>
                c.spawned && Number.isFinite(c.x) && Number.isFinite(c.z)
            );
        },

        /** Dot colour: guards, workers, children and the unemployed read differently. */
        citizenDotClass(citizen) {
            if (!citizen.spawned) return "asleep";
            if (citizen.child) return "child";
            return citizen.jobType ? "worker" : "idle";
        },

        citizenTitle(citizen) {
            return `${citizen.name} — ${citizen.job || "Unemployed"} (${citizen.x}, ${citizen.z})`;
        },

        buildingTitle(building) {
            return `${building.name} · level ${building.level} (${building.x}, ${building.z})`;
        },
    };
}
