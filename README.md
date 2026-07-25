# Colony Web Dashboard

A **server-side-only** Minecraft Forge mod (MC **1.20.1**, Forge **47.4.0**) that expands on
[**MineColonies**](https://www.curseforge.com/minecraft/mc-mods/minecolonies) by exposing a
**live web dashboard**, served by the Minecraft server itself. Open it in any browser to see —
per colony — which builder is working on which building, exactly what resources each
build/upgrade needs, what the hut already has, and what the warehouse can provide, all with
live item/block icons (including [**Domum Ornamentum**](https://www.curseforge.com/minecraft/mc-mods/domum-ornamentum)
textured blocks) and no manual refresh.

The mod does **not** need to be installed on the client — only on the server.

---

## Table of Contents

- [Features](#features)
- [Screenshots / UI Overview](#screenshots--ui-overview)
- [How It Works](#how-it-works)
- [Project Structure](#project-structure)
- [HTTP API & Endpoints](#http-api--endpoints)
- [Data Model](#data-model)
- [Configuration](#configuration)
- [In-Game Command](#in-game-command)
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
- **Per-colony view** with a colony selector dropdown (switch between all colonies on the server).
- **Live updates via SSE** (Server-Sent Events) — the page re-renders automatically when
  builders progress, resources change, or work orders start/finish. No manual refresh.
- **Connection status indicator** (live / reconnecting) with an animated dot.

### Builders panel
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
- Full **resource table** with columns: icon, item name (+ Domum material), **needed**,
  **in hut**, **in warehouse**, and a color-coded **status pill**.
- **Per-building item search** to quickly find a specific resource.
- Resources sorted **missing-first** so shortfalls are obvious.
- Progress bar and "built by" details.

### Search, filters & sorting
- **Global search** across buildings — matches building names **and** the items/materials they
  require.
- **"In progress only"** toggle to show just what's actively being built/upgraded.
- **Sort** by: Status (missing first), Progress, Name, or Level.
- Dedicated **warehouse search**.

### Resource-scroll parity
- The required-resources data mirrors what MineColonies' in-game **resource scroll** shows —
  read directly from the assigned builder's building.
- Status is color-coded:
  - 🟢 **enough** — the hut already has it.
  - 🟡 **deliverable** — the warehouse can cover the shortfall.
  - 🔴 **missing** — not available.

### Warehouse overview
- Aggregated stock across all colony warehouse(s), searchable and sorted by count.

### Server-side texture pipeline
- Item/block **PNG icons generated on the server** and served to the browser.
- A dedicated server has no client textures, so the mod **downloads & caches the vanilla client
  jar** for the running MC version (once) to source vanilla icons.
- Modded textures (MineColonies, Domum Ornamentum, etc.) are read from the installed mod jars.
- **Domum Ornamentum support**: resolves the block's **material components** from stack NBT
  (`BlockEntityTag → textureData`), shows the **material name** (e.g. "Beige Bricks"), and uses
  the **material's texture** as the icon. Each textured variant caches to its own PNG.
- Animated textures are cropped to their first frame; unresolved icons fall back to a generated
  magenta/black placeholder so the UI always lays out.
- **Two-tier cache** (in-memory + disk under `<server>/colonyweb-cache/`).

### Reflection-based soft dependency
- Integrates with MineColonies / Domum Ornamentum **purely through reflection at runtime**.
- The mod **compiles and loads without them present** and never crashes if their API shifts —
  every lookup fails soft and logs which lookups miss.

### In-game command
- `/colonyweb` prints a **clickable** dashboard link in chat, plus `status` and `port`
  sub-commands.

---

## Screenshots / UI Overview

```
┌───────────────────────────────────────────────────────────────────────────┐
│ ⛏ Colony Dashboard        [ Colony ▾ ]                       ● live        │
├───────────────────────────────────────────────────────────────────────────┤
│ 🔍 Search buildings, items or materials…   ☐ In progress only  Sort [▾]    │
├───────────────────────────────────────────────────────────────────────────┤
│ BUILDERS                                                                    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                         │
│  │ Bob          │ │ Alice        │ │ …            │                         │
│  │ Barracks →4  │ │ Fisher's →2  │ │              │                         │
│  │ ▓▓▓▓▓░░░░ 55%│ │ ▓▓░░░░░░ 20% │ │              │                         │
│  └──────────────┘ └──────────────┘ └──────────────┘                         │
├───────────────────────────────────────────────────────────────────────────┤
│ BUILDINGS & DECORATIONS                                                     │
│  ┌──────────────────────┐ ┌──────────────────────┐  (click → detail modal) │
│  │ Barracks   lvl 3 → 4 │ │ Paved Road  [Deco]   │                          │
│  │ being built by Bob   │ │ in progress          │                          │
│  │ ▓▓▓▓▓░░░  3 missing   │ │ ▓▓░░░░░  1 missing    │                          │
│  │ 🧱🪵🟦🟫 +12          │ │ 🧱🪵                  │                          │
│  └──────────────────────┘ └──────────────────────┘                          │
├───────────────────────────────────────────────────────────────────────────┤
│ WAREHOUSE   🔍 …                                                            │
│  🧱 Beige Bricks Stairs   x200    🪵 Oak Planks   x512   …                   │
└───────────────────────────────────────────────────────────────────────────┘
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

---

## Project Structure

```
src/main/java/DahRealPanda/plugins/untitled1/
  Untitled1.java                 # @Mod entry — server lifecycle + command wiring
  Config.java                    # server config (port, bind, refresh, assets, host)
  ColonyWebService.java          # owns web server, scheduler, SSE, change detection

  web/
    WebServer.java               # HttpServer bootstrap, routing, lifecycle
    SseBroadcaster.java          # tracks SSE clients, pushes events, heartbeats
    JsonUtil.java                # Gson instance + HTTP response helpers
    handlers/
      ApiHandler.java            # /api/colonies, /api/colony/{id}
      EventsHandler.java         # /events (SSE)
      TextureHandler.java        # /textures/{key}.png
      StaticHandler.java         # serves webroot/ assets

  colony/
    ColonyDataProvider.java      # enumerate colonies + build snapshots (reflection)
    MineColoniesReflect.java     # cached reflection handles + null-safety
    ColonyCache.java             # thread-safe latest-scan holder
    model/                       # DTOs: ColonySummary, ColonySnapshot, BuildingInfo,
                                 #       WorkOrderInfo, BuilderInfo, ResourceEntry, ItemRef

  texture/
    TextureService.java          # itemKey/stack -> PNG bytes (memory + disk cache)
    ModelResolver.java           # read item/block model JSON from jars / vanilla cache
    VanillaAssetProvider.java    # download + cache vanilla client jar assets
    DomumOrnamentumResolver.java # resolve DO material components -> texture key + name
    PngCache.java                # keyed cache -> byte[] (disk-backed)

  command/
    ColonyWebCommand.java        # /colonyweb (clickable link, status, port)

src/main/resources/
  META-INF/mods.toml             # server-side; optional deps on minecolonies & domum_ornamentum
  pack.mcmeta
  webroot/
    index.html                   # dashboard shell (Alpine.js)
    app.js                       # Alpine component: data load, SSE, search/sort/filter
    style.css                    # dark theme, cards, modal
    vendor/alpine.min.js         # bundled Alpine.js (no CDN dependency)
```

---

## HTTP API & Endpoints

| Method | Path                    | Purpose                                             |
|--------|-------------------------|-----------------------------------------------------|
| GET    | `/`                     | dashboard `index.html`                              |
| GET    | `/app.js`, `/style.css` | static front-end assets                             |
| GET    | `/vendor/alpine.min.js` | bundled Alpine.js                                   |
| GET    | `/api/colonies`         | colony list for the selector                        |
| GET    | `/api/colony/{id}`      | full snapshot for one colony                        |
| GET    | `/events`               | **SSE** stream; emits `colonies` and `colony` events |
| GET    | `/textures/{key}.png`   | PNG icon for an item/block/DO stack                 |

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
        { "itemKey": "domum_ornamentum:vanilla_stairs_compat#a1b2c3d4",
          "name": "Beige Bricks Stairs", "material": "Beige Bricks",
          "needed": 18, "inHut": 0, "inWarehouse": 64, "deliverable": true }
      ]
    }
  ],
  "warehouse": {
    "present": true,
    "stacks": [ { "itemKey": "minecraft:oak_planks", "name": "Oak Planks",
                  "material": null, "count": 512 } ]
  }
}
```

Key fields:
- `buildings[].kind` — `"building"` or `"decoration"`.
- `required[].material` — Domum Ornamentum material name (e.g. "Beige Bricks"), else `null`.
- `required[].deliverable` — the warehouse can cover the shortfall.

---

## Configuration

Config file (generated on first server start):
`<server-directory>/config/untitled1-common.toml`

| Key                         | Default   | Meaning                                                        |
|-----------------------------|-----------|----------------------------------------------------------------|
| `httpPort`                  | `8723`    | Web server port. **Must differ from the Minecraft game port.** |
| `bindAddress`               | `0.0.0.0` | Bind interface (`127.0.0.1` for local only, or an allocated IP).|
| `refreshIntervalSeconds`    | `3`       | Re-scan + SSE push cadence.                                    |
| `autoDownloadVanillaAssets` | `true`    | Download vanilla client textures on first run.                 |
| `publicHost`                | `""`      | Host shown in the `/colonyweb` link (blank = auto-detect).     |

Example:
```toml
httpPort = 8723
bindAddress = "0.0.0.0"
refreshIntervalSeconds = 3
autoDownloadVanillaAssets = true
publicHost = ""
```

---

## In-Game Command

`/colonyweb` (permission level 0 — any player or console):

| Command             | Description                                                        |
|---------------------|-------------------------------------------------------------------|
| `/colonyweb`        | Prints a **clickable** dashboard link.                            |
| `/colonyweb status` | Shows running state, port, connected SSE clients, MineColonies detection. |
| `/colonyweb port`   | Prints the configured port.                                       |

---

## Building From Source

Requirements: **JDK 17** (Forge 1.20.1 requires it to run the game).

```powershell
# from the project root
./gradlew build           # compiles the mod → build/libs/untitled1-1.0-SNAPSHOT.jar
./gradlew runServer       # dev server (drop MineColonies + Domum Ornamentum jars in run/mods)
```

Then open `http://localhost:8723/` (or the configured port). Use `/colonyweb` in-game for the link.

> Runtime integration is reflection-based, so **MineColonies is not required to compile**.
> Optional compile-time API access can be enabled by uncommenting the `compileOnly` deps in
> `build.gradle` (the LDTTeam maven repo is already configured).

---

## Installing on a Server

1. Build the jar (or grab it from `build/libs/`).
2. Place `untitled1-1.0-SNAPSHOT.jar` in the server's `mods/` folder, **alongside MineColonies
   and Domum Ornamentum**.
3. Start the server once to generate `config/untitled1-common.toml`, adjust the port/bind if
   needed, and restart.
4. Open `http://<host>:<httpPort>/` or run `/colonyweb` for the link.

> Replacing an existing jar? The filename doesn't change between builds — delete the old jar
> first so the loader doesn't keep the stale one.

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
| Icons show a magenta/black checker | Texture couldn't be resolved (vanilla jar not downloaded, or a modded texture path differs). Vanilla icons require `autoDownloadVanillaAssets = true` and internet on first run. |
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
- **Alpine.js** (vendored) for the reactive front-end — no build step, no CDN dependency.

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

The dashboard is an **unauthenticated, read-only** web page. Anyone who can reach the port can
view colony data. For public servers:

- Bind to `127.0.0.1` and put it behind an authenticated reverse proxy, **or**
- Firewall the port to trusted IPs.

Do not expose it publicly if colony data is sensitive.

---

## License

All Rights Reserved (see `gradle.properties` → `mod_license`). Update as desired.
