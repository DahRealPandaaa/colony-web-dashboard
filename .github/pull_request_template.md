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
