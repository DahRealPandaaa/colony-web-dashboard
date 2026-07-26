import { escapeHtml } from "./format.js";

/**
 * Map tab: a top-down plan of the colony.
 *
 * Markers are built as an SVG string and injected with x-html because Alpine's x-for needs a
 * real <template>, which the HTML parser will not create inside <svg>. Hover labels therefore
 * use native <title> elements rather than JS listeners.
 */
export function mapState() {
    return {
        /** Padding around the colony bounds, in blocks. */
        mapPadding: 20,

        get mapMarkers() {
            return this.buildingMarkers() + this.citizenMarkers();
        },

        buildingMarkers() {
            return this.snap.buildings.map((b) => {
                const fill = b.kind === "decoration"
                    ? "#fbbf24"
                    : (b.beingBuilt ? "#38bdf8" : "#60a5fa");
                const label = `${b.name} · level ${b.level} · ${b.x}, ${b.z}`;
                return `<rect x="${b.x - 3}" y="${b.z - 3}" width="6" height="6" rx="1.5"`
                    + ` fill="${fill}" opacity="${b.beingBuilt ? 1 : 0.75}">`
                    + `<title>${escapeHtml(label)}</title></rect>`;
            }).join("");
        },

        citizenMarkers() {
            return this.citizens.map((c) =>
                `<circle cx="${c.x}" cy="${c.z}" r="1.8" fill="#34d399"`
                + ` opacity="${c.spawned ? 0.95 : 0.3}">`
                + `<title>${escapeHtml(`${c.name} · ${c.job}`)}</title></circle>`
            ).join("");
        },

        /** Fit the viewBox to everything on the map, north up. */
        get mapViewBox() {
            const xs = [];
            const zs = [];
            this.snap.buildings.forEach((b) => { xs.push(b.x); zs.push(b.z); });
            this.citizens.forEach((c) => {
                if (c.x || c.z) { xs.push(c.x); zs.push(c.z); }
            });
            if (!xs.length) return "0 0 100 100";

            const pad = this.mapPadding;
            const minX = Math.min(...xs) - pad;
            const minZ = Math.min(...zs) - pad;
            const width = Math.max(48, Math.max(...xs) + pad - minX);
            const height = Math.max(48, Math.max(...zs) + pad - minZ);
            return `${minX} ${minZ} ${width} ${height}`;
        },
    };
}
