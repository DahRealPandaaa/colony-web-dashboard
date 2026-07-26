# ColonyWeb — Project Plan & Specification

> The design record for the mod: the goal, the decisions behind the architecture, and the
> task checklist. `README.md` documents the mod **as it is today** (structure, endpoints,
> config, commands) — when the two disagree, the README wins.

---

## 1. Goal

A **server-side-only** Minecraft Forge mod (MC **1.20.1**, Forge **47.4.0**) that expands on
**MineColonies** by exposing a **live web dashboard**. The website (served by the Minecraft
server itself) shows, per colony:

- Every builder's hut / building and **which builder is upgrading which building**.
- The **resources each build/upgrade needs** (resource-scroll parity).
- **What the building hut already has** in its inventory.
- **What the colony can provide from the warehouse(s)**.
- **Item & block textures** for every stack — including **Domum Ornamentum** dynamically
  textured blocks.
- **Live updates via SSE** (Server-Sent Events) — no manual refresh.
- A **colony selector** on the site so you can switch between colonies.

The mod does **not** need to be installed on the client — only on the server.

---

## 2. Key Constraints & Decisions

| Topic | Decision |
|-------|----------|
| Side | Server only (`side = "SERVER"` in `mods.toml`). No client code path. |
| HTTP host | Embedded HTTP server started on `ServerStartingEvent`, stopped on `ServerStoppingEvent`. |
| Live updates | **SSE** stream (`/events`); server re-scans on an interval and pushes deltas. |
| MineColonies access | **Reflection-based soft dependency** — the mod compiles and loads without MineColonies present, and binds to its API at runtime. |
| Textures | Generated **server-side** as PNG and served by the web server. A dedicated server has no vanilla client textures, so we **download & cache the vanilla client jar assets** for the running MC version. Modded textures (MineColonies, Domum Ornamentum) are read from the installed mod jars on the classpath. |
| Domum Ornamentum | Resolve **every** material component from stack NBT. Each becomes a tooltip line ("Supported by: Oak Planks", "Main Material: Brick Extra"), and the components drive the icon render. |
| Block icons | Ordinary items use the flat inventory-face texture. **Domum Ornamentum blocks are rendered in 3D** (`texture/IsometricRenderer`) from their own model geometry with the vanilla GUI transform, so the icon shows the actual shape in the actual materials. Falls back to the flat material texture when a model cannot be parsed. |
| JSON | Use `com.google.gson.Gson` (bundled with Minecraft). |

---

## 3. Tech Stack

- Java 17, Forge 47.4.0, MC 1.20.1.
- `com.sun.net.httpserver.HttpServer` (JDK built-in) for HTTP + SSE. No extra HTTP lib.
- Gson (already on the MC classpath) for JSON serialization.
- Vanilla asset acquisition via Mojang version manifest + client jar extraction.
- Reflection for all MineColonies / Domum Ornamentum access.
- Static web front-end (vanilla HTML/CSS/JS) bundled in `src/main/resources/webroot/`.

---

## 4. Dependencies / Versions

- Maven repo (already added to `build.gradle`): `https://ldtteam.jfrog.io/ldtteam/modding/`.
- MineColonies (MC 1.20.1 line = `1.1.x`) and Domum Ornamentum (`1.0.x`).
  - The exact latest version can be pinned in `gradle.properties`
    (`minecolonies_version`, `domum_version`) **only if** you uncomment the `compileOnly`
    deps in `build.gradle`. Runtime integration is reflection-based, so this is optional.
  - To always resolve newest at build time, dynamic versions `1.1.+` / `1.0.+` work.
- `mods.toml` declares both as **optional** dependencies (`mandatory = false`, `AFTER`).

**Already done in the repo:**
- `build.gradle`: LDTTeam maven repo added; commented `compileOnly fg.deobf(...)` deps.
- `gradle.properties`: mod metadata + `minecolonies_version` / `domum_version` placeholders.
- `mods.toml`: `side = "SERVER"`, optional deps on `minecolonies` and `domum_ornamentum`.
- `Config.java`: rewritten with `httpPort`, `bindAddress`, `refreshIntervalSeconds`,
  `autoDownloadVanillaAssets`, `publicHost`.

---

## 5. Package / File Layout

Base package: `DahRealPanda.plugins.colonyweb` (mod id `colonyweb`).

The full, current tree is in **README.md → Project Structure**. The shape it settled into:

```
colonyweb/
  ColonyWeb.java        @Mod entry — server lifecycle + command wiring
  Config.java           port, bind, refresh, assets, host, auth settings
  ColonyWebService.java wires web server, auth, provider and scheduler together

  auth/       pairing codes, sessions, grants, persistence, session cookie
  web/        HttpServer bootstrap, SSE broadcaster, JSON helpers
    handlers/ auth, request auth, api, events, textures, static
  colony/     one scanner per concern (lookup, buildings, work orders, warehouse,
              citizens, research, combat), a StatsBuilder, the reflection layer,
              the cache, and model/ DTOs
  service/    refresh scheduler + the change hash driving SSE
  texture/    icon pipeline: model resolution, isometric rasterizer, DO materials, caches
  util/       text/component helpers
  command/    the Brigadier tree + the access command bodies

src/main/resources/webroot/
  index.html            app shell (sidebar, topbar, partial slots)
  partials/*.html       one file per tab, plus the two modals and the sign-in screen
  js/*.js               ES modules: boot, dashboard, api, format, one per tab
  style.css             generated by Tailwind
  vendor/alpine.min.js
```

Rules that keep it that way:

- **One concern per file.** A scanner reads one part of the colony; a JS module backs one tab.
- `ColonyDataProvider` orchestrates a scan and owns no scanning logic itself.
- Everything MineColonies-shaped goes through `MineColoniesReflect` and fails soft.
- The front-end mirrors the same split: `partials/<tab>.html` ↔ `js/<tab>.js`.

---

## 6. Data Model (JSON shapes served by the API)

`GET /api/colonies` →
```json
[
  { "id": 1, "name": "Springfield", "dimension": "minecraft:overworld",
    "owner": "Steve", "x": 120, "y": 64, "z": -340,
    "buildingCount": 14, "builderCount": 3, "activeWorkOrders": 2 }
]
```

`GET /api/colony/{id}` →
```json
{
  "id": 1, "name": "Springfield", "dimension": "minecraft:overworld", "owner": "Steve",
  "builders": [
    { "id": 42, "name": "Bob", "hutX": 118, "hutY": 64, "hutZ": -338,
      "assignedWorkOrderId": 7 }
  ],
  "workOrders": [
    { "id": 7, "buildingName": "Fisher's Hut", "buildingType": "fisherman",
      "x": 130, "y": 64, "z": -350, "currentLevel": 1, "targetLevel": 2,
      "action": "UPGRADE", "builderId": 42, "builderName": "Bob",
      "progress": 0.35 }
  ],
  "buildings": [
    { "id": 130640350, "name": "Fisher's Hut", "type": "fisherman", "level": 1,
      "x": 130, "y": 64, "z": -350,
      "beingBuilt": true, "workOrderId": 7,
      "required": [
        { "itemKey": "minecraft:oak_planks", "name": "Oak Planks",
          "needed": 64, "inHut": 12, "inWarehouse": 200, "deliverable": true }
      ]
    }
  ],
  "warehouse": {
    "present": true,
    "stacks": [ { "itemKey": "minecraft:oak_planks", "name": "Oak Planks", "count": 200 } ]
  }
}
```

- `itemKey` is the texture key: `namespace:path` plus, when NBT-relevant (Domum
  Ornamentum), a suffix `#<8charHash>` so distinct textured blocks map to distinct PNGs.
- `/textures/{key}.png` returns the icon for a given `itemKey` (URL-encode the `#`).

---

## 7. HTTP Endpoints

The full table (including the `/auth/*` routes and the per-section colony endpoints) is in
**README.md → HTTP API & Endpoints**. The core shape:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | `index.html` |
| GET | `/style.css`, `/js/*`, `/partials/*` | static assets |
| GET/POST | `/auth/me`, `/auth/login`, `/auth/logout` | pairing-code sign-in |
| GET | `/api/colonies` | colonies the signed-in player may see |
| GET | `/api/colony/{id}` | full snapshot for one colony |
| GET | `/api/colony/{id}/{citizens\|research\|combat}` | the heavier sections, fetched per tab |
| GET | `/events` | **SSE** stream; emits `colonies` and `colony:{id}` change events |
| GET | `/textures/{key}.png` | PNG icon for an item/block/DO stack |

SSE event format:
```
event: update
data: {"type":"colony","id":1}

```
Client reacts by re-fetching the affected endpoint (simple + robust). Optionally send full
payloads later as an optimization.

---

## 8. MineColonies Reflection Integration (runtime binding)

All access goes through `MineColoniesReflect` which lazily resolves and caches
`Class`/`Method`/`Field` handles, and returns `Optional`/empty on any failure so the mod
never crashes if the API shifts. Target concepts (resolve by class name, fall back
gracefully):

- Colony manager: `com.minecolonies.api.colony.IColonyManager#getInstance()` →
  `getColonies(Level)` / `getAllColonies()` / `getColonyByDimension(...)`.
- Colony: `IColony#getID()`, `getName()`, `getDimension()`, `getBuildingManager()`,
  `getWorkManager()`, permissions/owner.
- Buildings: `IBuildingManager#getBuildings()`; per building `getID()`/`getPosition()`,
  `getBuildingType()` (registry name), `getBuildingLevel()`, tile entity `IItemHandler`
  for hut inventory.
- Warehouse: buildings of type `warehouse`; read their `IItemHandler`(s) / tile entity
  inventory to aggregate available stock.
- Work orders: `IWorkManager#getWorkOrders()` → `WorkOrderBuildBuilding` /
  `IWorkOrder`: `getID()`, `getBuilding()` (position), `getStructureName()`,
  `getTargetLevel()`, `getCurrentLevel()`, `getClaimedBy()` (builder pos), and the
  work-order type (BUILD / UPGRADE / REPAIR / REMOVE).
- Builder requirements: the builder building's required-resources map
  (`BuildingBuilderResource` list: item, amount needed, amount available). This is the
  same data the **resource scroll** shows.
- Builder ↔ building link: map each work order's `claimedBy` builder hut position to a
  `BuilderInfo`, and set `beingBuilt`/`workOrderId` on the matching `BuildingInfo`.

> Implementation note: keep every reflective call in one class with helper methods like
> `invoke(obj, "method")`, `field(obj, "name")`, `resolve("fqcn")`. Log once at startup
> whether MineColonies was detected.

---

## 9. Texture Pipeline (server-side PNG generation)

Goal: given an `itemKey` (and optionally the originating `ItemStack`/NBT), return PNG bytes.

1. **Resolve registry item** from `namespace:path` via `ForgeRegistries.ITEMS`.
2. **Read the item model** JSON from the classpath: `assets/<ns>/models/item/<path>.json`.
   - If `parent` is `item/generated` / `item/handheld`: use `textures.layer0`.
   - If it points to a block model: follow `parent` chain, pick a representative face
     texture (`textures.up`/`side`/`all` / first texture found).
   - Fallback: try `assets/<ns>/textures/item/<path>.png` then `.../block/<path>.png`.
3. **Load the texture PNG bytes**:
   - Modded namespaces (`minecolonies`, `domum_ornamentum`, others): read from the mod
     jar via `ModList`/classloader resource `assets/<ns>/textures/....png`.
   - `minecraft` namespace: read from the **cached vanilla assets** produced by
     `VanillaAssetProvider`.
4. **Domum Ornamentum**: if the stack has DO texture/material NBT, resolve the primary
   material block (e.g. `minecraft:oak_planks`) and use that block's texture PNG instead
   of the plain DO block texture. Include the material in the `itemKey` hash so each
   variant caches separately.
5. **Cache** results in `PngCache` (memory + `run/colonyweb/textures/`).
6. If nothing resolves, return a generated **placeholder** PNG (e.g. magenta/black
   checker) so the UI still lays out.

### VanillaAssetProvider
- On first server start (if `autoDownloadVanillaAssets`):
  1. Fetch Mojang version manifest:
     `https://launchermeta.mojang.com/mc/game/version_manifest_v2.json`.
  2. Find entry for `1.20.1` → fetch its version JSON → `downloads.client.url`.
  3. Download `client.jar` to `run/colonyweb/`.
  4. Lazily extract `assets/minecraft/textures/**` entries on demand (or all up front).
- Cache so it only downloads once. Respect a config flag and fail soft (log + placeholder)
  if offline.

---

## 10. Web UI (`webroot/`)

- **No bundler.** Alpine.js is vendored, the modules are plain ES modules, and the stylesheet
  is generated by the standalone Tailwind binary. Nothing needs `npm` to run.
- **`index.html`** is only the shell: sidebar nav, topbar (colony selector, live indicator,
  refresh, sign out) and one `<div data-partial="x">` per tab and modal.
- **`js/boot.js`** fetches each `/partials/x.html`, replaces the placeholder, and *only then*
  appends the Alpine script — Alpine walks the DOM once at startup, so the markup has to be in
  place first.
- **`js/dashboard.js`** composes the per-tab mixins with `Object.defineProperties` (a spread
  would evaluate their getters once and freeze the value), and owns colony selection, tab
  routing via the URL hash, lazy section loading and the `EventSource('/events')` subscription.
- **`js/<tab>.js`** holds one tab's filters, derived lists and modal state. `format.js` is pure
  presentation helpers; `api.js` is every server call plus the `Unauthorized` error that bounces
  the viewer back to the sign-in screen.
- **The design system lives in `tailwind.input.css`'s component layer** (`.panel`, `.card`,
  `.tile`, `.meter`, `.mc-tip`, …) so the partials read as structure, not as utility soup.
- Status colours are consistent everywhere: green = satisfied, amber = deliverable from the
  warehouse, red = missing.
- Item lists (warehouse, build requirements, citizen inventories) are rendered as **Minecraft
  tooltips**, material lines and all.

**Gotchas worth remembering:**

- Inside `<svg>`, `<template x-for>` is parsed as an SVG element, not a real template, and
  `:viewBox` is lowercased to `:viewbox`. The map tab therefore builds marker markup as a string
  (`x-html`) and sets `viewBox` imperatively via `x-effect`.
- Key `x-for` over item lists **by index**, not by `itemKey` — two stacks can collapse to the
  same key, and duplicate keys break Alpine's reconciliation outright.

---

## 11. Config (`Config.java`)

| Key | Default | Meaning |
|-----|---------|---------|
| `httpPort` | 8723 | Web server port |
| `bindAddress` | `0.0.0.0` | Bind interface (`127.0.0.1` for local only) |
| `refreshIntervalSeconds` | 3 | Re-scan + SSE push cadence |
| `autoDownloadVanillaAssets` | true | Download vanilla client textures on first run |
| `publicHost` | "" | Host shown in `/colonyweb` link (blank = auto-detect) |
| `authEnabled` | true | Require a pairing-code sign-in |
| `sessionDays` | 30 | How long a browser stays signed in |
| `loginCodeMinutes` | 10 | How long a pairing code stays valid |

---

## 12. Commands

`/colonyweb` — link, `port` and `sync` are open to anyone; `status`, `sync <player>`,
`access grant|revoke|list` and `logout <player>` require op (permission level 2). The Brigadier
tree lives in `ColonyWebCommand`; the bodies in `AccessCommands`. Full table in the README.

Design notes:

- Codes use a 28-character alphabet with no vowels or look-alikes (`0/O`, `1/I`), formatted
  `XXXX-XXXX`. Input is normalised (dashes and spaces stripped, upper-cased) so typing is
  forgiving.
- `sync` runs on the server thread, because that is where colony membership has to be read.
- An operator syncing someone else gets the code in their own chat **and** whispers it to the
  target when they're online, so it isn't read off a shared screen.

---

## 12b. Auth Model

- `/colonyweb sync` mirrors the player's colonies into `WebUser.colonies` (replaced wholesale
  on every sync) and issues a single-use code held **in memory only**.
- Redeeming a code mints a random 32-byte token, sets it as an `HttpOnly; SameSite=Lax` cookie,
  and stores only its **SHA-256 hash** in `auth.json`.
- `WebUser.granted` holds explicit operator grants; these survive re-syncs, unlike `colonies`.
- `canAccess` = admin, or the colony is in `colonies ∪ granted`.
- Per-colony routes answer an identical `403` for "not yours" and "doesn't exist", so the API
  can't enumerate colonies.
- Expired sessions are purged by the refresh scheduler's housekeeping pass.

---

## 13. Lifecycle Wiring (`ColonyWeb.java`)

- Constructor: register `Config.SPEC`, register mod-bus + forge-bus listeners.
- `ServerStartingEvent`: init `VanillaAssetProvider` (async download), start `WebServer`,
  start the refresh scheduler (a `ScheduledExecutorService` or server-tick counter) that
  every `refreshIntervalSeconds` diff-scans colonies and calls `SseBroadcaster`.
- `RegisterCommandsEvent`: register `/colonyweb`.
- `ServerStoppingEvent`: stop scheduler, stop `WebServer`, flush caches.
- Detect MineColonies via `ModList.get().isLoaded("minecolonies")` and log the result.

---

## 14. Change Detection for SSE

- Maintain a lightweight hash per colony snapshot (hash of building levels, work-order
  ids/levels/progress buckets, warehouse stack counts).
- Each refresh tick: rebuild summaries, compare hashes; on change emit
  `{"type":"colony","id":N}` (and `{"type":"colonies"}` when the set of colonies changes).
- Progress is bucketed (e.g. to whole %) to avoid excessive events.

---

## 15. Build & Run

```powershell
# from project root
./gradlew build            # compiles the mod (no MineColonies needed to compile)
./gradlew runServer        # dev server; drop MineColonies + Domum Ornamentum jars in run/mods
```
Then open `http://localhost:8723/` (or the configured port). Use `/colonyweb` in-game to
get the link.

Production: build the jar (`build/libs/colonyweb-2.0.0.jar`), place it in the
server's `mods/` folder alongside MineColonies + Domum Ornamentum.

After editing any partial, JS module or `tailwind.input.css`, regenerate the stylesheet:

```powershell
./tailwindcss.exe -c tailwind.config.js -i tailwind.input.css -o src/main/resources/webroot/style.css --minify
```

---

## 16. Implementation Order (task checklist)

- [x] `build.gradle` repo + optional deps
- [x] `gradle.properties` metadata + version placeholders
- [x] `mods.toml` server side + optional deps
- [x] `Config.java` rewritten
- [x] Mod entry class — lifecycle wiring, MineColonies detection, scheduler
- [x] `web/WebServer.java` + `web/SseBroadcaster.java` + `web/JsonUtil.java`
- [x] `web/handlers/*` — Api, Events (SSE), Texture, Static
- [x] `colony/model/*` DTOs
- [x] `colony/MineColoniesReflect.java` + `colony/ColonyDataProvider.java`
- [x] `texture/VanillaAssetProvider.java`
- [x] `texture/ModelResolver.java` + `texture/TextureService.java` + `texture/PngCache.java`
- [x] `texture/DomumOrnamentumResolver.java`
- [x] `command/ColonyWebCommand.java`
- [x] `webroot/` front-end + generated `style.css`
- [x] Change-detection hashing for SSE
- [x] Manual test with a real colony (builder upgrading a hut, warehouse stocked)

### v2 — tabs, deeper MineColonies integration, 3D Domum icons

- [x] `colony/CitizenScanner.java` — citizens: job, 11 skills, happiness modifiers, inventory
- [x] `colony/ResearchScanner.java` — branches, per-research state/progress/effects/cost
- [x] `colony/CombatScanner.java` — raid pressure, guard roster, guard posts, colony events
- [x] `colony/Scan.java` + `util/Text.java` — shared coercion / naming helpers
- [x] `MineColoniesReflect#invokeAny` — signature-agnostic calls (MineColonies moves params
      between versions)
- [x] DTOs: `ColonyStats`, `CitizenInfo`, `ResearchInfo`, `CombatInfo`, `ItemInfo`,
      `ItemCount`, `MaterialComponent`
- [x] Endpoints: `/citizens`, `/citizen/{id}`, `/research`, `/combat` (kept out of the snapshot
      so the live-updated document stays small)
- [x] `texture/BlockModel.java` + `ModelResolver#resolveModel` — parse model geometry
- [x] `texture/IsometricRenderer.java` — z-buffered software rasterizer for 3D block icons
- [x] Front-end: seven tabs, citizen detail modal, Minecraft-tooltip item lists, colony map
- [x] Tailwind component layer for tabs, stat tiles, meters and MC tooltips

### v2.0.0 — rename to ColonyWeb, accounts, and the code split

- [x] Rename: package `…plugins.colonyweb`, mod id `colonyweb`, `ColonyWeb.java`, version 2.0.0
- [x] `auth/` — `AuthService` (codes, sessions, grants), `AuthStore`, `WebUser`,
      `StoredSession`, `SessionCookie`
- [x] `web/handlers/AuthHandler.java` + `RequestAuth.java`; API/events/textures behind a session
- [x] `/api/colonies` filtered per player; per-colony routes 403 identically for
      "not yours" and "doesn't exist"
- [x] Commands: `sync`, `sync <player>`, `access grant|revoke|list`, `logout <player>`
- [x] Split `ColonyDataProvider` → `ColonyLookup`, `BuildingScanner`, `WorkOrderScanner`,
      `WarehouseScanner`, `StatsBuilder`, `ScanContext`, `ColonyScan`
- [x] New `service/` package: `ColonyRefreshScheduler` + `ScanHasher`
- [x] Front-end split into ES modules + HTML partials; `boot.js` loads partials before Alpine
- [x] Design pass: sidebar shell, new dark palette, semantic component layer, sign-in screen
- [x] Verified every tab, both modals, the sign-in screen and the narrow layout against a mock
      server (headless Chrome, zero console errors)

---

## 17. Risks / Notes for Copilot

- **Reflection signatures**: MineColonies internal names differ across versions. Keep all
  reflection in `MineColoniesReflect`, fail soft, and log which lookups miss so they're
  easy to fix against the installed version.
- **Vanilla textures on dedicated servers**: only available via the download step — never
  assume `assets/minecraft/**` is on the server classpath.
- **Domum Ornamentum NBT**: material data lives in block-entity/stack capability + NBT;
  read defensively and fall back to the base DO texture.
- **Thread-safety**: HTTP handlers run off-thread; never touch the server world directly
  from a handler. Snapshots are built on the server thread (or a synchronized scan) and
  handed to handlers as immutable DTOs.
- **Security**: the dashboard is read-only, and with `authEnabled` (the default) every data
  route needs a session obtained from an in-game pairing code. Traffic is still plain HTTP, so
  on an untrusted network put it behind a TLS-terminating reverse proxy — a session cookie sent
  in the clear can be captured. With `authEnabled = false` it is fully public to anyone who can
  reach the port.
- **Never persist a secret**: pairing codes stay in memory, and only SHA-256 hashes of session
  tokens reach `auth.json`.

