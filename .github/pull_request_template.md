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

## Release

Nothing to choose here. Merging publishes a pre-release carrying the next CI build
number — `v4`, `v5`, `v6` — so neither the branch name nor this description can change
what gets published.

Full `x.x.x` releases are a separate, deliberate step: run **Build & Release** manually
from the Actions tab and give it the exact version.
