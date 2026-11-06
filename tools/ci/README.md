# GitHub Actions workflows

GitHub only runs workflows from `.github/workflows/`. Move these two files there before the first push:

```
.github/workflows/build.yml     <- tools/ci/build.yml
.github/workflows/release.yml   <- tools/ci/release.yml
```

- `build.yml`: builds every push and pull request, checks that only `com.polycore.neoforge` imports NeoForge,
  and keeps the jar as a workflow artifact.
- `release.yml`: on a tag `v<version>` (equal to `mod_version` in `gradle.properties`), builds the mod and
  publishes a GitHub Release with `polycore-<version>.jar`. Versions with a `-` (e.g. `0.3.0-beta.1`) are
  marked as pre-releases.

Release steps: bump `mod_version`, commit, then `git tag v0.3.0` and `git push origin main v0.3.0`.
