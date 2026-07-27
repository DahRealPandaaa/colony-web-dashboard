## Summary

<!-- What changed, and why. -->

<!--
Where does this belong?
  common/     shared code — must compile against EVERY supported Minecraft version.
              CI builds each one, so a 1.20.1-only API will fail the 1.21.1 leg.
  versions/   loader- or version-specific code. If shared code needs to do something
              different per version, add a method to platform/Platform.java rather than
              moving the caller out of common/.
Adding a Minecraft version needs no CI change — see "Adding a Minecraft version" in the README.
-->

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
