# ktc-ktlint

A standalone local [Kotlin Toolchain](https://github.com/JetBrains/kotlin-toolchain)
plugin for ktlint 1.8.0. Pinned and tested with Kotlin Toolchain **0.13.0**.
Includes a real consumer example, a reusable module template, and producer metadata
for source-based plugin installers. This is an independent repository.

## Try it

```sh
./kotlin build
./kotlin check
./kotlin do ktlintFormat -m example
python3 scripts/smoke.py
```

On Windows use `kotlin.bat`. `check` runs unit tests plus `ktlintCheck` for enabled
modules. Narrow a check with `./kotlin check ktlintCheck -m example`.
Formatting is an explicit source-writing command; building and checking never
invoke it. Choose one formatter for each source tree: ktlint and ktfmt can disagree
on formatting conventions.

## Install in another project

From your consumer project, use the [ktc-plugins installer](https://github.com/Heapy/ktc-plugins):

```sh
./ktc-plugins add Heapy/ktc-ktlint --branch main --enable-in app
```

Replace `app` with your consumer module path. The root [`ktc-plugin.yaml`](ktc-plugin.yaml)
declares selector `ktlint`, module `plugins/ktlint`, and `LICENSE`; the installer registers
and enables the plugin automatically. Commit the generated manifest, lockfile, and vendored
sources. The lockfile pins the resolved commit; use `--commit <full-40-character-SHA>`
instead of `--branch main` to select a specific revision. Templates are copied separately.

For manual installation:

Copy `plugins/ktlint/` to the same path in the consumer and retain this repository's
`LICENSE`. The plugin module is self-contained: all dependencies use pinned Maven
coordinates, and it requires no root template, catalog, or other local module.
Register the copied module in **both** lists in the consumer's `project.yaml`:

```yaml
modules:
  - app
  - plugins/ktlint

plugins:
  - //plugins/ktlint
```

Enable it in `app/module.yaml`:

```yaml
plugins:
  ktlint:
    enabled: true
    includes: ["**/*.kt", "**/*.kts"]
    excludes: ["**/generated/**"]
    # Kotlin code style and individual rules are configured in .editorconfig.
```

For common settings, optionally copy `templates/ktlint.module-template.yaml` and
apply it from modules with `apply: [//templates/ktlint.module-template.yaml]`.
`ktc-plugin.yaml` declares selector `ktlint`, module path `plugins/ktlint`, and
`licenseFiles: [LICENSE]` for the source installer.

## Files and execution

Discovery starts at the enabled module's directory and includes `.kt` and `.kts`
files in main sources, tests, platform directories, Maven-style layouts, and
module-root scripts. It always skips directories named `build`, `.git`, `.kotlin`,
`.gradle`, `.idea`, and `node_modules`, plus nested modules with their own
`module.yaml`. Symlinks are not followed. Generated directories are excluded by
the default glob; add project-specific output directories to `excludes` when needed.

Globs are Java NIO globs relative to the module root, using `/` separators. A leading
`**/` also matches zero directories. `includes` selects files and `excludes` removes
matches. The lists above are defaults; configuration replaces those lists.
Discovery is source-based and can inspect platform directories without compiling
that target. It does not inspect Java, generated build outputs, or dependencies.

Both actions disable execution avoidance so source and parent configuration changes
are always observed. Their root inputs disable task-dependency inference, and format
actions declare no source outputs. This prevents the toolchain from treating a
format action as a prerequisite for compilation or checking. Within each module, formatting computes
all results before writing, so a parse error leaves that module unchanged. Other
modules can still finish formatting. Filesystem write failures can leave some completed writes.

The plugin uses ktlint's standard rule providers and reads applicable `.editorconfig`
files through the upstream rule engine, including parent and nested files. Use
`ktlint_code_style`, `ktlint_standard_<rule>`, indentation, and other upstream
properties there. It does not load custom rule JARs or support baselines/report files
in this version. Diagnostics include path, line, column, rule ID, and message.

Formatting applies supported fixes, then fails if violations remain that need manual
changes. The check path calls the lint API only. ktlint 1.8.0 is the stable release;
2.0.0-ALPHA-4 has a new namespace and is deliberately not the default dependency.

The embedded upstream Kotlin parser determines supported source syntax; newer
language constructs may need a dependency upgrade even when Toolchain compiles
them. The real Kotgent trial confirmed that ktlint 1.8.0 rejects Kotlin 2.4.20's
square-bracket destructuring (`val [a, b] = ...` and `for ([a, b] in ...)`).
Modules using that syntax cannot currently pass this plugin's checks or format
command; a parse failure reports the source path and leaves that module unchanged.
See [the trial results](docs/verification.md#real-kotgent-worktree-trial).
Upgrade the literal versions in `plugins/ktlint/module.yaml` together with
these checks. Current dependencies: `com.pinterest.ktlint:ktlint-rule-engine:1.8.0 and ktlint-ruleset-standard:1.8.0`.

## Verification and maintenance

`./kotlin check` exercises source discovery, include/exclude boundaries, scripts,
read-only failed checks, formatting idempotence, and syntax-error behavior. The
isolated consumer smoke test copies only the plugin module and wrapper into a fresh
project and proves that build/check do not format, checks fail on bad formatting,
explicit formatting fixes main/test/script files, generated exclusions survive,
consumer tests pass, and a later violation is detected.

CI repeats build, check, and the smoke test on Linux, macOS, and Windows, using
pinned checkout and [Heapy/setup-ktc](https://github.com/Heapy/setup-ktc) commits.
Dependabot tracks action updates. Local verification is recorded in
[docs/verification.md](docs/verification.md).

## Upstream and license

- [ktlint 1.8.0 release](https://github.com/ktlint/ktlint/releases/tag/1.8.0)
- [ktlint rule engine API at the pinned tag](https://github.com/ktlint/ktlint/blob/1.8.0/ktlint-rule-engine/src/main/kotlin/com/pinterest/ktlint/rule/engine/api/KtLintRuleEngine.kt)
- [ktlint configuration](https://pinterest.github.io/ktlint/1.8.0/rules/configuration-ktlint/)
- [Toolchain 0.13.0](https://github.com/JetBrains/kotlin-toolchain/tree/v0.13.0)

Apache-2.0; see [LICENSE](LICENSE). Upstream dependencies retain their own licenses.
