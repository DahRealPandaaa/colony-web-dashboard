/**
 * Pure presentation helpers — no state, no fetching. Kept separate so the tab modules stay
 * about their own data.
 */

/** Percentage of value over max, clamped to 0-100 and rounded. */
export function pct(value, max) {
    if (!max) return 0;
    return Math.max(0, Math.min(100, Math.round((value / max) * 100)));
}

/** Thousands-separated integer, tolerant of undefined. */
export function num(value) {
    return (value || 0).toLocaleString();
}

/** Work-order action badge colour. */
export function badgeClass(action) {
    switch ((action || "").toUpperCase()) {
        case "UPGRADE": return "b-upgrade";
        case "BUILD": return "b-build";
        case "REPAIR": return "b-repair";
        case "REMOVE": return "b-remove";
        default: return "";
    }
}

/** Whether a required resource is satisfied, deliverable from the warehouse, or missing. */
export function statusOf(resource) {
    if (resource.inHut >= resource.needed) return "ok";
    return resource.deliverable ? "deliver" : "missing";
}

export function statusLabel(resource) {
    switch (statusOf(resource)) {
        case "ok": return "Enough";
        case "deliver": return "Deliverable";
        default: return "Missing";
    }
}

export function stateClass(state) {
    if (state === "COMPLETED") return "completed";
    if (state === "IN_PROGRESS") return "in-progress";
    return "not-started";
}

export function stateLabel(state) {
    if (state === "COMPLETED") return "Done";
    if (state === "IN_PROGRESS") return "Researching";
    return "Not started";
}

/** Escape text destined for innerHTML (the colony map builds SVG markup as a string). */
export function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"]/g, (ch) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;",
    }[ch]));
}

/** Case-insensitive "does any of these fields contain the query" test. */
export function matches(query, ...fields) {
    if (!query) return true;
    const needle = query.toLowerCase();
    return fields.some((field) => (field || "").toLowerCase().includes(needle));
}
