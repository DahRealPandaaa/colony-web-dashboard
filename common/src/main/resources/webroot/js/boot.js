/**
 * Page bootstrap.
 *
 * The dashboard markup is split into one partial per tab so each stays readable. Alpine only
 * scans the DOM once, at startup, so the partials must be in place *before* it loads — hence
 * this module fetches them, then appends the Alpine script itself.
 */
import { dashboard } from "./dashboard.js";

/** Replace every <div data-partial="x"> with the contents of /partials/x.html. */
async function loadPartials() {
    const slots = Array.from(document.querySelectorAll("[data-partial]"));
    await Promise.all(slots.map(async (slot) => {
        const name = slot.dataset.partial;
        try {
            const res = await fetch(`/partials/${name}.html`);
            if (!res.ok) throw new Error(`${name} -> ${res.status}`);
            slot.outerHTML = await res.text();
        } catch (e) {
            console.error("Could not load partial", name, e);
            slot.remove();
        }
    }));
}

function startAlpine() {
    document.addEventListener("alpine:init", () => Alpine.data("dashboard", dashboard));
    const script = document.createElement("script");
    script.src = "/vendor/alpine.min.js";
    document.head.appendChild(script);
}

await loadPartials();
startAlpine();
