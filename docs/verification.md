# Verification

Verified locally on 2026-10-05, macOS arm64, Kotlin Toolchain 0.13.0, JDK 25.
Pinned engine: ktlint 1.8.0; SLF4J Simple 2.0.20.

- `./kotlin build`: plugin and example main/test sources compile.
- `./kotlin check`: 8 plugin tests and 1 example test pass; example `ktlintCheck` passes.
- `kotlinr scripts/smoke.main.kts`: isolated copied-plugin consumer builds and tests;
  failed formatting checks preserve source bytes; explicit format fixes main,
  test, and script files; generated exclusion stays untouched; a second format
  changes no bytes; a new violation fails the next check.

The `.editorconfig` test changes indentation after a successful run, confirms the
next check fails, then confirms formatting follows the new setting. The explicit
SLF4J runtime dependency is required by the embedded rule engine.

The macOS agent sandbox denied Toolchain's `sysctl` process inspection; actual
build and task verification ran with permission for compiler subprocess management.
Kotlin's embedded compiler emits an upstream `sun.misc.Unsafe` deprecation warning
on JDK 25; it did not fail these checks.

The three-OS GitHub Actions matrix is included but has not been run remotely.
Linux/Windows behavior and newer Kotlin syntax than the upstream embedded parser
supports are not established by this local verification. No release or registry
operation was part of these checks.

## Real Kotgent worktree trial

On 2026-10-05 the plugin payload and license were copied into a detached Kotgent
worktree based on `ac1f35a21af210c0579b3536f326da96dace0ebb`. The worktree is
`ktc-plugin-trials/2026-10-05/kotgent-ktlint`; its
`validation/result.json`, command logs, and SHA-256 snapshots preserve the evidence.
These are maintainer-local artifacts, not files in this repository. No files in the
primary Kotgent checkout were changed.

The consumer registered `plugins/ktlint` in `project.yaml` and enabled ktlint in
the Native root, `fakes`, `sysnative`, `webuitest`, `plugins/build-info`, and
`plugins/sqldelight-gen`. No include/exclude overrides, rule disabling, or language
rewrites were used. All Toolchain invocations were serialized with
`kotgent mutex run kotlin-build -- ./kotlin ...`.

| Real module | Initial check | Explicit format and repeat check |
| --- | --- | --- |
| Native root (302 discovered files) | Parser rejects `src/adapter/claude/ClaudeCli.kt:47` | Module remains unchanged |
| fakes (9 files) | Parser rejects `src/daemon/FakeTmux.kt:31` | Module remains unchanged |
| webuitest (30 files) | Parser rejects `test/ApiPrefixTest.kt:39` | Module remains unchanged |
| build-info (4 files) | 51 style violations | All 4 formatted; check passes |
| sqldelight-gen (2 files) | 263 style violations | Both formatted; check passes |
| sysnative (12 files) | 54 style violations | 6 formatted; check passes |

The parser failures are Kotlin 2.4.20 square-bracket destructuring: `val [a, b] = ...`
and `for ([a, b] in ...)`. Kotgent enables `-Xname-based-destructuring=complete`;
ktlint 1.8.0's embedded parser cannot process this syntax. Full checks and format
commands therefore correctly exit nonzero for the first three modules, and the
trial does **not** establish compatibility for later files in those modules.
The complete project cannot currently adopt this pinned engine successfully.

Commands and observations:

- `./kotlin show checks` registers `ktlintCheck` alongside built-in tests.
- `./kotlin check ktlintCheck` fails on existing style and parser findings. All
  393 Kotlin file hashes in the worktree are identical before and after the check.
- `./kotlin do ktlintFormat` formats exactly 12 files in the three supported
  modules; root, fakes, and webuitest remain unchanged despite earlier files being
  parsed before their failures. Atomicity is per module, not across modules.
- `./kotlin check ktlintCheck -m build-info -m sqldelight-gen -m sysnative` passes
  across all 18 source/test/platform files in those complete modules.
- `./kotlin do ktlintFormat -m build-info -m sqldelight-gen -m sysnative` then
  reports zero formatted files, and every source hash remains identical.
- `./kotlin test -m build-info` passes all 10 existing consumer tests after
  formatting. Native, browser, and full-product tests were not run for this trial.

The real trial exposed a plugin diagnostic defect: upstream parse exceptions
contained line/column but omitted the filename. The plugin now reports the full
source path, preserves the upstream cause, and suggests unsupported parser syntax
as a possible reason. A failed-check regression and the strengthened atomic-format
regression both failed before the fix. Afterward, `./kotlin check` in this plugin
repository passes 9 plugin tests, 1 example test, and example lint. Repeating the
real consumer check confirms filenames are now present in parser diagnostics.

The initial sandboxed `show checks` failed on Toolchain's `sysctl` process
inspection; subsequent commands ran with permission for compiler subprocess
management. This host restriction is separate from the verified parser limitation.
