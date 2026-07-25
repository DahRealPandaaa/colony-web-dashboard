# Colony Web Dashboard — Project Plan & Specification

> Paste this whole file into your workspace (kept as `PLAN.md`) so Copilot has full
> context on the goal, architecture, and every module to build. This is the single
> source of truth for the mod.

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
| Domum Ornamentum | Resolve the block's material component(s) from stack NBT and use the material's texture as the icon (flat inventory-face approximation). |
| Block icons | Flat inventory-face texture (not a 3D isometric render) for v1. |
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

Base package: `DahRealPanda.plugins.untitled1` (mod id `untitled1`).

```
src/main/java/DahRealPanda/plugins/untitled1/
  Untitled1.java                 # @Mod entry — wires everything, server lifecycle events
  Config.java                    # (done) server config

  web/
    WebServer.java               # HttpServer bootstrap, routing, lifecycle
    SseBroadcaster.java          # tracks SSE clients, pushes events
    handlers/
      ApiHandler.java            # /api/colonies, /api/colony/{id}
      EventsHandler.java         # /events  (SSE)
      TextureHandler.java        # /textures/{key}.png
      StaticHandler.java         # serves webroot/ assets (index.html, app.js, style.css)
    JsonUtil.java                # Gson instance + helpers

  colony/
    ColonyDataProvider.java      # top-level: enumerate colonies + build snapshots (reflection)
    MineColoniesReflect.java     # cached reflection handles (classes/methods) + null-safety
    model/
      ColonySummary.java         # id, name, dimension, owner, position, counts
      ColonySnapshot.java        # full detail: buildings, builders, warehouse
      BuildingInfo.java          # building id, type, level, position, hut inventory
      WorkOrderInfo.java         # what is being built/upgraded, target level, progress
      BuilderInfo.java           # builder name, hut pos, assigned work order id
      ResourceEntry.java         # itemKey, display name, needed, available(hut),
                                 #   availableWarehouse, deliverable flag, nbt hash
      ItemRef.java               # registry name + optional nbt hash -> texture key

  texture/
    TextureService.java          # itemKey/stack -> PNG bytes (cache in memory + disk)
    ModelResolver.java           # read models/item + models/block JSON from classpath/jars
    VanillaAssetProvider.java    # download+cache vanilla client jar assets on first run
    DomumOrnamentumResolver.java # resolve DO material components -> texture key
    PngCache.java                # keyed cache -> byte[]; disk-backed under run/colonyweb-cache

  command/
    ColonyWebCommand.java        # /colonyweb -> prints dashboard URL, status, port

src/main/resources/
  META-INF/mods.toml             # (done)
  pack.mcmeta                    # (done)
  webroot/
    index.html                   # dashboard shell + colony selector
    app.js                       # fetch /api, subscribe to /events (SSE), render cards
    style.css                    # layout & theming
```

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

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/` | `index.html` |
| GET | `/app.js`, `/style.css` | static assets |
| GET | `/api/colonies` | colony list for the selector |
| GET | `/api/colony/{id}` | full snapshot for one colony |
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
5. **Cache** results in `PngCache` (memory + `run/colonyweb-cache/textures/`).
6. If nothing resolves, return a generated **placeholder** PNG (e.g. magenta/black
   checker) so the UI still lays out.

### VanillaAssetProvider
- On first server start (if `autoDownloadVanillaAssets`):
  1. Fetch Mojang version manifest:
     `https://launchermeta.mojang.com/mc/game/version_manifest_v2.json`.
  2. Find entry for `1.20.1` → fetch its version JSON → `downloads.client.url`.
  3. Download `client.jar` to `run/colonyweb-cache/`.
  4. Lazily extract `assets/minecraft/textures/**` entries on demand (or all up front).
- Cache so it only downloads once. Respect a config flag and fail soft (log + placeholder)
  if offline.

---

## 10. Web UI (`webroot/`)

- **index.html**: header with a **colony `<select>`** dropdown, a connection/live status
  dot, and a container for building cards.
- **app.js**:
  - On load: `GET /api/colonies`, populate the selector, select first (or from URL hash).
  - `GET /api/colony/{id}` and render:
    - A **"Builders" panel**: each builder → which building + level they're upgrading,
      with a progress bar.
    - A **grid of building cards**: title (name + level → target), a "being built by X"
      badge, and a resource table with columns: **icon**, item name, **needed**,
      **in hut**, **in warehouse**, status (enough / deliverable / missing) color-coded.
    - A **warehouse panel** summarizing available stock.
  - Icons: `<img src="/textures/{encodeURIComponent(itemKey)}.png">` with lazy loading.
  - Open an `EventSource('/events')`; on `update` re-fetch `/api/colonies` and the current
    `/api/colony/{id}`. Show a reconnecting status indicator.
- **style.css**: responsive card grid, resource table styling, status colors
  (green = satisfied, amber = deliverable from warehouse, red = missing), dark theme.

---

## 11. Config (`Config.java` — done)

| Key | Default | Meaning |
|-----|---------|---------|
| `httpPort` | 8723 | Web server port |
| `bindAddress` | `0.0.0.0` | Bind interface (`127.0.0.1` for local only) |
| `refreshIntervalSeconds` | 3 | Re-scan + SSE push cadence |
| `autoDownloadVanillaAssets` | true | Download vanilla client textures on first run |
| `publicHost` | "" | Host shown in `/colonyweb` link (blank = auto-detect) |

---

## 12. Command

`/colonyweb` (permission level 0, any player or console):
- Prints the dashboard URL: `http://<publicHost-or-detected-ip>:<httpPort>/`.
- Sub-actions (optional): `/colonyweb status` (running? clients connected? MineColonies
  detected?), `/colonyweb port`.

---

## 13. Lifecycle Wiring (`Untitled1.java`)

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

Production: build the jar (`build/libs/untitled1-1.0-SNAPSHOT.jar`), place it in the
server's `mods/` folder alongside MineColonies + Domum Ornamentum.

---

## 16. Implementation Order (task checklist)

- [x] `build.gradle` repo + optional deps
- [x] `gradle.properties` metadata + version placeholders
- [x] `mods.toml` server side + optional deps
- [x] `Config.java` rewritten
- [ ] `Untitled1.java` — lifecycle wiring, MineColonies detection, scheduler
- [ ] `web/WebServer.java` + `web/SseBroadcaster.java` + `web/JsonUtil.java`
- [ ] `web/handlers/*` — Api, Events (SSE), Texture, Static
- [ ] `colony/model/*` DTOs
- [ ] `colony/MineColoniesReflect.java` + `colony/ColonyDataProvider.java`
- [ ] `texture/VanillaAssetProvider.java`
- [ ] `texture/ModelResolver.java` + `texture/TextureService.java` + `texture/PngCache.java`
- [ ] `texture/DomumOrnamentumResolver.java`
- [ ] `command/ColonyWebCommand.java`
- [ ] `webroot/index.html` + `app.js` + `style.css`
- [ ] Change-detection hashing for SSE
- [ ] Manual test with a real colony (builder upgrading a hut, warehouse stocked)

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
- **Security**: `bindAddress`/port expose data on the network. Document that it's an
  unauthenticated read-only dashboard; recommend firewalling or `127.0.0.1` + reverse
  proxy for public servers.

