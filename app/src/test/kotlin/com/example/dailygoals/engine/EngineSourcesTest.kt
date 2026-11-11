package com.example.dailygoals.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSourcesTest {
    private val root = File("src/main/kotlin/com/example/dailygoals")
    private val platformImport = Regex("""^\s*import\s+(android|androidx)\.""")

    @Test
    fun engineAndModelHaveNoPlatformImports() {
        val sources = listOf("engine", "model").flatMap { File(root, it).walkTopDown().filter(File::isFile) }
        assertTrue("No sources found under ${root.absolutePath}", sources.isNotEmpty())
        val offenders = sources.filter { file -> file.readLines().any(platformImport::containsMatchIn) }
        assertEquals(emptyList<File>(), offenders)
    }
}
