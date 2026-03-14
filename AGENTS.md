# Agent Notes

## Spotless / formatting checklist
- Always run `./gradlew spotlessApply` before committing Java or JSON changes.
- Verify formatting passes with `./gradlew spotlessJavaCheck` (or `./gradlew spotlessCheck` for broader checks) before opening a PR.
- If Spotless reports violations, do not hand-format line wrapping/blank lines manually; rerun Spotless and commit the resulting formatting changes.

## Tests
- Maintain test suite validity and meaningfullness.
- Add new tests to validate new implementations.
