package dev.ktc.plugins.ktlint

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FormattingTest {
    private val settings = object : KtlintSettings {}

    private fun inModule(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("ktc-format-test-")
        root.resolve(".editorconfig").writeText("root = true\n\n[*.{kt,kts}]\nindent_size = 4\n")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun checkFailsWithoutChangingBytesThenFormatIsIdempotent() = inModule { root ->
        val file = root.resolve("Example.kt")
        val original = "fun answer( )=42\n"
        file.writeText(original)
        assertFailsWith<IllegalStateException> { runKtlint(root, settings, format = false) }
        assertEquals(original, file.readText())
        runKtlint(root, settings, format = true)
        val formatted = file.readText()
        assertNotEquals(original, formatted)
        runKtlint(root, settings, format = false)
        runKtlint(root, settings, format = true)
        assertEquals(formatted, file.readText())
    }

    @Test
    fun parseFailureDoesNotPartiallyFormatOtherFiles() = inModule { root ->
        val original = "fun answer( )=42\n"
        root.resolve("A.kt").writeText(original)
        root.resolve("Z.kt").writeText("fun broken( {\n")
        val error = assertFailsWith<IllegalArgumentException> { runKtlint(root, settings, format = true) }
        assertTrue(error.message.orEmpty().contains(root.resolve("Z.kt").toString()))
        assertTrue(error.cause is com.pinterest.ktlint.rule.engine.api.KtLintParseException)
        assertEquals(original, root.resolve("A.kt").readText())
    }

    @Test
    fun failedCheckReportsSourcePathAndPreservesBytes() = inModule { root ->
        val file = root.resolve("Broken.kt")
        val original = "fun broken( {\n"
        file.writeText(original)
        val error = assertFailsWith<IllegalArgumentException> { runKtlint(root, settings, format = false) }
        assertTrue(error.message.orEmpty().contains(file.toString()))
        assertTrue(error.cause is com.pinterest.ktlint.rule.engine.api.KtLintParseException)
        assertEquals(original, file.readText())
    }

    @Test
    fun formatsKotlinScripts() = inModule { root ->
        root.resolve("script.kts").writeText("println( 42 )\n")
        runKtlint(root, settings, format = true)
        runKtlint(root, settings, format = false)
    }

    @Test
    fun editorConfigIsHonoredAndChangesAreObserved() = inModule { root ->
        val file = root.resolve("Example.kt")
        file.writeText("fun answer() {\n    println(42)\n}\n")
        runKtlint(root, settings, format = false)
        root.resolve(".editorconfig").writeText("root = true\n\n[*.{kt,kts}]\nindent_size = 2\n")
        assertFailsWith<IllegalStateException> { runKtlint(root, settings, format = false) }
        runKtlint(root, settings, format = true)
        assertEquals("fun answer() {\n  println(42)\n}\n", file.readText())
    }
}
