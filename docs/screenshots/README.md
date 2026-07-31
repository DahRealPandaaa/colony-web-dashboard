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

- Shoot the whole dashboard, sidebar included, so the tab being shown is obvious.
- 1600px wide is plenty — GitHub scales anything wider down to the column and the extra
  bytes ship in every clone. Keep files under ~500 KB.
- PNG, not JPEG. The UI is flat colour and text; JPEG will smear it.
- Use a colony with enough going on to fill the view — a fresh colony makes every tab look
  broken.
- Names are cosmetic, but they are still player names. Use a test world or a colony whose
  members are fine appearing in the repo.

To size an image down in the README, swap the Markdown line for HTML:

```html
<img src="docs/screenshots/overview.png" alt="The Overview tab" width="800">
```
