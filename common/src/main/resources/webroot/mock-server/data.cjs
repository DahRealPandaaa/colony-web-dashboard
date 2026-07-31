/**
 * Mock colony data, shaped exactly like the DTOs the mod serialises out of
 * `common/src/main/kotlin/.../colony/model/`. Keep the two in step: the dashboard is written
 * against these field names.
 */

/** Every item payload extends ItemInfo, so build them through one helper. */
function item(itemKey, name, extra = {}) {
  return {
    itemKey,
    name,
    material: null,
    domum: false,
    craftedIn: null,
    craftable: false,
    components: [],
    ...extra,
  }
}

function resource(itemKey, name, needed, inHut, inWarehouse, extra = {}) {
  return {
    ...item(itemKey, name, extra),
    needed,
    maxStackSize: extra.maxStackSize ?? 64,
    inHut,
    inWarehouse,
    deliverable: inHut < needed && inWarehouse >= needed - inHut,
  }
}

const BUILDINGS = [
  {
    id: 1, name: "Builder's Hut", type: 'minecolonies:builder', kind: 'building',
    blockId: 'minecolonies:blockhutbuilder', level: 3, x: 120, y: 68, z: -80,
    beingBuilt: false, workOrderId: -1, required: [],
  },
  {
    id: 2, name: 'Town Hall', type: 'minecolonies:townhall', kind: 'building',
    blockId: 'minecolonies:blockhuttownhall', level: 4, x: 0, y: 70, z: 0,
    beingBuilt: false, workOrderId: -1, required: [],
  },
  {
    id: 3, name: 'Warehouse', type: 'minecolonies:warehouse', kind: 'building',
    blockId: 'minecolonies:blockhutwarehouse', level: 2, x: 50, y: 69, z: 30,
    beingBuilt: false, workOrderId: -1, required: [],
  },
  {
    id: 4, name: "Farmer's Hut", type: 'minecolonies:farmer', kind: 'building',
    blockId: 'minecolonies:blockhutfarmer', level: 2, x: -60, y: 67, z: 40,
    beingBuilt: true, workOrderId: 1,
    required: [
      resource('minecraft:oak_planks', 'Oak Planks', 192, 64, 448),
      resource('minecraft:cobblestone', 'Cobblestone', 128, 128, 512),
      resource('minecraft:glass_pane', 'Glass Pane', 24, 0, 6, { craftable: true, craftedIn: 'Glassblower' }),
      resource('minecraft:iron_ingot', 'Iron Ingot', 12, 0, 96),
      resource('domum_ornamentum:brick_extra', 'Brick Extra', 48, 0, 0, {
        domum: true,
        material: 'Brick, Oak',
        craftedIn: 'Architects Cutter',
        craftable: true,
        components: [
          { id: 'domum_ornamentum:shingle_face', label: 'Main Material', material: 'Brick', itemKey: 'minecraft:bricks' },
          { id: 'domum_ornamentum:shingle_support', label: 'Supported by', material: 'Oak', itemKey: 'minecraft:oak_planks' },
        ],
      }),
    ],
  },
  {
    id: 5, name: 'Guard Tower', type: 'minecolonies:guardtower', kind: 'building',
    blockId: 'minecolonies:blockhutguardtower', level: 1, x: 90, y: 71, z: -30,
    beingBuilt: false, workOrderId: 3, required: [],
  },
  {
    id: 6, name: 'Tavern', type: 'minecolonies:tavern', kind: 'building',
    blockId: 'minecolonies:blockhuttavern', level: 3, x: 20, y: 70, z: -50,
    beingBuilt: false, workOrderId: -1, required: [],
  },
  {
    id: 7, name: "Enchanter's Tower", type: 'minecolonies:enchanter', kind: 'building',
    blockId: 'minecolonies:blockhutenchanter', level: 0, x: -30, y: 68, z: -70,
    beingBuilt: true, workOrderId: 2,
    required: [
      resource('minecraft:bookshelf', 'Bookshelf', 45, 12, 8),
      resource('minecraft:obsidian', 'Obsidian', 14, 0, 0),
      resource('minecraft:oak_log', 'Oak Log', 64, 64, 256),
    ],
  },
  {
    id: 8, name: 'Flower Bed', type: 'minecolonies:decoration', kind: 'decoration',
    blockId: null, level: 1, x: 15, y: 70, z: 20,
    beingBuilt: false, workOrderId: -1, required: [],
  },
  {
    id: 9, name: 'Stone Path', type: 'minecolonies:decoration', kind: 'decoration',
    blockId: 'minecolonies:decorationcontroller', level: 1, x: -25, y: 70, z: 15,
    beingBuilt: false, workOrderId: -1, required: [],
  },
  {
    id: 10, name: "Miner's Hut", type: 'minecolonies:miner', kind: 'building',
    blockId: 'minecolonies:blockhutminer', level: 2, x: 70, y: 66, z: 60,
    beingBuilt: false, workOrderId: -1, required: [],
  },
]

const WORK_ORDERS = [
  {
    id: 1, buildingName: "Farmer's Hut", buildingType: 'minecolonies:farmer',
    x: -60, y: 67, z: 40, currentLevel: 2, targetLevel: 3, action: 'UPGRADE',
    builderId: 1, builderName: 'Alice', progress: 0.65,
  },
  {
    id: 2, buildingName: "Enchanter's Tower", buildingType: 'minecolonies:enchanter',
    x: -30, y: 68, z: -70, currentLevel: 0, targetLevel: 1, action: 'BUILD',
    builderId: 3, builderName: 'Charlie', progress: 0.3,
  },
  {
    id: 3, buildingName: 'Guard Tower', buildingType: 'minecolonies:guardtower',
    x: 90, y: 71, z: -30, currentLevel: 1, targetLevel: 1, action: 'REPAIR',
    builderId: -1, builderName: '', progress: 0.1,
  },
]

const BUILDERS = [
  { id: 1, name: 'Alice', hutX: 120, hutY: 68, hutZ: -80, assignedWorkOrderId: 1 },
  { id: 3, name: 'Charlie', hutX: 118, hutY: 68, hutZ: -76, assignedWorkOrderId: 2 },
  { id: 7, name: 'Dave', hutX: 122, hutY: 68, hutZ: -84, assignedWorkOrderId: -1 },
]

function skills(primary, secondary, base) {
  const names = ['Adaptability', 'Athletics', 'Agility', 'Creativity', 'Dexterity',
    'Focus', 'Intelligence', 'Knowledge', 'Mana', 'Stamina', 'Strength']
  return names.map((name, i) => ({
    name,
    level: Math.max(1, (base + i * 3) % 45),
    xp: ((base + i) * 37) % 900,
    role: name === primary ? 'primary' : name === secondary ? 'secondary' : null,
  }))
}

const MODIFIERS = [
  { name: 'Well fed', factor: 1.2 },
  { name: 'Comfortable home', factor: 1.1 },
  { name: 'No school nearby', factor: 0.85 },
]

function citizen(id, name, job, jobType, opts = {}) {
  const list = opts.skills ?? skills(opts.primary, opts.secondary, id * 7)
  return {
    id,
    name,
    job,
    jobType,
    jobIcon: jobType ? `minecolonies:blockhut${jobType.split(':').pop()}` : null,
    child: opts.child ?? false,
    female: opts.female ?? false,
    health: opts.health ?? 20,
    maxHealth: 20,
    saturation: opts.saturation ?? 14,
    happiness: opts.happiness ?? 8,
    spawned: opts.spawned ?? true,
    x: opts.x ?? 0,
    y: 70,
    z: opts.z ?? 0,
    workBuilding: opts.workBuilding ?? '',
    workBuildingId: opts.workBuildingId ?? -1,
    homeBuilding: opts.homeBuilding ?? 'Residence 1',
    homeBuildingId: 20,
    status: opts.status ?? null,
    primarySkill: opts.primary ?? '',
    secondarySkill: opts.secondary ?? '',
    skillTotal: list.reduce((n, s) => n + s.level, 0),
    inventoryUsed: opts.inventoryUsed ?? 4,
    inventorySize: 27,
    skills: list,
    modifiers: MODIFIERS,
  }
}

const CITIZENS = [
  citizen(1, 'Alice', 'Builder', 'minecolonies:builder', {
    female: true, x: -60, z: 40, workBuilding: "Builder's Hut", workBuildingId: 1,
    primary: 'Adaptability', secondary: 'Athletics', status: 'Building the Farmer\'s Hut',
  }),
  citizen(2, 'Bob', 'Farmer', 'minecolonies:farmer', {
    x: -58, z: 44, workBuilding: "Farmer's Hut", workBuildingId: 4, health: 17,
    primary: 'Stamina', secondary: 'Dexterity', happiness: 6.4, saturation: 9,
  }),
  citizen(3, 'Charlie', 'Builder', 'minecolonies:builder', {
    x: -30, z: -70, workBuilding: "Builder's Hut", workBuildingId: 1,
    primary: 'Adaptability', secondary: 'Athletics', happiness: 9.1,
  }),
  citizen(4, 'Dana', 'Unemployed', null, {
    female: true, x: 50, z: 30, happiness: 4.2, saturation: 6,
  }),
  citizen(5, 'Eve', 'Knight', 'minecolonies:knight', {
    female: true, x: 90, z: -30, workBuilding: 'Guard Tower', workBuildingId: 5, health: 18,
    primary: 'Strength', secondary: 'Stamina',
  }),
  citizen(6, 'Frank', 'Ranger', 'minecolonies:ranger', {
    x: 88, z: -34, workBuilding: 'Guard Tower', workBuildingId: 5, health: 20,
    primary: 'Agility', secondary: 'Adaptability',
  }),
  citizen(7, 'Timmy', 'Unemployed', null, {
    child: true, x: 4, z: 6, happiness: 9.8, saturation: 18,
  }),
  citizen(8, 'Greta', 'Miner', 'minecolonies:miner', {
    female: true, x: 70, z: 60, workBuilding: "Miner's Hut", workBuildingId: 10,
    spawned: false, primary: 'Strength', secondary: 'Stamina',
  }),
]

const EQUIPMENT = [
  { ...item('minecraft:iron_helmet', 'Iron Helmet'), slot: 'Head', armorPoints: 2, enchanted: false, durabilityPct: 82 },
  { ...item('minecraft:iron_chestplate', 'Iron Chestplate'), slot: 'Chest', armorPoints: 6, enchanted: true, durabilityPct: 61 },
  { ...item('minecraft:iron_leggings', 'Iron Leggings'), slot: 'Legs', armorPoints: 5, enchanted: false, durabilityPct: 100 },
  { ...item('minecraft:iron_boots', 'Iron Boots'), slot: 'Feet', armorPoints: 2, enchanted: false, durabilityPct: 47 },
  { ...item('minecraft:iron_sword', 'Iron Sword'), slot: 'Main hand', armorPoints: 0, enchanted: true, durabilityPct: 73 },
]

const INVENTORY = [
  { ...item('minecraft:iron_pickaxe', 'Iron Pickaxe'), count: 1, slot: 0 },
  { ...item('minecraft:bread', 'Bread', { craftable: true, craftedIn: 'Baker' }), count: 5, slot: 1 },
  { ...item('minecraft:torch', 'Torch', { craftable: true }), count: 32, slot: 2 },
  { ...item('minecraft:cobblestone', 'Cobblestone'), count: 14, slot: 3 },
]

const WAREHOUSE_STACKS = [
  { ...item('minecraft:cobblestone', 'Cobblestone'), count: 2048, maxStackSize: 64 },
  { ...item('minecraft:oak_planks', 'Oak Planks', { craftable: true }), count: 448, maxStackSize: 64 },
  { ...item('minecraft:oak_log', 'Oak Log'), count: 256, maxStackSize: 64 },
  { ...item('minecraft:wheat', 'Wheat'), count: 384, maxStackSize: 64 },
  { ...item('minecraft:iron_ingot', 'Iron Ingot', { craftable: true, craftedIn: 'Smelter' }), count: 96, maxStackSize: 64 },
  { ...item('minecraft:carrot', 'Carrot'), count: 192, maxStackSize: 64 },
  { ...item('minecraft:potato', 'Potato'), count: 128, maxStackSize: 64 },
  { ...item('minecraft:diamond', 'Diamond'), count: 24, maxStackSize: 64 },
  { ...item('minecraft:ender_pearl', 'Ender Pearl'), count: 7, maxStackSize: 16 },
  { ...item('minecraft:oak_boat', 'Oak Boat', { craftable: true }), count: 3, maxStackSize: 1 },
  {
    ...item('domum_ornamentum:brick_extra#a1b2', 'Brick Extra', {
      domum: true,
      material: 'Brick, Oak',
      craftedIn: 'Architects Cutter',
      craftable: true,
      components: [
        { id: 'domum_ornamentum:shingle_face', label: 'Main Material', material: 'Brick', itemKey: 'minecraft:bricks' },
        { id: 'domum_ornamentum:shingle_support', label: 'Supported by', material: 'Oak', itemKey: 'minecraft:oak_planks' },
      ],
    }),
    count: 320, maxStackSize: 64,
  },
]

function researchEntry(id, name, branch, depth, state, progress, maxProgress, effects, cost) {
  return {
    id, name, branch, depth, state, progress, maxProgress,
    effects: effects || [],
    requirements: [],
    cost: (cost || []).map(([key, label, count]) => ({
      ...item(key, label, { craftable: count > 32 }),
      count,
      slot: -1,
    })),
  }
}

const RESEARCH = {
  available: true,
  completed: 4,
  inProgress: 2,
  total: 9,
  branches: [
    {
      id: 'minecolonies:technology', name: 'Technology',
      completed: 2, inProgress: 1, total: 4,
      researches: [
        researchEntry('stone_tools', 'Stone Tools', 'Technology', 1, 'COMPLETED', 1, 1,
          ['Builders work 5% faster'], [['minecraft:cobblestone', 'Cobblestone', 64]]),
        researchEntry('iron_tools', 'Iron Tools', 'Technology', 2, 'COMPLETED', 1, 1,
          ['Tool durability +10%'], [['minecraft:iron_ingot', 'Iron Ingot', 32]]),
        researchEntry('diamond_tools', 'Diamond Tools', 'Technology', 3, 'IN_PROGRESS', 7, 10,
          ['Tool durability +25%'], [['minecraft:diamond', 'Diamond', 48], ['minecraft:iron_ingot', 'Iron Ingot', 32]]),
        researchEntry('netherite_tools', 'Netherite Tools', 'Technology', 4, 'NOT_STARTED', 0, 12,
          ['Tool durability +50%'], [['minecraft:netherite_ingot', 'Netherite Ingot', 16]]),
      ],
    },
    {
      id: 'minecolonies:combat', name: 'Combat',
      completed: 1, inProgress: 1, total: 3,
      researches: [
        researchEntry('reinforced_walls', 'Reinforced Walls', 'Combat', 1, 'COMPLETED', 1, 1,
          ['Guards take 5% less damage'], [['minecraft:stone_bricks', 'Stone Bricks', 128]]),
        researchEntry('archery', 'Archery Training', 'Combat', 2, 'IN_PROGRESS', 3, 8,
          ['Rangers deal 10% more damage'], [['minecraft:bow', 'Bow', 3], ['minecraft:arrow', 'Arrow', 64]]),
        researchEntry('avoidance', 'Avoidance', 'Combat', 3, 'NOT_STARTED', 0, 10,
          ['Knights dodge more often'], [['minecraft:shield', 'Shield', 4]]),
      ],
    },
    {
      id: 'minecolonies:civilian', name: 'Civilian',
      completed: 1, inProgress: 0, total: 2,
      researches: [
        researchEntry('crop_rotation', 'Crop Rotation', 'Civilian', 1, 'COMPLETED', 1, 1,
          ['Farmers harvest 10% more'], [['minecraft:wheat_seeds', 'Wheat Seeds', 96]]),
        researchEntry('beekeeping', 'Beekeeping', 'Civilian', 2, 'NOT_STARTED', 0, 6,
          ['Unlocks the Beekeeper'], [['minecraft:beehive', 'Beehive', 3]]),
      ],
    },
  ],
}

function guard(id, name, job, jobType, level, health, building, buildingLevel, x, z) {
  return {
    id, name, job, jobType, level, health, maxHealth: 20, spawned: true,
    building, buildingId: 5, buildingLevel,
    equipment: EQUIPMENT,
    armorPoints: 15,
    weapon: 'Iron Sword',
    x, y: 70, z,
  }
}

const COMBAT = {
  raidsPossible: true,
  underAttack: false,
  nightsSinceRaid: 3,
  raidLevel: 42,
  spiesEnabled: false,
  guardCount: 2,
  guardCapacity: 4,
  averageGuardLevel: 12.5,
  averageHealthPct: 95,
  graves: 1,
  guards: [
    guard(5, 'Eve', 'Knight', 'minecolonies:knight', 14, 18, 'Guard Tower', 1, 90, -30),
    guard(6, 'Frank', 'Ranger', 'minecolonies:ranger', 11, 20, 'Guard Tower', 1, 88, -34),
  ],
  posts: [
    {
      id: 5, name: 'Guard Tower', type: 'minecolonies:guardtower',
      blockId: 'minecolonies:blockhutguardtower', level: 1,
      assigned: 2, capacity: 2, x: 90, y: 71, z: -30,
    },
    {
      id: 11, name: 'Archery', type: 'minecolonies:archery',
      blockId: 'minecolonies:blockhutarchery', level: 2,
      assigned: 0, capacity: 4, x: -95, y: 70, z: 55,
    },
  ],
  events: [],
}

const STATS = {
  citizens: CITIZENS.length,
  maxCitizens: 12,
  children: CITIZENS.filter(c => c.child).length,
  unemployed: CITIZENS.filter(c => !c.jobType).length,
  happiness: 7.4,
  saturation: 12.6,
  buildings: BUILDINGS.filter(b => b.kind !== 'decoration').length,
  decorations: BUILDINGS.filter(b => b.kind === 'decoration').length,
  workOrders: WORK_ORDERS.length,
  builders: BUILDERS.length,
  guards: COMBAT.guardCount,
  warehouseTypes: WAREHOUSE_STACKS.length,
  warehouseItems: WAREHOUSE_STACKS.reduce((n, s) => n + s.count, 0),
  researchCompleted: RESEARCH.completed,
  researchInProgress: RESEARCH.inProgress,
  raided: false,
  nightsSinceRaid: COMBAT.nightsSinceRaid,
}

const COLONIES = [
  {
    id: 1, name: 'Oakridge', dimension: 'minecraft:overworld', owner: 'Steve',
    x: 0, y: 70, z: 0,
    buildingCount: BUILDINGS.length, builderCount: BUILDERS.length,
    activeWorkOrders: WORK_ORDERS.length,
  },
  {
    id: 2, name: 'Pinevale', dimension: 'minecraft:overworld', owner: 'Steve',
    x: 1200, y: 64, z: -400,
    buildingCount: 3, builderCount: 1, activeWorkOrders: 0,
  },
]

/** The second colony is deliberately sparse, to exercise the empty states. */
function snapshot(colonyId) {
  const summary = COLONIES.find(c => c.id === colonyId) || COLONIES[0]
  if (colonyId === 2) {
    return {
      id: summary.id, name: summary.name, dimension: summary.dimension, owner: summary.owner,
      builders: [], workOrders: [], buildings: [],
      warehouse: { present: false, stacks: [] },
      stats: { ...STATS, citizens: 0, buildings: 0, decorations: 0, workOrders: 0, builders: 0,
        guards: 0, warehouseTypes: 0, warehouseItems: 0, researchCompleted: 0, researchInProgress: 0 },
    }
  }
  return {
    id: summary.id, name: summary.name, dimension: summary.dimension, owner: summary.owner,
    builders: BUILDERS,
    workOrders: WORK_ORDERS,
    buildings: BUILDINGS,
    warehouse: { present: true, stacks: WAREHOUSE_STACKS },
    stats: STATS,
  }
}

module.exports = {
  COLONIES, BUILDINGS, CITIZENS, RESEARCH, COMBAT, EQUIPMENT, INVENTORY, snapshot,
}
