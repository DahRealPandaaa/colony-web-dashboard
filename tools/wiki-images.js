/**
 * Build the manifest of MineColonies wiki images to bundle with ColonyWeb.
 *
 * Two sources, because the wiki keeps them apart:
 *
 * - **Hut block icons** come from the wiki's generator submodule, which renders every block
 *   state to a PNG. The wiki site itself is gitignoring these under `public/images/wiki/blocks`
 *   and pulls them from the generator at build time, so that is where we go too.
 * - **Worker portraits** are committed in the wiki repo proper.
 *
 *   node tools/wiki-images.js > tools/wiki-images.tsv
 *
 * Sources (both GPL-3.0 — see webroot/img/ATTRIBUTION.md):
 *   https://github.com/ldtteam/MinecoloniesWiki
 *   https://github.com/ldtteam/minecolonies-wiki-generator
 */
const https = require("https");

const REPO = "ldtteam/MinecoloniesWiki";
const BRANCH = "main";
const RAW = `https://raw.githubusercontent.com/${REPO}/${BRANCH}/`;

const GEN_REPO = "ldtteam/minecolonies-wiki-generator";
const GEN_BRANCH = "publish";
const GEN_RAW = `https://raw.githubusercontent.com/${GEN_REPO}/${GEN_BRANCH}/`;

/** Generator output version to take block renders from. */
const GEN_VERSION = "12100";

/**
 * Which block state to render. The hut blocks are directional, and east puts the decorated
 * front face toward the camera — the same one the wiki shows.
 */
const FACING = "east";

/**
 * MineColonies job registry paths whose wiki worker folder is named differently.
 * The wiki names some roles after the citizen, MineColonies after the job.
 */
const JOB_ALIASES = {
    deliveryman: "courier",
    ranger: "archer",
    lumberjack: "forester",
    fisherman: "fisher",
    student: "pupil",
    stonesmeltery: "stonesmelter",
    sawmill: "carpenter",
    netherworker: "netherminer",
    knight: "knight",
    cook: "chef",
    cookassistant: "chef",
    baker: "baker",
    beekeeping: "beekeeper",
    beekeeper: "beekeeper",
    rabbitherder: "rabbitherder",
    combattraining: "knight",
    archertraining: "archer",
    druid: "druid",
    enchanter: "enchanter",
    quarrier: "quarrier",
    undertaker: "undertaker",
    teacher: "teacher",
    healer: "healer",
    planter: "planter",
    crusher: "crusher",
    sifter: "sifter",
    smelter: "smelter",
    dyer: "dyer",
    fletcher: "fletcher",
    glassblower: "glassblower",
    concretemixer: "concretemixer",
    mechanic: "mechanic",
    florist: "florist",
    alchemist: "alchemist",
    blacksmith: "blacksmith",
    stonemason: "stonemason",
    composter: "composter",
    builder: "builder",
    miner: "miner",
    farmer: "farmer",
    shepherd: "shepherd",
    cowboy: "cowboy",
    swineherder: "swineherder",
    chickenherder: "chickenherder",
    researcher: "researcher",
};

function fetchJson(url) {
    return new Promise((resolve, reject) => {
        https.get(url, { headers: { "User-Agent": "colonyweb-asset-tool" } }, (res) => {
            if (res.statusCode !== 200) return reject(new Error(`${url} -> ${res.statusCode}`));
            let body = "";
            res.on("data", (c) => (body += c));
            res.on("end", () => resolve(JSON.parse(body)));
        }).on("error", reject);
    });
}

(async () => {
    const tree = await fetchJson(
        `https://api.github.com/repos/${REPO}/git/trees/${BRANCH}?recursive=1`);
    const paths = tree.tree.filter((n) => n.type === "blob").map((n) => n.path);

    const rows = [];
    const seen = new Set();
    const add = (url, out, w, h, format) => {
        if (seen.has(out)) return;
        seen.add(out);
        rows.push([url, out, w, h, format].join("\t"));
    };

    // ---- Blocks: the icon the game itself shows ----
    // Every MineColonies block, not just the huts: a decoration is anchored by a deco
    // controller, a quarry by its own block, and so on — whatever the world reports at a
    // building's position should resolve to a real icon.
    const stateDir = `versions/${GEN_VERSION}/output/block_states/minecolonies`;
    const states = await fetchJson(
        `https://api.github.com/repos/${GEN_REPO}/contents/${stateDir}?ref=${GEN_BRANCH}`);
    const blocks = states
        .filter((entry) => entry.name.endsWith(".json"))
        .map((entry) => entry.name.replace(/\.json$/, ""));

    const missingBlocks = [];
    for (const block of blocks) {
        const state = await fetchJson(`${GEN_RAW}${stateDir}/${block}.json`);
        const imageId = imageIdFor(state);
        if (!imageId) { missingBlocks.push(block); continue; }
        add(`${GEN_RAW}versions/${GEN_VERSION}/output/block_images/minecolonies/${block}/${imageId}.png`,
            `blocks/${block}.png`, 128, 128, "png");
    }

    /**
     * The front-facing ("a" pose) portrait for a worker folder.
     *
     * Single-level jobs are `default-a.png`; jobs with levels are `default-1a.png`, and the
     * plain citizen is `default-aristocrat1a.png`. Lowest level first, so the portrait matches
     * a starting colony rather than an end-game one.
     */
    const portrait = (folder, gender) => {
        const dir = `src/assets/images/wiki/workers/${folder}/${gender}/`;
        const poses = paths.filter((p) => p.startsWith(dir) && p.endsWith("a.png")).sort();
        return poses.find((p) => p.endsWith("/default-a.png")) || poses[0] || null;
    };

    // ---- Workers: one portrait per job, per gender ----
    const missingJobs = [];
    const jobs = new Set([...Object.keys(JOB_ALIASES), ...Object.values(JOB_ALIASES)]);
    for (const job of jobs) {
        const folder = JOB_ALIASES[job] || job;
        let any = false;
        for (const gender of ["male", "female"]) {
            const match = portrait(folder, gender);
            if (!match) continue;
            any = true;
            add(RAW + match, `jobs/${job}-${gender}.png`, 128, 128, "png");
        }
        if (!any) missingJobs.push(job);
    }

    // A generic citizen portrait, used for anyone with no job-specific art.
    for (const gender of ["male", "female"]) {
        const match = portrait("citizen", gender);
        if (match) add(RAW + match, `jobs/_citizen-${gender}.png`, 128, 128, "png");
    }

    console.log(rows.join("\n"));
    console.error(`manifest: ${rows.length} images (${blocks.length} blocks)`);
    if (missingBlocks.length) console.error(`no block render: ${missingBlocks.join(", ")}`);
    if (missingJobs.length) console.error(`no worker art: ${missingJobs.join(", ")}`);
})().catch((e) => { console.error(e); process.exit(1); });

/** The chosen facing's render id, falling back to whatever the block's first state is. */
function imageIdFor(state) {
    const all = state.blockstates || [];
    const facing = all.find((entry) =>
        (entry.values || []).some((v) => v.property === "facing" && v.value === FACING));
    return (facing || all[0] || {}).imageid;
}
