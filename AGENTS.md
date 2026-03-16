# Agent Notes

## Spotless / formatting checklist
- Always run `./gradlew spotlessApply` before committing Java or JSON changes.
- Verify formatting passes with `./gradlew spotlessJavaCheck` (or `./gradlew spotlessCheck` for broader checks) before opening a PR.
- If Spotless reports violations, do not hand-format line wrapping/blank lines manually; rerun Spotless and commit the resulting formatting changes.

## Tests
- Maintain test suite validity and meaningfullness.
- Add new tests to validate new implementations.

## NeoForge AutoRenamingTool cache corruption troubleshooting
- If Gradle fails during NeoForge artifact generation with an error like `Invalid or corrupt jarfile ... AutoRenamingTool-2.0.3-all.jar`, the local Gradle cache may contain a bad download.
- Fix it by rerunning with dependency refresh (preferred): `./gradlew --refresh-dependencies <task>`.
- If refresh alone does not recover, stop daemons and clear the affected cache entry, then rerun:
  - `./gradlew --stop`
  - remove the bad AutoRenamingTool directory under `~/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/`
  - rerun your build/test command.
- Do **not** skip test execution because of this environment issue. Repair the cache/tooling first, then run the relevant tests so changes are still validated.
