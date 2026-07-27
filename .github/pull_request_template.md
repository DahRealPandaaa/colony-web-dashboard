## Summary

<!-- What changed, and why. -->

## Version bump

The release version is taken from the branch prefix: `major/` and `breaking/` bump major,
`feat/`, `feature/` and `minor/` bump minor, anything else bumps patch.

Tick a box to override that:

- [ ] **major** — breaking change
- [ ] **minor** — new feature, backwards compatible
- [ ] **patch** — fix, docs or internal change

<!-- Leave all boxes unticked to use the branch prefix. -->

A merged pull request always keeps the current suffix, so it can only ever cut another
`-BETA`. Promoting to a full release, or publishing a shortened version such as `v1.1`
or `v2`, is done by running **Build & Release** manually from the Actions tab.
