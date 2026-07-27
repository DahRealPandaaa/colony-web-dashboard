# ColonyWeb

A **server-side-only** Minecraft Forge mod (MC **1.20.1**, Forge **47.4.0**) that expands on
[**MineColonies**](https://www.curseforge.com/minecraft/mc-mods/minecolonies) by exposing a
**live web dashboard**, served by the Minecraft server itself. Open it in any browser to see —
per colony — which builder is working on which building, exactly what resources each
build/upgrade needs, what the hut already has, and what the warehouse can provide, all with
live item/block icons (including [**Domum Ornamentum**](https://www.curseforge.com/minecraft/mc-mods/domum-ornamentum)
textured blocks) and no manual refresh.

Players sign in with a **pairing code they get in-game** (`/colonyweb sync`) and only ever see
the colonies they actually belong to.

The mod does **not** need to be installed on the client — only on the server.

---

## Table of Contents

- [Features](#features)
- [Signing In](#signing-in)
- [Screenshots / UI Overview](#screenshots--ui-overview)
- [How It Works](#how-it-works)
- [Project Structure](#project-structure)
- [HTTP API & Endpoints](#http-api--endpoints)
- [Data Model](#data-model)
- [Configuration](#configuration)
- [In-Game Commands](#in-game-commands)
- [Building From Source](#building-from-source)
- [Installing on a Server](#installing-on-a-server)
- [Hosting & Networking Notes](#hosting--networking-notes)
- [Troubleshooting](#troubleshooting)
- [Tech Stack](#tech-stack)
- [Compatibility & Versions](#compatibility--versions)
- [Security](#security)
- [License](#license)

---

## Features

### Live dashboard
- **Per-colony view** with a colony selector (only the colonies you may see).
- **Seven tabs** in a sidebar — Overview, Map, Buildings, Citizens, Research, Combat and
  Warehouse — each deep-linkable via the URL hash (`#<colonyId>/<tab>`), each with a live count
  badge.
- **Live updates via SSE** (Server-Sent Events) — the page re-renders automatically when
  builders progress, resources change, or work orders start/finish. No manual refresh.
- **Connection status indicator** (live / reconnecting) with an animated dot.
- Collapses to a horizontal tab strip on narrow screens.

### Accounts & access control
- **Pairing-code sign-in.** A player runs `/colonyweb sync` in-game and gets a one-shot
  `XXXX-XXXX` code (click it in chat to copy). Entering it in the browser creates a session.
- **Per-player scoping.** `/api/colonies` lists only the colonies that player belongs to, and
  every per-colony route answers the same `403` whether or not the colony exists — so the API
  cannot be used to enumerate other people's colonies.
- **Operators** see every colony and can pair, grant, revoke or sign out other players.
- Sessions are `HttpOnly; SameSite=Lax` cookies. Only **SHA-256 hashes** of session tokens are
  written to disk — the token itself is never persisted, and neither is the pairing code.
- Auth can be turned off entirely (`authEnabled = false`) for a private/LAN server.

### Overview tab
- Stat tiles for citizens, happiness, buildings, defence, warehouse stock, research, builders
  and average saturation.
- Every **active work order** with its action badge, progress bar and assigned builder.
- The **Builders panel**: who is building what, and how far along.

### Map tab
- A **live top-down map of the colony**, in the spirit of Dynmap but drawn by this mod alone —
  no extra server, no extra port.
- The terrain is rendered **server-side from the world itself**, one pixel per block, using each
  block's own **map colour** (the palette the in-game map item uses) with **hill shading** from
  the surface heightmap and **depth shading** for water. Rivers, roads, fields and cliffs are all
  legible at a glance.
- **Every building and decoration** is pinned at its real coordinates with its hut-block artwork;
  anything under construction is highlighted. **Click a pin** for the same detail modal the
  Buildings tab opens.
- **Every citizen** is plotted where they actually are, colour-coded by worker / unemployed /
  child / not-currently-loaded. **Click a dot** for their full citizen modal.
- **Drag to pan, scroll to zoom** (20 %–800 %), with *Fit* and *Town hall* framing buttons, a
  live X/Z readout under the cursor, and toggles for buildings, citizens and name labels.
- The map is drawn **incrementally, a few chunks per scan**, closest to the town hall first, so
  it never costs the server a tick spike — a coverage chip shows the progress. Areas nobody has
  loaded stay blank, and once a chunk *has* been drawn it stays on the map even after it unloads.
- Mapping only runs **while somebody has the tab open**; a server nobody is looking at does no
  mapping work at all.

### Citizens tab
- A card per citizen: job, workplace, **health / saturation / happiness** meters, their job's
  **primary and secondary skills**, and how full their pack is.
- Search by name, job or building; filter by job; sort by job, name, total skill, happiness or
  health.
- **Click a citizen** for the full detail modal: all eleven **skills** with levels, **perks &
  grievances** (the happiness modifiers the game tracks, colour-coded by whether they help or
  hurt), and their complete **inventory** rendered as in-game tooltips.

### Research tab
- Every **research branch** with completed / in-progress / total counts and a progress bar.
- Per research: tier, state, a **live progress bar** for whatever the university is working on,
  the **effects** it grants and its **item cost** with icons.
- Filter by branch and by state (everything / in progress / completed / not started).

### Combat tab
- **Raid banner**: whether the colony is under attack, nights since the last raid, raid level
  and whether spies are active.
- Defence tiles: guard count, average guard level, average guard health, guard posts, and
  **unclaimed graves**.
- The **guard roster**, sorted best-equipped first, showing per guard: their level and health,
  **every equipped item** (helmet, chestplate, leggings, boots and what they are holding, with
  enchanted pieces marked and durability on hover), their **total armour value**, the **best
  armour material** they wear as a colour-coded badge, and the **guard post they are stationed
  at together with that post's level** — so it is obvious which tower holds the best kit.
- Every **guard tower / barracks** and how well it is staffed.
- Any **active colony events** (ongoing raids) with their position.

### Builders panel *(Overview tab)*
- Every builder and the building/decoration they are currently working on.
- The **target level** and action (BUILD / UPGRADE / REPAIR / REMOVE).
- A **progress bar** per builder.

### Buildings & decorations
- A responsive **grid of cards** for every building **and decoration** in the colony.
- **Decorations** (non-building structures) are detected from work orders and labeled with a
  dedicated badge.
- Each card shows: name, current → target level, a "being built by *X*" badge, a progress bar,
  quick **status chips** (how many resources are enough / deliverable / missing), and a strip of
  item icons.
- **Click any card** to open a **detail modal** with a large overview.

### Building detail modal
- Every required resource as an **in-game style tooltip card**: icon, item name, the Domum
  Ornamentum material breakdown, how many are **in the hut** and **in the warehouse**, the
  amount **needed**, and a colour-coded **status pill**.
- **Per-building item search** to quickly find a specific resource.
- Resources sorted **missing-first** so shortfalls are obvious.
- Progress bar and "built by" details.

### Minecraft-style item lists
Warehouse stock, building requirements and citizen inventories are all rendered as
**Minecraft tooltips** — the dark violet-bordered panel, monospaced type, and the same lines
the game shows for a Domum Ornamentum block:

```
┌────────────────────────────────────────┐
│ ▣  Brick Extra Shingles          x1284 │
│    Crafted in the Architects Cutter    │
│    Supported by: Oak Planks            │
│    Main Material: Brick Extra          │
└────────────────────────────────────────┘
```

### Search, filters & sorting
- **Global search** across buildings — matches building names **and** the items/materials they
  require.
- **"In progress only"** and **"Show decorations"** toggles.
- **Sort** by: Status (missing first), Progress, Name, or Level.
- Dedicated **citizen**, **research** and **warehouse** filters.

### Craftable items
- Anything a colony worker already knows a recipe for is marked with a green **craft badge**
  wherever it appears — warehouse stock, build requirements, citizen inventories and research
  costs.
- The recipe list is read from every crafting building's learned recipes, so it reflects what
  the colony can make *right now*, not what the mod could theoretically produce.

### Resource-scroll parity
- The required-resources data mirrors what MineColonies' in-game **resource scroll** shows —
  read directly from the assigned builder's building.
- Status is color-coded:
  - 🟢 **enough** — the hut already has it.
  - 🟡 **deliverable** — the warehouse can cover the shortfall.
  - 🔴 **missing** — not available.

### Warehouse tab
- Aggregated stock across all colony warehouse(s), searchable and sortable by count or name.
- Each entry is a Minecraft-style tooltip card showing the registry id for vanilla/modded items
  and the full material breakdown for Domum blocks.

### Server-side texture pipeline
- Item/block **PNG icons generated on the server** and served to the browser.
- A dedicated server has no client textures, so the mod **downloads & caches the vanilla client
  jar** for the running MC version (once) to source vanilla icons.
- Modded textures (MineColonies, Domum Ornamentum, etc.) are read from the installed mod jars.
- **Domum Ornamentum support**: resolves the block's **material components** from stack NBT
  (`BlockEntityTag → textureData`) and shows each one as its own tooltip line ("Supported by:
  Oak Planks", "Main Material: Brick Extra"). Each textured variant caches to its own PNG.
- **The icon players actually see in their inventory.** A block's inventory icon is its *model*
  drawn in 3D, not one of its face textures — so the mod parses the model's geometry and
  renders it isometrically under the vanilla GUI transform (`[30, 225, 0]`). Oak stairs look
  like stairs, a furnace shows its front, a fence shows its inventory post. The item model
  decides, exactly as it does in game: items whose model is a flat sprite (wheat, sticks,
  doors, torches) keep that sprite, because that is equally what the inventory shows.
  Rendering is a self-contained software rasterizer (z-buffered, nearest-neighbour sampled,
  vanilla face shading, cut-out geometry preserved) — no client, no OpenGL.
- Faces with a `tintindex` (grass, leaves) are coloured with the temperate grass/foliage green;
  their textures ship greyscale and would otherwise render bone white.
- **Domum Ornamentum blocks** go one step further: each material component's texture is
  substituted into the model's texture variables first, so a *Brick Extra Shingle* looks like a
  shingle made of brick rather than a flat brick square. If a model cannot be parsed it falls
  back to the flat material texture, and then to the plain item texture.
- Animated textures are cropped to their first frame; unresolved icons fall back to a generated
  magenta/black placeholder so the UI always lays out.
- **Two-tier cache** (in-memory + disk under `<server>/colonyweb/`), stamped with a renderer
  version so an upgrade that changes how icons look discards its own stale PNGs on first start.

### Reflection-based soft dependency
- Integrates with MineColonies / Domum Ornamentum **purely through reflection at runtime**.
- The mod **compiles and loads without them present** and never crashes if their API shifts —
  every lookup fails soft and logs which lookups miss.

### In-game commands
- `/colonyweb` prints a **clickable** dashboard link in chat; `sync` issues a pairing code, and
  operators get `status`, `access grant|revoke|list` and `logout`.

---

## Signing In

1. Join the server and run **`/colonyweb sync`**.
2. The mod reads which colonies you belong to (owner or permissions member) and prints a
   one-shot code:

   ```
   Open http://your-server:8723/ and enter this code:
     A4TC-9KHM                                    ← click to copy
   Valid for 10 minutes · unlocks 2 colony/colonies.
   ```
3. Open the dashboard and type the code. The browser stays signed in for `sessionDays` (30 by
   default).

Re-running `sync` re-reads your colony membership, so run it again after joining or leaving a
colony. It also invalidates nothing — existing sessions keep working and pick up the new list.

**Operators** can additionally:

```
/colonyweb sync <player>                 issue a code for someone else
/colonyweb access grant <player> <id>    grant one extra colony (survives re-sync)
/colonyweb access revoke <player> <id>   take that grant away
/colonyweb access list <player>          show everything that player can see
/colonyweb logout <player>               drop all of that player's browser sessions
```

---

## Screenshots / UI Overview

```
┌────────────────┬──────────────────────────────────────────────────────────┐
│ ⌂ ColonyWeb    │ Colony overview                  ● Live  [Colony ▾]  ⟳   │
│   MINECOLONIES ├──────────────────────────────────────────────────────────┤
│                │ ┌──────────┐┌──────────┐┌──────────┐        BUILDERS     │
│ ▸ Overview     │ │ CITIZENS ││ HAPPINESS││ BUILDINGS│   ┌───────────────┐  │
│   Map          │ │  24 / 30 ││   7.4    ││    14    │   │ [UPGRADE]     │  │
│   Buildings 14 │ │ 3 kids   ││ ▓▓▓▓▓▓▓░ ││ 2 orders │   │ Barracks 3→4  │  │
│   Citizens  24 │ └──────────┘└──────────┘└──────────┘   │ ▓▓▓▓▓░░░  55% │  │
│   Research  12 │                                        └───────────────┘  │
│   Combat     3 │ WORK ORDERS                                               │
│   Warehouse 37 │  [UPGRADE] Barracks    ▓▓▓▓▓░░░  3 → 4   Bob Miller       │
│                │  [BUILD]   Paved Road  ▓▓░░░░░░  0 → 1   unclaimed        │
│ NA Nathan      │                                                           │
│    Operator  ⇥ │                                                           │
└────────────────┴──────────────────────────────────────────────────────────┘

  Map tab ──────────────────────────────────────────────────────────────────┐
  │ [Buildings][Citizens][Labels]  Fit  Town hall   Mapping 92%   X 128 Z -341│
  │ ┌───────────────────────────────────────────────────────────────────────┐ │
  │ │ ≈≈≈≈≈░░░░▒▒▒▒▒▒▒▒▓▓▓▓▓░░░░░░░░░░░░░░░░░░▒▒▒▒▒▒░░░░░░░░░░░░░░░░░░░░░░░ │ │
  │ │ ≈≈≈≈░░░░░▒▒▒▒[▣]▒▒▒░░░░░·  ·  ░░░░░░░░░░▒▒▒▒░░░░░░░░░░░░░[▣]░░░░░░░░ │ │
  │ │ ≈≈≈░░░░░░▒▒▒▒▒▒▒▒▒░░░░(◉)░ ·░░░░░[▣]░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │ │
  │ │ ░░░░░░░▓▓▓▓▓▓▓░░░░░░░·  · ░░░░░░░░░░░░░░░[▣]░░░░░░░░░░░░░░░░░░░░░░░░ │ │
  │ │ ● Town hall  ● Being built  ● Worker  ● Unemployed  ● Child          │ │
  │ └───────────────────────────────────────────────────────────────────────┘ │
  │   ▣ building pin (click → detail modal)   · citizen   (◉) town hall       │
  └───────────────────────────────────────────────────────────────────────────┘

  Citizens tab → click a citizen ───────────────────────────────────────────┐
  │ Bob Miller  [Builder]        Works at Barracks · Lives at Residence     │
  │ HEALTH 15  SATURATION 7.0  HAPPINESS 6.0  TOTAL SKILL 121               │
  │ Skills                          │ Inventory              4 / 27 slots   │
  │  Adaptability [PRIMARY]   34    │ ┌───────────────────────────────────┐ │
  │  Athletics   [SECONDARY]   4    │ │ ▣ Brick Extra Shingles       x64  │ │
  │  Creativity               44    │ │   Crafted in the Architects Cutter│ │
  │  …                              │ │   Supported by: Oak Planks        │ │
  │ Perks & grievances              │ │   Main Material: Brick Extra      │ │
  │  Homelessness ×0.75  Food ×1.25 │ └───────────────────────────────────┘ │
  └─────────────────────────────────────────────────────────────────────────┘
```

---

## How It Works

1. On `ServerStartingEvent`, the mod:
   - (optionally) downloads & caches the vanilla client jar for textures,
   - starts an embedded HTTP server (`com.sun.net.httpserver.HttpServer`),
   - starts a scheduler that re-scans colonies every few seconds **on the server thread**.
2. Each scan builds immutable snapshot DTOs (colonies, buildings, decorations, builders, work
   orders, warehouse) via reflection into MineColonies, and stores them in a thread-safe cache.
3. A lightweight per-colony **hash** is compared each tick; on change, an SSE `update` event is
   pushed to connected browsers, which re-fetch the affected endpoint.
4. HTTP handlers run off-thread and only read the cached immutable DTOs, so the world is never
   touched from a request thread.
5. The colony map rides the same server-thread pass, because it too needs the world: for each
   colony a browser is currently looking at, it draws a small budget of **loaded** chunks into
   that colony's raster (nearest the town hall first), then hands a copy to a worker thread to
   shade and encode as a PNG. Requesting the map is what registers the interest, so nothing is
   mapped unless somebody is watching.

---

## Project Structure

```
src/main/java/DahRealPanda/plugins/colonyweb/
  ColonyWeb.java                 # @Mod entry — server lifecycle + command wiring
  Config.java                    # server config (port, bind, refresh, assets, host, auth)
  ColonyWebService.java          # wires web server, auth, provider, scheduler together

  auth/
    AuthService.java             # pairing codes, sessions, grants, access checks
    AuthStore.java               # atomic JSON persistence of auth.json
    WebUser.java                 # a player: mirrored colonies, explicit grants, sessions
    StoredSession.java           # one browser session (SHA-256 token hash + expiry)
    SessionCookie.java           # read/set/clear the HttpOnly session cookie

  web/
    WebServer.java               # HttpServer bootstrap, routing, lifecycle
    SseBroadcaster.java          # tracks SSE clients, pushes events, heartbeats
    JsonUtil.java                # Gson instance + request/response helpers
    handlers/
      AuthHandler.java           # /auth/me, /auth/login, /auth/logout
      RequestAuth.java           # resolve the session cookie -> WebUser, or 401
      ApiHandler.java            # /api/colonies, /api/colony/{id}/…  (access-scoped)
      EventsHandler.java         # /events (SSE)
      TextureHandler.java        # /textures/{key}.png
      MapHandler.java            # /map/{colonyId}.png
      StaticHandler.java         # serves webroot/ assets

  colony/
    ColonyDataProvider.java      # orchestrates one scan; owns nothing else
    ColonyLookup.java            # find colonies/buildings/work orders + a player's colonies
    BuildingScanner.java         # building identity: position, type, hut block, pretty name
    WorkOrderScanner.java        # work orders, decorations, builder linkage, resource scroll
    WarehouseScanner.java        # per-rack stock tally, de-duplicated colony-wide
    CitizenScanner.java          # citizens: job, skills, happiness modifiers, inventory
    EquipmentScanner.java        # what a citizen wears and holds, with tier + armour value
    RecipeScanner.java           # which items the colony knows how to craft
    ResearchScanner.java         # walk the global research tree + this colony's state
    CombatScanner.java           # raid pressure, guard roster, guard posts, events
    StatsBuilder.java            # roll the scanned payloads up into ColonyStats
    ScanContext.java             # per-scan working state shared by the scanners
    ColonyScan.java              # one scan's outputs (snapshot + sections)
    MineColoniesReflect.java     # cached reflection handles + null-safety
    Scan.java                    # shared coercion helpers + ItemStack -> ItemInfo
    ColonyCache.java             # thread-safe latest-scan holder (all payloads)
    model/                       # DTOs: ColonySummary, ColonySnapshot, ColonyStats,
                                 #       BuildingInfo, WorkOrderInfo, BuilderInfo,
                                 #       CitizenInfo, ResearchInfo, CombatInfo, MapInfo,
                                 #       ItemInfo, ItemCount, MaterialComponent,
                                 #       ResourceEntry, ItemRef

  map/
    ColonyMapService.java        # per-colony surface maps: interest, budgets, PNG publishing
    ColonyMap.java               # one colony's raster + per-chunk draw stamps
    SurfaceRenderer.java         # loaded chunks -> map colours, hill shading -> PNG

  service/
    ColonyRefreshScheduler.java  # periodic scan on the server thread + SSE publish
    ScanHasher.java              # cheap change hash, bucketed so idle wandering is ignored

  texture/
    TextureService.java          # itemKey/stack -> PNG bytes (memory + disk cache)
    ModelResolver.java           # read item/block model JSON + geometry from jars / vanilla
    BlockModel.java              # flattened model: merged textures + cuboid elements
    IsometricRenderer.java       # software rasterizer -> inventory-style 3D block icons
    VanillaAssetProvider.java    # download + cache vanilla client jar assets
    DomumOrnamentumResolver.java # resolve DO material components -> texture key + tooltip lines
    PngCache.java                # keyed cache -> byte[] (disk-backed)

  util/
    Text.java                    # component unwrapping + id/translation-key humanizing

  command/
    ColonyWebCommand.java        # /colonyweb — the Brigadier tree only
    AccessCommands.java          # sync / access grant|revoke|list / logout bodies

src/main/resources/
  META-INF/mods.toml             # server-side; optional deps on minecolonies & domum_ornamentum
  pack.mcmeta
  webroot/
    index.html                   # app shell: sidebar, topbar, partial slots
    favicon.svg
    style.css                    # generated by Tailwind — do not edit by hand
    partials/                    # one file per tab, fetched at boot
      overview.html  map.html    buildings.html  citizens.html
      research.html  combat.html warehouse.html
      modal-building.html  modal-citizen.html  login.html
    js/
      boot.js                    # load the partials, then start Alpine
      dashboard.js               # composes the mixins; colony/tab routing, loading, SSE
      api.js                     # every server call + the Unauthorized error type
      format.js                  # pure presentation helpers (pct, badges, labels)
      auth.js                    # sign-in screen state
      icons.js                   # wiki artwork lookup + per-<img> fallback chain
      map.js                     # colony map: pan/zoom transform, markers, coverage polling
      overview.js  buildings.js  citizens.js  research.js
      combat.js    warehouse.js
    img/                         # bundled MineColonies wiki artwork
      ATTRIBUTION.md             # source + licence — READ THIS BEFORE PUBLISHING
      blocks/blockhut*.png       # the game's own hut block icon, one per building
      jobs/<job>-<gender>.png    # citizen portraits, plus _citizen-* as the generic
    vendor/alpine.min.js         # bundled Alpine.js (no CDN dependency)

tools/
  wiki-images.js                 # picks which wiki images to bundle -> wiki-images.tsv
  WikiImages.java                # downloads + downscales them into webroot/img/
  wiki-images.tsv                # generated manifest (url, output, size, format)

tailwind.input.css               # Tailwind source + the design-system component layer
tailwind.config.js               # Tailwind config (scans webroot/**/*.{html,js})
```

Front-end layout: `boot.js` replaces every `<div data-partial="x">` in `index.html` with
`/partials/x.html` **before** appending the Alpine script, because Alpine only walks the DOM
once at startup. `dashboard.js` merges the per-tab mixins with `Object.defineProperties` rather
than a spread, so their getters stay lazy.

### Bundled artwork

Buildings use the game's own **hut block icons** and citizens use **worker portraits**, both
taken from the MineColonies wiki and downscaled into `webroot/img/`. The block renders live in
the wiki's [generator submodule](https://github.com/ldtteam/minecolonies-wiki-generator) rather
than the [wiki repo](https://github.com/ldtteam/MinecoloniesWiki) itself, because the wiki
gitignores them and pulls them in at build time.

A building's icon is chosen from the hut block actually placed in the world, falling back to one
derived from the building type. Every `<img>` then falls back again to the server-rendered block
texture on error, so a missing file costs nothing. Regenerate with:

```powershell
node tools/wiki-images.js > tools/wiki-images.tsv
java tools/WikiImages.java tools/wiki-images.tsv src/main/resources/webroot/img
```

> ⚠️ **Licence:** both wiki repositories are **GPL-3.0**, and this project's `mod_license` is
> currently *All Rights Reserved*. Those terms conflict for a distributed build. See
> [`webroot/img/ATTRIBUTION.md`](src/main/resources/webroot/img/ATTRIBUTION.md) for the options
> before publishing a jar containing these images.

The stylesheet is generated. After editing `tailwind.input.css`, any partial or any JS module,
rebuild it:

```powershell
./tailwindcss.exe -c tailwind.config.js -i tailwind.input.css -o src/main/resources/webroot/style.css --minify
```

---

## HTTP API & Endpoints

| Method | Path                                    | Auth | Purpose                                              |
|--------|-----------------------------------------|:----:|------------------------------------------------------|
| GET    | `/`                                     |  –   | dashboard `index.html`                               |
| GET    | `/style.css`, `/favicon.svg`            |  –   | static front-end assets                              |
| GET    | `/js/*.js`, `/partials/*.html`          |  –   | front-end modules and tab markup                     |
| GET    | `/img/**`                               |  –   | bundled building and citizen artwork                 |
| GET    | `/vendor/alpine.min.js`                 |  –   | bundled Alpine.js                                    |
| GET    | `/auth/me`                              |  –   | who the browser is; always `200`                     |
| POST   | `/auth/login`                           |  –   | `{"code":"XXXX-XXXX"}` → sets the session cookie     |
| POST   | `/auth/logout`                          |  –   | drops this browser's session                         |
| GET    | `/api/colonies`                         |  ✓   | colonies **this player** may see                     |
| GET    | `/api/colony/{id}`                      |  ✓   | buildings, builders, work orders, warehouse, stats   |
| GET    | `/api/colony/{id}/citizens`             |  ✓   | citizen roster with skills and happiness modifiers   |
| GET    | `/api/colony/{id}/citizen/{citizenId}`  |  ✓   | one citizen **plus their inventory and equipment**   |
| GET    | `/api/colony/{id}/research`             |  ✓   | research branches, states and progress               |
| GET    | `/api/colony/{id}/combat`               |  ✓   | raid status, guard roster, guard posts, events       |
| GET    | `/api/colony/{id}/map`                  |  ✓   | where the surface map sits and how far along it is   |
| GET    | `/events`                               |  ✓   | **SSE** stream; emits `colonies` and `colony` events |
| GET    | `/textures/{key}.png`                   |  ✓   | PNG icon for an item/block/DO stack                  |
| GET    | `/map/{colonyId}.png`                   |  ✓   | rendered colony surface, one pixel per block         |

Only the shell is public — it is just markup and needs a session before it shows anything.
Authenticated routes answer `401 {"error": "..."}` with no session, which the front-end turns
into a bounce back to the sign-in screen. A colony the player may not see answers `403` with
the same message whether or not it exists.

Citizens, research and combat live on their own endpoints (rather than inside the snapshot) so
the document re-fetched on every live update stays small. The front-end only requests the
sections the visible tab needs.

**SSE event format:**
```
event: update
data: {"type":"colony","id":1}
```
The client reacts by re-fetching `/api/colonies` and/or the current `/api/colony/{id}`.

> `itemKey` is the texture key: `namespace:path`, plus a `#<8charHash>` suffix for
> NBT-relevant (Domum Ornamentum) variants. When requesting `/textures/{key}.png`, the `#`
> must be URL-encoded.

---

## Data Model

`GET /api/colonies`
```json
[
  { "id": 1, "name": "Springfield", "dimension": "minecraft:overworld",
    "owner": "Steve", "x": 120, "y": 64, "z": -340,
    "buildingCount": 14, "builderCount": 3, "activeWorkOrders": 2 }
]
```

`GET /api/colony/{id}`
```json
{
  "id": 1, "name": "Springfield", "dimension": "minecraft:overworld", "owner": "Steve",
  "builders": [
    { "id": 42, "name": "Bob", "hutX": 118, "hutY": 64, "hutZ": -338, "assignedWorkOrderId": 7 }
  ],
  "workOrders": [
    { "id": 7, "buildingName": "Barracks", "buildingType": "minecolonies:barracks",
      "x": 130, "y": 64, "z": -350, "currentLevel": 3, "targetLevel": 4,
      "action": "UPGRADE", "builderId": 42, "builderName": "Bob", "progress": 0.55 }
  ],
  "buildings": [
    { "id": 130640350, "name": "Barracks", "type": "minecolonies:barracks",
      "kind": "building", "level": 3, "x": 130, "y": 64, "z": -350,
      "beingBuilt": true, "workOrderId": 7,
      "required": [
        { "itemKey": "domum_ornamentum:shingle#a1b2c3d4",
          "name": "Brick Extra Shingles", "material": "Oak Planks + Brick Extra",
          "domum": true, "craftedIn": "Architects Cutter",
          "components": [
            { "id": "domum_ornamentum:shingle_support", "label": "Supported by",
              "material": "Oak Planks", "itemKey": "minecraft:oak_planks" },
            { "id": "domum_ornamentum:shingle_face", "label": "Main Material",
              "material": "Brick Extra", "itemKey": "domum_ornamentum:brick_extra" }
          ],
          "needed": 18, "maxStackSize": 64, "inHut": 0, "inWarehouse": 64, "deliverable": true }
      ]
    }
  ],
  "warehouse": {
    "present": true,
    "stacks": [ { "itemKey": "minecraft:oak_planks", "name": "Oak Planks",
                  "material": null, "domum": false, "components": [],
                  "count": 512, "maxStackSize": 64 } ]
  },
  "stats": {
    "citizens": 24, "maxCitizens": 30, "children": 3, "unemployed": 2,
    "happiness": 7.4, "saturation": 12.6,
    "buildings": 14, "decorations": 1, "workOrders": 2, "builders": 3, "guards": 3,
    "warehouseTypes": 37, "warehouseItems": 14263,
    "researchCompleted": 12, "researchInProgress": 1,
    "raided": false, "nightsSinceRaid": 4
  }
}
```

`GET /api/colony/{id}/citizens`
```json
[
  { "id": 42, "name": "Bob Miller", "job": "Builder", "jobType": "minecolonies:builder",
    "jobIcon": "minecolonies:blockhutbuilder", "child": false, "female": false,
    "health": 15.0, "maxHealth": 20.0, "saturation": 7.0, "happiness": 6.0,
    "spawned": true, "x": 118, "y": 64, "z": -338,
    "workBuilding": "Builder", "workBuildingId": 130640350,
    "homeBuilding": "Residence", "homeBuildingId": 130640111,
    "status": "Working", "primarySkill": "Adaptability", "secondarySkill": "Athletics",
    "skillTotal": 121, "inventoryUsed": 4, "inventorySize": 27,
    "skills": [ { "name": "Adaptability", "level": 34, "xp": 120.0, "role": "primary" } ],
    "modifiers": [ { "name": "Homelessness", "factor": 0.75 } ] }
]
```

`GET /api/colony/{id}/citizen/{citizenId}` — the citizen above, their carried items and their kit:
```json
{ "citizen": { "id": 42, "name": "Bob Miller" },
  "inventory": [ { "slot": 0, "itemKey": "minecraft:bread", "name": "Bread", "count": 12,
                   "craftable": true } ],
  "equipment": [ { "slot": "Chest", "itemKey": "minecraft:iron_chestplate",
                   "name": "Iron Chestplate", "tier": "Iron", "tierRank": 4,
                   "armorPoints": 6, "enchanted": false, "durabilityPct": 91 } ] }
```

`GET /api/colony/{id}/research`
```json
{ "available": true, "completed": 12, "inProgress": 1, "total": 26,
  "branches": [
    { "id": "minecolonies:technology", "name": "Technology",
      "completed": 8, "inProgress": 1, "total": 14,
      "researches": [
        { "id": "minecolonies:bronzeage", "name": "Bronze Age",
          "branch": "minecolonies:technology", "depth": 2, "state": "IN_PROGRESS",
          "progress": 45, "maxProgress": 100,
          "effects": ["Miner works 10% faster"], "requirements": ["Stone Age"],
          "cost": [ { "itemKey": "minecraft:copper_ingot", "name": "Copper Ingot", "count": 32 } ] }
      ] }
  ] }
```

`GET /api/colony/{id}/combat`
```json
{ "raidsPossible": true, "underAttack": false, "nightsSinceRaid": 4, "raidLevel": 38,
  "spiesEnabled": false, "guardCount": 3, "guardCapacity": 6,
  "averageGuardLevel": 21.3, "averageHealthPct": 82.5, "graves": 1,
  "guards": [ { "id": 51, "name": "Alice Stone", "job": "Knight", "level": 22,
                "health": 16.0, "maxHealth": 20.0, "spawned": true,
                "building": "Barracks", "buildingId": 130640999, "buildingLevel": 4,
                "armorPoints": 20, "armorTier": "Diamond", "armorTierRank": 6,
                "weapon": "Diamond Sword",
                "equipment": [ { "slot": "Head", "itemKey": "minecraft:diamond_helmet",
                                 "name": "Diamond Helmet", "tier": "Diamond", "tierRank": 6,
                                 "armorPoints": 3, "enchanted": true, "durabilityPct": 88 } ],
                "x": 148, "y": 64, "z": -312 } ],
  "posts":  [ { "id": 130640999, "name": "Guard Tower", "type": "minecolonies:guardtower",
                "blockId": "minecolonies:blockhutguardtower", "level": 2,
                "assigned": 1, "capacity": 2, "x": 170, "y": 66, "z": -300 } ],
  "events": [] }
```

`GET /api/colony/{id}/map`
```json
{ "available": true, "ready": true, "unavailableReason": null,
  "dimension": "minecraft:overworld", "centerX": 120, "centerY": 64, "centerZ": -340,
  "minX": 16, "minZ": -448, "width": 320, "height": 288,
  "version": 7, "renderedAt": 1761500000000, "chunksMapped": 331, "chunksTotal": 360 }
```

The image lives at `/map/{colonyId}.png` and is exactly `width × height` pixels, with its
top-left pixel at world `(minX, minZ)` — so a marker for anything at block `(x, z)` belongs at
pixel `(x - minX, z - minZ)`. Pixels for chunks that have not been drawn are **transparent**.
`version` bumps on every redraw and must be used as a cache-buster; poll this document while
`chunksMapped < chunksTotal` to watch the map fill in. `ready` is false until the first chunk has
been drawn, and `/map/{colonyId}.png` answers `404` until then.

Key fields:
- `buildings[].kind` — `"building"` or `"decoration"`.
- `required[].material` — combined Domum Ornamentum material name, else `null`.
- `*.components[]` — per-material tooltip lines for Domum blocks (`label`, `material`,
  `itemKey`); empty for ordinary items.
- `required[].deliverable` — the warehouse can cover the shortfall.
- `*.maxStackSize` — that item's stack limit, so counts can be shown as stacks plus a
  remainder (`140` at `64` becomes `2 stacks + 12`).
- `*.craftable` — a colony worker knows a recipe that produces this item.
- `guards[].armorTierRank` — rising with material quality, so the roster sorts without
  needing to know the tier names.
- `research.branches[].researches[].state` — `COMPLETED` / `IN_PROGRESS` / `NOT_STARTED`.
- `citizens[].skills[].role` — `"primary"` / `"secondary"` for the citizen's job skills, else
  `null`.
- `citizens[].modifiers[].factor` — happiness multiplier; `> 1` is a perk, `< 1` a grievance.

---

## Configuration

Config file (generated on first server start):
`<server-directory>/config/colonyweb-common.toml`

| Key                         | Default   | Meaning                                                        |
|-----------------------------|-----------|----------------------------------------------------------------|
| `httpPort`                  | `8723`    | Web server port. **Must differ from the Minecraft game port.** |
| `bindAddress`               | `0.0.0.0` | Bind interface (`127.0.0.1` for local only, or an allocated IP).|
| `refreshIntervalSeconds`    | `3`       | Re-scan + SSE push cadence.                                    |
| `autoDownloadVanillaAssets` | `true`    | Download vanilla client textures on first run.                 |
| `mapEnabled`                | `true`    | Show the colony map tab.                                       |
| `mapRadius`                 | `256`     | How far the map reaches from the colony centre, in blocks (64–512). |
| `publicHost`                | `""`      | Host shown in the `/colonyweb` link (blank = auto-detect).     |
| `authEnabled`               | `true`    | Require a pairing-code sign-in. **Off makes the dashboard fully public.** |
| `sessionDays`               | `30`      | How long a browser stays signed in (1–365).                    |
| `loginCodeMinutes`          | `10`      | How long a pairing code stays valid (1–1440).                  |

Example:
```toml
httpPort = 8723
bindAddress = "0.0.0.0"
refreshIntervalSeconds = 3
autoDownloadVanillaAssets = true
mapEnabled = true
mapRadius = 256
publicHost = ""
authEnabled = true
sessionDays = 30
loginCodeMinutes = 10
```

Runtime state lives in `<server-directory>/colonyweb/`:

```
colonyweb/
  auth.json            # users, mirrored colonies, grants, session token hashes
  textures/            # rendered PNG icon cache
  assets/              # extracted vanilla client textures
```

---

## In-Game Commands

| Command                                  | Level | Description                                                 |
|------------------------------------------|:-----:|-------------------------------------------------------------|
| `/colonyweb`                             |  any  | Prints a **clickable** dashboard link.                       |
| `/colonyweb port`                        |  any  | Prints the configured port.                                  |
| `/colonyweb sync`                        |  any  | Mirrors your colonies and issues a **click-to-copy** code.   |
| `/colonyweb status`                      |  op   | Running state, port, viewers, MineColonies detection, sessions, pending codes. |
| `/colonyweb sync <player>`               |  op   | Issues a code for someone else (whispered to them if online).|
| `/colonyweb access grant <player> <id>`  |  op   | Grants one extra colony; survives later re-syncs.            |
| `/colonyweb access revoke <player> <id>` |  op   | Removes that grant (colony **membership** still applies).    |
| `/colonyweb access list <player>`        |  op   | Shows membership, grants and open session count.             |
| `/colonyweb logout <player>`             |  op   | Invalidates all of that player's browser sessions.           |

---

## Building From Source

Requirements: **JDK 17** (Forge 1.20.1 requires it to run the game).

### Local development in VS Code

Open **Terminal → Run Task** and choose one of these committed tasks:

- **Minecraft: Run Client (Singleplayer)** — starts Minecraft; create/open a singleplayer world
  to run the mod's server side without a separate dedicated server.
- **Minecraft: Run Dedicated Server** — starts a local headless server.
- **Minecraft: Run Client + Server** — starts both in separate terminals so the client can join
  `localhost`.

The client and server use separate `run-client/` and `run-server/` directories, so they can run
at the same time without sharing worlds or configuration. Press **Ctrl+Shift+B** for the default
singleplayer client task. Stop a session with the trash-can button in its terminal.

MineColonies, Structurize, BlockUI, Multi-Piston, and Domum Ornamentum are downloaded into the
development runtime automatically by Gradle; no manual jar copying is needed. Additional optional
mods can be placed in `run-client/mods/`, `run-server/mods/`, or both. The dedicated server's first
start creates `run-server/eula.txt` and exits; read it, set `eula=true` if you accept the Minecraft
EULA, then run the server task again.

The same sessions are available without VS Code:

```powershell
# from the project root
./gradlew build       # compiles the mod → build/libs/colonyweb-1.0.0-BETA.jar
./gradlew runClient   # Minecraft client + integrated singleplayer server
./gradlew runServer   # dedicated server
```

Then open `http://localhost:8723/` (or the configured port), and run `/colonyweb sync` in-game
for a sign-in code.

> Runtime integration is reflection-based, so **MineColonies is not required to compile**.
> Optional compile-time API access can be enabled by uncommenting the `compileOnly` deps in
> `build.gradle` (the LDTTeam maven repo is already configured).

---

## Installing on a Server

1. Build the jar (or grab it from `build/libs/`).
2. Place `colonyweb-1.0.0-BETA.jar` in the server's `mods/` folder, **alongside MineColonies
   and Domum Ornamentum**.
3. Start the server once to generate `config/colonyweb-common.toml`, adjust the port/bind if
   needed, and restart.
4. Open `http://<host>:<httpPort>/` or run `/colonyweb` for the link, then `/colonyweb sync`
   for a code.

> **Upgrading from the pre-rename builds?** The mod id changed from `untitled1` to `colonyweb`.
> Delete the old jar, and note that `config/untitled1-common.toml` and the
> `colonyweb-cache/` directory are no longer read — settings fall back to defaults in the new
> `config/colonyweb-common.toml`, and icons re-render into `<server>/colonyweb/textures/`.

---

## Hosting & Networking Notes

- **Use a different port than the Minecraft game port.** If `httpPort` collides with the game
  port, the HTTP server can't bind and browsers get "connection reset".
- `bindAddress` must be an address the machine actually owns — use `0.0.0.0` (all interfaces),
  `127.0.0.1` (local only), or the specific allocated IP. You **cannot** bind to your public
  IP directly (it lives on the router/NAT).
- You never browse to `0.0.0.0` — connect via `localhost`, the LAN IP, or the public IP.
- For internet access, **forward the TCP port** and allow it through the firewall.
- On managed hosts (Pterodactyl, etc.), use a **second allocated port** from your panel; some
  panels require binding to the allocation's IP rather than `0.0.0.0`.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| `BindException: Cannot assign requested address` | `bindAddress` isn't a local interface — use `0.0.0.0` and put the public IP in `publicHost`. |
| Port reachable but "connection reset" / page won't load | `httpPort` collides with the Minecraft game port — pick a different, allocated port. |
| Dashboard loads but "0 buildings / no warehouse" | Check `logs/latest.log` for `[ColonyWeb] colony … buildings=… warehouse=…`. The mod logs per-colony counts each scan. |
| "No colonies to show" after signing in | You aren't a member of any colony yet, or you joined one after syncing. Run `/colonyweb sync` again. |
| The code is rejected | Codes are single-use and expire after `loginCodeMinutes`. Run `/colonyweb sync` for a fresh one. Casing and the dash don't matter. |
| Signed out unexpectedly | The session expired (`sessionDays`), an operator ran `/colonyweb logout`, or `auth.json` was deleted. |
| Icons show a magenta/black checker | Texture couldn't be resolved (vanilla jar not downloaded, or a modded texture path differs). Vanilla icons require `autoDownloadVanillaAssets = true` and internet on first run. |
| Icons are still flat after upgrading | Cached PNGs are discarded automatically when the renderer changes, on the first start after the upgrade. If they persist, delete `<server>/colonyweb/textures/` and restart. |
| The map stays mostly blank | The map only draws chunks the server currently has loaded. Fly or walk the area once (or enable chunk loading around the colony) and it fills in — drawn chunks are then kept even after they unload. |
| The map never gets past "Mapping n%" | Those chunks have never been loaded while the tab was open. The percentage counts the whole square the map covers, so a colony at the edge of explored terrain will not reach 100%. |
| The map tab is missing | `mapEnabled = false` in `config/colonyweb-common.toml`. |
| A block renders as a flat swatch | Its model had no parseable geometry (e.g. a runtime-generated model, or a `builtin/entity` one like chests and beds), so the mod fell back to a texture. Everything else still works. |
| `/colonyweb` link not clickable | Fixed — the link is a proper `OPEN_URL` chat component. |

The dashboard logs a line per scan so issues are visible without debug logging:
```
[ColonyWeb] colony 1 ('Springfield'): buildings=14 workOrders=8 warehouse=true (37 stacks)
```

---

## Tech Stack

- **Java 17**, **Forge 47.4.0**, **Minecraft 1.20.1**.
- `com.sun.net.httpserver.HttpServer` (JDK built-in) for HTTP + SSE — no external HTTP lib.
- **Gson** (already on the MC classpath) for JSON.
- Vanilla asset acquisition via the Mojang version manifest + client jar extraction.
- **Reflection** for all MineColonies / Domum Ornamentum access (fail-soft, cached).
- **Alpine.js** (vendored) for the reactive front-end, split into ES modules and HTML partials —
  no bundler, no CDN dependency.
- **Tailwind CSS** via the standalone `tailwindcss.exe`; the visual language lives in a
  component layer so the partials stay readable.

---

## Compatibility & Versions

- **Minecraft:** 1.20.1
- **Forge:** 47.4.0
- **MineColonies:** 1.20.1 branch (latest build — reflection-based, so version-independent at runtime)
- **Domum Ornamentum:** 1.20.1 (optional; enables textured-block material icons)

Both MineColonies and Domum Ornamentum are declared as **optional** dependencies
(`mandatory = false`, `AFTER`) and are bound at runtime via reflection.

---

## Security

The dashboard is **read-only** — nothing it exposes can change the world.

With `authEnabled = true` (the default):

- Every data route requires a session; the only public routes are the static shell and
  `/auth/*`.
- Sessions come from a **single-use pairing code** that can only be obtained in-game, so having
  a Minecraft account on the server is the credential. There is no password to leak or reset.
- The session cookie is `HttpOnly; SameSite=Lax`. Only a **SHA-256 hash** of each token is
  written to `auth.json`; pairing codes are never persisted at all.
- Players are scoped to the colonies they belong to. Per-colony routes return an identical
  `403` for "not yours" and "doesn't exist", so the API can't be used to enumerate colonies.
- `/colonyweb logout <player>` revokes every session that player has open.

Remaining considerations:

- Traffic is **plain HTTP**. On an untrusted network, put it behind a TLS-terminating reverse
  proxy — a session cookie sent in the clear can be captured.
- Operators see every colony on the server.
- With `authEnabled = false` the dashboard is fully public to anyone who can reach the port.
  Only do that on a firewalled or LAN-only address.

---

## License

All Rights Reserved (see `gradle.properties` → `mod_license`). Update as desired.
