package dev.ktc.plugins.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintParseException
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider
import java.nio.file.Path
import kotlin.io.path.writeText
import org.jetbrains.amper.plugins.Configurable
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction

@Configurable
interface KtlintSettings {
    val includes: List<String> get() = listOf("**/*.kt", "**/*.kts")
    val excludes: List<String> get() = listOf("**/generated/**")
}

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun checkFormatting(
    @Input(inferTaskDependency = false) moduleRoot: Path,
    settings: KtlintSettings,
) {
    runKtlint(moduleRoot, settings, format = false)
}

// Formatting modifies source inputs deliberately. Declaring them as outputs would wire format
// into compilation/check tasks through path inference. Always run it only on explicit request.
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun formatSources(
    @Input(inferTaskDependency = false) moduleRoot: Path,
    settings: KtlintSettings,
) {
    runKtlint(moduleRoot, settings, format = true)
}

internal fun runKtlint(moduleRoot: Path, settings: KtlintSettings, format: Boolean) {
    val files = kotlinSources(moduleRoot, settings.includes, settings.excludes)
    val engine = KtLintRuleEngine(ruleProviders = StandardRuleSetProvider().getRuleProviders())
    var errors = 0
    val updates = mutableListOf<Pair<Path, String>>()
    try {
        for (file in files) {
            val code = Code.fromFile(file.toFile())
            try {
                val result = if (format) {
                    engine.format(code, callback = { AutocorrectDecision.ALLOW_AUTOCORRECT })
                } else {
                    code.content
                }
                // Check the result as well: not every lint violation can be corrected automatically.
                engine.lint(if (format) Code.fromSnippetWithPath(result, file) else code) { error ->
                    System.err.println("$file:${error.line}:${error.col}: ${error.detail} (${error.ruleId.value})")
                    errors++
                }
                if (format && result != code.content) updates += file to result
            } catch (error: KtLintParseException) {
                throw IllegalArgumentException(
                    "Cannot parse Kotlin source '$file': ${error.message}. " +
                        "The source may use syntax unsupported by the embedded ktlint parser.",
                    error,
                )
            }
        }
        // Parse and format all files first, so a syntax error cannot cause a partial format run.
        for ((file, result) in updates) {
            file.writeText(result)
            println("Formatted ${moduleRoot.relativize(file)}")
        }
        check(errors == 0) {
            "ktlint found $errors violation(s). Run './kotlin do ktlintFormat' for automatic fixes; " +
                "remaining violations require manual changes."
        }
        println("ktlint checked ${files.size} Kotlin file(s); formatted ${updates.size}.")
    } finally {
        engine.trimMemory()
    }
}
