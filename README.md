# ColonyWeb

A **server-side-only** Minecraft mod for **1.20.1 (Forge)** and **1.21.1 (NeoForge)** that adds a live web dashboard for [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) colonies. Open it in any browser to see builder progress, resource needs, citizens, research, defence stats, and warehouse stock — all with live updates via server-sent events.

Players sign in with a pairing code from `/colonyweb sync` and only see colonies they belong to. The mod does **not** need to be installed on the client.

---

## Features

| Tab | What it shows |
|---|---|
| **Overview** | Stat tiles (citizens, happiness, buildings, defence, warehouse, research, builders), work orders sorted by activity, builder roster |
| **Map** | Pannable/zoomable top-down colony map with building pins and citizen dots, auto-fit and centre controls |
| **Buildings** | Card grid with search, sort (status/name/progress/level), in-progress and decoration filters, per-building resource detail modal |
| **Citizens** | Roster with job filter and search, skill breakdown, health and happiness meters, inventory and equipment modals |
| **Research** | University branches with progress bars, state pills, item cost icons |
| **Combat** | Guard post staffing (ok/deliver/missing), guard health, raid banner with nights since last raid |
| **Warehouse** | Aggregated colony stock with search and sort (by count or alphabetically) |

All tabs update in real time — no manual refresh needed.

---

## Dependencies

| Mod | Required | Notes |
|---|---|---|
| [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) | Yes | The mod is useless without it — the dashboard has no colonies to show. |
| [Domum Ornamentum](https://www.curseforge.com/minecraft/mc-mods/domum-ornamentum) | Yes | Dynamically textured decorative blocks used by MineColonies builds. The dashboard shows their icons when installed. |
| [KotlinForForge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge) | Yes | Provides the Kotlin standard library at runtime. Use the NeoForge variant for 1.21.1 and the Forge variant for 1.20.1. |

---

## Installation

1. Drop the ColonyWeb jar **and its dependencies** into your server's `mods/` folder.
2. Start the server — the config file `config/colonyweb-common.toml` is generated automatically.
3. Open `http://your-server-ip:8723` in any browser.
4. Run `/colonyweb sync` in-game and enter the pairing code on the web page.

---

## Configuration

File: `config/colonyweb-common.toml`

| Option | Default | Description |
|---|---|---|
| `port` | `8723` | HTTP server port |
| `bindAddress` | `0.0.0.0` | Bind address (set to `127.0.0.1` with a reverse proxy for production) |
| `authEnabled` | `true` | Require pairing-code authentication |
| `refreshIntervalSeconds` | `3` | How often colony data is scanned |
| `idleTimeoutMinutes` | `20` | Session idle timeout |
| `maxViewers` | `10` | Max concurrent SSE connections |

---

## In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/colonyweb sync` | Any | Generate a one-shot pairing code |
| `/colonyweb access add <player>` | OP | Grant web access to a player |
| `/colonyweb access remove <player>` | OP | Revoke web access |
| `/colonyweb access list` | OP | List who has access |
| `/colonyweb op <player>` | OP | Grant operator status on the web dashboard |
| `/colonyweb deop <player>` | OP | Remove operator status |
| `/colonyweb logout` | Any | Invalidate your own web sessions |
| `/colonyweb status` | OP | Show auth and session stats |

## Signing In

1. Run `/colonyweb sync` in-game. A short code appears in chat (e.g. `3B7X-K2L9`).
2. Open your browser to the server's dashboard URL.
3. Type the code into the pairing screen. Codes are case-insensitive.

The code is single-use and expires after 20 minutes by default.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Page won't load / connection refused | Server port not reachable — check firewall and port forwarding |
| "No colonies to show" | Player hasn't joined a MineColonies colony yet, or the pairing code was for a different player |
| Pairing code not accepted | Code expired (20 min limit) or already used — run `/colonyweb sync` again |
| Dashboard loads but all tabs are empty | MineColonies not installed or not loaded on the server |
| Images / textures missing | First load downloads vanilla assets — may take a minute on slow connections |
| SSE disconnects frequently | Check `idleTimeoutMinutes` and network proxy settings |

See the server log (`latest.log`) for lines prefixed with `[ColonyWeb]`.

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
