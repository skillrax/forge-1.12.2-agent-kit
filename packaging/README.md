# Kit packaging

Build-KitZip.ps1 packages exactly files.json from kit/. The allowlist is every Git-tracked file under kit/; after adding, moving or deleting kit files, regenerate it from the repository root with `git ls-files kit` (strip the `kit/` prefix, sort) and review the diff. It rejects missing/unsafe/duplicate paths and existing archive outputs, fixes timestamps/order, and writes ZIP SHA256 plus per-file hashes and size metadata. Dependencies, authoring QA/evidence and prototype versions are excluded. Test-KitZip.ps1 verifies the actual extracted candidate and runs shell-only setup without Node and a real foundation build.

The version comes from kit/kit.json; manifest releaseState follows its alpha/beta stage (`release` for a plain x.y.z). Archives go to releases/<version>, ignored by Git. Same-host repeat packaging to a separate output directory can compare ZIP hashes. Export-Release.ps1 inside the kit exports mod JARs and is separate from this packager.

Publishing (public since 2026-10-21, github.com/skillrax/forge-1.12.2-agent-kit): after qa/Run-All.ps1 passes and Test-KitZip.ps1 verifies the built ZIP, tag `v<version>` and attach the ZIP and its manifest to a GitHub release (`gh release create`), with notes from kit/CHANGELOG.md.
