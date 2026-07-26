/**
 * Every call to the ColonyWeb server lives here.
 *
 * All colony endpoints answer 401 once a session expires, so a single Unauthorized error type
 * lets callers bounce the viewer back to the sign-in screen without inspecting status codes.
 */

/** Thrown when the server says the browser has no valid session. */
export class Unauthorized extends Error {
    constructor() {
        super("Not signed in");
        this.name = "Unauthorized";
    }
}

async function getJson(url) {
    const res = await fetch(url, { cache: "no-store" });
    if (res.status === 401) throw new Unauthorized();
    if (!res.ok) throw new Error(`${url} -> ${res.status}`);
    return res.json();
}

/** POST helper that reports failures as data rather than throwing — the sign-in form
 *  needs to show the server's message. */
async function postJson(url, body) {
    const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {}),
    });
    let data = {};
    try {
        data = await res.json();
    } catch (e) {
        // A non-JSON error body is not worth surfacing; ok/status carry the outcome.
    }
    return { ok: res.ok, status: res.status, data };
}

export const api = {
    session: () => getJson("/auth/me"),
    login: (code) => postJson("/auth/login", { code }),
    logout: () => postJson("/auth/logout"),

    colonies: () => getJson("/api/colonies"),
    snapshot: (colonyId) => getJson(`/api/colony/${colonyId}`),
    section: (colonyId, section) => getJson(`/api/colony/${colonyId}/${section}`),
    citizen: (colonyId, citizenId) => getJson(`/api/colony/${colonyId}/citizen/${citizenId}`),
};

/** PNG icon for an item/block texture key (the "#" in Domum variants must be encoded). */
export function textureUrl(key) {
    return "/textures/" + encodeURIComponent(key) + ".png";
}
