# Bundled artwork — source and licence

Every image under this directory comes from the **MineColonies Wiki**, across its two
repositories:

- `blocks/blockhut*.png` — the game's own hut block renders, from the wiki's generator
  submodule <https://github.com/ldtteam/minecolonies-wiki-generator> (branch `publish`) at
  `versions/12100/output/block_images/minecolonies/…`. The wiki site itself does not commit
  these — `public/images/wiki/blocks/` is gitignored and populated from the generator at build
  time — so that is where they are taken from. The **east**-facing block state is used, which is
  the one the wiki renders. Downscaled to fit 160×160.
- `jobs/*.png` — worker portraits from <https://github.com/ldtteam/MinecoloniesWiki> at
  `src/assets/images/wiki/workers/<job>/<gender>/default-*a.png`, downscaled to fit 128×128.
  `_citizen-*.png` is the generic settler, used for anyone with no job-specific art.

Nothing here was redrawn or modified beyond downscaling and re-encoding.

To refresh them:

```bash
node tools/wiki-images.js > tools/wiki-images.tsv
java tools/WikiImages.java tools/wiki-images.tsv src/main/resources/webroot/img
```

## Licence

Both repositories are licensed **GNU GPL v3.0**. That licence is copyleft: a work
that includes these files and is distributed to others is expected to be distributed under
GPL-3.0 as well, with the corresponding source offered to recipients.

ColonyWeb's own `mod_license` in `gradle.properties` currently reads **All Rights Reserved**,
which is not compatible with that. Before publishing a build that bundles these images, pick
one:

1. **Relicense ColonyWeb under GPL-3.0** — simplest, and consistent with the MineColonies
   ecosystem, which is GPL-3.0 throughout.
2. **Ship the images separately** as an optional GPL-3.0 resource pack, keeping the mod jar
   itself free of them. The dashboard already degrades gracefully: every image falls back to a
   server-rendered block texture when the file is missing.
3. **Ask LDTTeam for permission** to bundle the artwork under different terms.

This file is not legal advice — it is a note that the question is open and needs answering by
the project owner.
