/**
 * A stand-in for the mod's HTTP server, so the dashboard can be developed without Minecraft.
 *
 * Every response mirrors the real endpoint's shape — see `data.cjs` for the payloads and the
 * Kotlin DTOs they follow.
 */
const express = require('express')
const cors = require('cors')
const { encodePng } = require('./png.cjs')
const data = require('./data.cjs')

const app = express()
app.use(cors())
app.use(express.json())
app.use(express.urlencoded({ extended: true }))

const USER = {
  uuid: '00000000-0000-0000-0000-000000000001',
  name: 'Steve',
  colonies: [1, 2],
  granted: [],
  admin: false,
  syncedAt: Date.now(),
}

let authenticated = false

// ---- Auth ----

app.get('/auth/me', (_req, res) => {
  if (authenticated) res.json({ authenticated: true, authEnabled: true, user: USER })
  else res.json({ authenticated: false, authEnabled: true })
})

app.post('/auth/login', (req, res) => {
  const code = (req.body && req.body.code) || ''
  if (code.replace(/[^A-Za-z0-9]/g, '').length >= 8) {
    authenticated = true
    res.json({ authenticated: true, user: USER })
  } else {
    res.status(403).json({ error: 'That code was not accepted.' })
  }
})

app.post('/auth/logout', (_req, res) => {
  authenticated = false
  res.json({})
})

// ---- Colony data ----

/** Every colony endpoint answers 401 once the session has gone. */
function requireAuth(_req, res, next) {
  if (!authenticated) return res.status(401).json({ error: 'Not signed in' })
  next()
}

app.get('/api/colonies', requireAuth, (_req, res) => res.json(data.COLONIES))

app.get('/api/colony/:id', requireAuth, (req, res) => {
  res.json(data.snapshot(Number(req.params.id)))
})

app.get('/api/colony/:id/citizens', requireAuth, (req, res) => {
  res.json(Number(req.params.id) === 1 ? data.CITIZENS : [])
})

app.get('/api/colony/:id/research', requireAuth, (req, res) => {
  if (Number(req.params.id) !== 1) {
    return res.json({ available: false, branches: [], completed: 0, inProgress: 0, total: 0 })
  }
  res.json(data.RESEARCH)
})

app.get('/api/colony/:id/combat', requireAuth, (req, res) => {
  if (Number(req.params.id) !== 1) {
    return res.json({
      raidsPossible: false, underAttack: false, nightsSinceRaid: 0, raidLevel: 0,
      spiesEnabled: false, guardCount: 0, guardCapacity: 0, averageGuardLevel: 0,
      averageHealthPct: 0, graves: 0, guards: [], posts: [], events: [],
    })
  }
  res.json(data.COMBAT)
})

app.get('/api/colony/:id/citizen/:cid', requireAuth, (req, res) => {
  const citizen = data.CITIZENS.find(c => c.id === Number(req.params.cid))
  if (!citizen) return res.status(404).json({ error: 'Not found' })
  res.json({ citizen, inventory: data.INVENTORY, equipment: data.EQUIPMENT })
})

// ---- Map ----

const MAP = { minX: -160, minZ: -160, width: 320, height: 320, centerX: 0, centerY: 70, centerZ: 0 }

/** The server draws a few chunks per scan, so the mock fills in over the first minute. */
const startedAt = Date.now()
const CHUNKS_TOTAL = 400
function chunksMapped() {
  return Math.min(CHUNKS_TOTAL, Math.floor((Date.now() - startedAt) / 150) + 40)
}

app.get('/api/colony/:id/map', requireAuth, (req, res) => {
  if (Number(req.params.id) !== 1) {
    return res.json({
      available: false, ready: false, unavailableReason: 'No town hall has been placed yet.',
      dimension: '', centerX: 0, centerY: 0, centerZ: 0, minX: 0, minZ: 0, width: 0, height: 0,
      version: 0, renderedAt: 0, chunksMapped: 0, chunksTotal: 0,
    })
  }
  const mapped = chunksMapped()
  res.json({
    available: true,
    ready: true,
    unavailableReason: null,
    dimension: 'minecraft:overworld',
    ...MAP,
    // Bumped as more chunks land, so the browser re-fetches the image.
    version: Math.floor(mapped / 20),
    renderedAt: Date.now(),
    chunksMapped: mapped,
    chunksTotal: CHUNKS_TOTAL,
  })
})

/** A cheap procedural surface, so the map tab has something to pan around. */
function renderTerrain(mappedFraction) {
  const { width, height } = MAP
  const rgb = Buffer.alloc(width * height * 3)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 3
      // Undrawn chunks stay blank, closest to the centre first.
      const dist = Math.hypot(x - width / 2, y - height / 2) / (width / 2)
      if (dist > mappedFraction) {
        rgb[i] = 18; rgb[i + 1] = 23; rgb[i + 2] = 34
        continue
      }
      const n = Math.sin(x / 17) * Math.cos(y / 23) + Math.sin((x + y) / 41)
      if (n < -0.9) { rgb[i] = 56; rgb[i + 1] = 96; rgb[i + 2] = 160 }      // water
      else if (n > 1.1) { rgb[i] = 120; rgb[i + 1] = 118; rgb[i + 2] = 112 } // stone
      else {
        const g = 92 + Math.round(n * 22)
        rgb[i] = 58; rgb[i + 1] = g; rgb[i + 2] = 52
      }
    }
  }
  return encodePng(width, height, rgb)
}

app.get(/^\/map\/(\d+)\.png$/, requireAuth, (req, res) => {
  const png = renderTerrain(chunksMapped() / CHUNKS_TOTAL)
  res.set('Content-Type', 'image/png')
  res.set('Cache-Control', 'public, max-age=30')
  res.send(png)
})

// ---- Textures ----

/** A flat 16x16 swatch keyed off the item id, so different items look different. */
const textureCache = new Map()
app.get(/^\/textures\/(.+)\.png$/, requireAuth, (req, res) => {
  const key = decodeURIComponent(req.params[0])
  if (!textureCache.has(key)) {
    let hash = 0
    for (let i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) >>> 0
    const r = 70 + (hash & 0x7f)
    const g = 70 + ((hash >> 8) & 0x7f)
    const b = 70 + ((hash >> 16) & 0x7f)
    const size = 16
    const rgb = Buffer.alloc(size * size * 3)
    for (let p = 0; p < size * size; p++) {
      const edge = p < size || p >= size * (size - 1) || p % size === 0 || p % size === size - 1
      rgb[p * 3] = edge ? r >> 1 : r
      rgb[p * 3 + 1] = edge ? g >> 1 : g
      rgb[p * 3 + 2] = edge ? b >> 1 : b
    }
    textureCache.set(key, encodePng(size, size, rgb))
  }
  res.set('Content-Type', 'image/png')
  res.set('Cache-Control', 'public, max-age=3600')
  res.send(textureCache.get(key))
})

// ---- Live updates ----

app.get('/events', (req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  })
  res.write('retry: 2000\n\n')

  const timer = setInterval(() => {
    res.write(`event: update\ndata: ${JSON.stringify({ type: 'colony', id: 1 })}\n\n`)
  }, 10000)

  req.on('close', () => clearInterval(timer))
})

const PORT = 3001
app.listen(PORT, () => {
  console.log(`Mock ColonyWeb API on http://localhost:${PORT}`)
  console.log('Sign in with any 8-character code, e.g. ABCD-1234')
})
