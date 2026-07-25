"use strict";

const state = {
    colonyId: null,
    colonies: [],
};

const els = {
    select: document.getElementById("colonySelect"),
    status: document.getElementById("status"),
    statusText: document.getElementById("statusText"),
    builders: document.getElementById("buildersBody"),
    buildings: document.getElementById("buildingsBody"),
    warehouse: document.getElementById("warehouseBody"),
};

function textureUrl(itemKey) {
    return "/textures/" + encodeURIComponent(itemKey) + ".png";
}

function setStatus(kind, text) {
    els.status.classList.remove("live", "down");
    if (kind) {
        els.status.classList.add(kind);
    }
    els.statusText.textContent = text;
}

async function fetchJson(url) {
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) {
        throw new Error(url + " -> " + res.status);
    }
    return res.json();
}

async function loadColonies() {
    try {
        const colonies = await fetchJson("/api/colonies");
        state.colonies = colonies;
        renderSelector();
        if (state.colonyId == null && colonies.length > 0) {
            const hash = parseInt(location.hash.replace("#", ""), 10);
            state.colonyId = colonies.some(c => c.id === hash) ? hash : colonies[0].id;
        }
        if (state.colonyId != null) {
            await loadColony(state.colonyId);
        } else {
            renderEmpty();
        }
    } catch (e) {
        console.error(e);
    }
}

function renderSelector() {
    const prev = state.colonyId;
    els.select.innerHTML = "";
    if (state.colonies.length === 0) {
        const opt = document.createElement("option");
        opt.textContent = "No colonies found";
        opt.value = "";
        els.select.appendChild(opt);
        return;
    }
    for (const c of state.colonies) {
        const opt = document.createElement("option");
        opt.value = String(c.id);
        opt.textContent = `${c.name} (#${c.id}) — ${c.buildingCount} buildings`;
        els.select.appendChild(opt);
    }
    if (prev != null) {
        els.select.value = String(prev);
    }
}

function renderEmpty() {
    els.builders.innerHTML = '<div class="empty">No colony selected.</div>';
    els.buildings.innerHTML = '<div class="empty">No data. Is MineColonies installed and a colony founded?</div>';
    els.warehouse.innerHTML = '<div class="empty">No warehouse data.</div>';
}

async function loadColony(id) {
    try {
        const snap = await fetchJson("/api/colony/" + id);
        renderBuilders(snap);
        renderBuildings(snap);
        renderWarehouse(snap);
    } catch (e) {
        console.error(e);
        renderEmpty();
    }
}

function renderBuilders(snap) {
    const woById = {};
    for (const wo of snap.workOrders || []) {
        woById[wo.id] = wo;
    }
    if (!snap.builders || snap.builders.length === 0) {
        els.builders.innerHTML = '<div class="empty">No builders assigned.</div>';
        return;
    }
    els.builders.innerHTML = "";
    for (const b of snap.builders) {
        const wo = woById[b.assignedWorkOrderId];
        const div = document.createElement("div");
        div.className = "builder";
        const task = wo
            ? `${wo.buildingName || wo.buildingType || "?"} → level ${wo.targetLevel} (${wo.action})`
            : "Idle";
        const pct = wo ? Math.round((wo.progress || 0) * 100) : 0;
        div.innerHTML =
            `<div class="name">${escapeHtml(b.name || "Builder")}</div>` +
            `<div class="task">${escapeHtml(task)}</div>` +
            `<div class="progress"><span style="width:${pct}%"></span></div>`;
        els.builders.appendChild(div);
    }
}

function renderBuildings(snap) {
    const buildings = (snap.buildings || []).slice().sort((a, b) => {
        return (b.beingBuilt ? 1 : 0) - (a.beingBuilt ? 1 : 0);
    });
    if (buildings.length === 0) {
        els.buildings.innerHTML = '<div class="empty">No buildings.</div>';
        return;
    }
    els.buildings.innerHTML = "";
    for (const bld of buildings) {
        const card = document.createElement("div");
        card.className = "card";
        const wo = (snap.workOrders || []).find(w => w.id === bld.workOrderId);
        const levelText = wo && wo.targetLevel
            ? `lvl ${bld.level} → ${wo.targetLevel}`
            : `lvl ${bld.level}`;
        const badge = bld.beingBuilt
            ? `<span class="badge">${escapeHtml(builtBy(wo))}</span>`
            : "";
        let rows = "";
        for (const r of bld.required || []) {
            const cls = rowClass(r);
            rows +=
                `<tr class="${cls}">` +
                `<td><span class="itemcell"><img class="icon" loading="lazy" src="${textureUrl(r.itemKey)}" alt="">${escapeHtml(r.name)}</span></td>` +
                `<td>${r.needed}</td>` +
                `<td>${r.inHut}</td>` +
                `<td>${r.inWarehouse}</td>` +
                `</tr>`;
        }
        const table = (bld.required && bld.required.length)
            ? `<table class="resources"><thead><tr><th>Item</th><th>Need</th><th>Hut</th><th>WH</th></tr></thead><tbody>${rows}</tbody></table>`
            : '<div class="empty">No pending requirements.</div>';
        card.innerHTML =
            `<div class="card-head"><span class="title">${escapeHtml(bld.name)}</span> ${badge}</div>` +
            `<div class="level">${escapeHtml(levelText)} · ${bld.x}, ${bld.y}, ${bld.z}</div>` +
            table;
        els.buildings.appendChild(card);
    }
}

function builtBy(wo) {
    if (wo && wo.builderName) {
        return "built by " + wo.builderName;
    }
    return "in progress";
}

function rowClass(r) {
    if (r.inHut >= r.needed) {
        return "row-ok";
    }
    if (r.deliverable) {
        return "row-deliver";
    }
    return "row-missing";
}

function renderWarehouse(snap) {
    const wh = snap.warehouse || { present: false, stacks: [] };
    if (!wh.present || !wh.stacks || wh.stacks.length === 0) {
        els.warehouse.innerHTML = '<div class="empty">No warehouse or it is empty.</div>';
        return;
    }
    const stacks = wh.stacks.slice().sort((a, b) => b.count - a.count);
    els.warehouse.innerHTML = "";
    for (const s of stacks) {
        const div = document.createElement("div");
        div.className = "wh-item";
        div.innerHTML =
            `<img class="icon" loading="lazy" src="${textureUrl(s.itemKey)}" alt="">` +
            `<span>${escapeHtml(s.name)}</span>` +
            `<span class="count">${s.count}</span>`;
        els.warehouse.appendChild(div);
    }
}

function escapeHtml(str) {
    return String(str == null ? "" : str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

els.select.addEventListener("change", () => {
    const id = parseInt(els.select.value, 10);
    if (!Number.isNaN(id)) {
        state.colonyId = id;
        location.hash = String(id);
        loadColony(id);
    }
});

function connectEvents() {
    const source = new EventSource("/events");
    source.addEventListener("open", () => setStatus("live", "live"));
    source.addEventListener("error", () => setStatus("down", "reconnecting…"));
    source.addEventListener("update", (ev) => {
        let data;
        try {
            data = JSON.parse(ev.data);
        } catch (e) {
            return;
        }
        if (data.type === "colonies") {
            loadColonies();
        } else if (data.type === "colony" && data.id === state.colonyId) {
            loadColony(state.colonyId);
        }
    });
}

loadColonies();
connectEvents();
