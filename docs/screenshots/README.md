# Screenshots

Images referenced by the Features section of the root [README](../../README.md). Each tab has
its image line already written there, commented out — add the file here, uncomment the line,
and it renders.

| File | Tab |
|---|---|
| `overview.png` | Overview |
| `map.png` | Map |
| `buildings.png` | Buildings |
| `citizens.png` | Citizens |
| `research.png` | Research |
| `combat.png` | Combat |
| `warehouse.png` | Warehouse |

## Capturing them

- **Maximum width 850px.** This is a hard limit, not a suggestion: the CurseForge project
  description reuses these images, and it does not scale anything wider to fit — a 1600px
  screenshot gets clipped there even though GitHub renders it fine. Resize the file itself;
  an `<img width="850">` tag only fixes the GitHub side, because CurseForge strips the
  attribute and shows the image at its true size.
- Shoot the whole dashboard, sidebar included, so the tab being shown is obvious. Capture at
  a narrow browser width rather than shooting wide and downscaling — 850px of a 2560px window
  turns the UI text to mush.
- PNG, not JPEG. The UI is flat colour and text; JPEG will smear it. Under ~500 KB a file,
  which 850px width makes easy.
- Use a colony with enough going on to fill the view — a fresh colony makes every tab look
  broken.
- Names are cosmetic, but they are still player names. Use a test world or a colony whose
  members are fine appearing in the repo.

Check a file before committing it:

```sh
# ImageMagick
identify -format '%f %wx%h\n' docs/screenshots/*.png

# or, resize one that came out too wide (never upscales)
magick overview.png -resize 850x\> overview.png
```
