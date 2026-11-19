package app.radiorecalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesParityTest {

    private fun resolveResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate res directory from ${File(".").absolutePath}")
    }

    private fun parseStringEntries(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val entries = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            assertTrue("Empty string name in ${file.path}", name.isNotBlank())
            assertTrue("Duplicate string key '$name' in ${file.path}", !entries.containsKey(name))
            entries[name] = el.textContent
        }
        return entries
    }

    private fun extractFormatSpecifiers(text: String): List<String> {
        val regex = Regex("""%\d+\$[sd]""")
        return regex.findAll(text).map { it.value }.sorted().toList()
    }

    @Test
    fun `all locale strings files have identical keys and placeholders`() {
        val resDir = resolveResDir()
        val baseFile = File(resDir, "values/strings.xml")
        assertTrue("Missing base strings.xml at ${baseFile.path}", baseFile.exists())

        val baseEntries = parseStringEntries(baseFile)
        val localeFiles = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-") && File(file, "strings.xml").exists()
        }?.map { File(it, "strings.xml") }?.sortedBy { it.parentFile?.name ?: it.name } ?: emptyList()

        assertTrue("Expected at least one locale strings.xml", localeFiles.isNotEmpty())

        for (localeFile in localeFiles) {
            val localeName = localeFile.parentFile?.name ?: localeFile.name
            val localeEntries = parseStringEntries(localeFile)

            val missingKeys = baseEntries.keys - localeEntries.keys
            assertEquals("Missing keys in $localeName/strings.xml", emptySet<String>(), missingKeys)

            val extraKeys = localeEntries.keys - baseEntries.keys
            assertEquals("Extra keys in $localeName/strings.xml", emptySet<String>(), extraKeys)

            for ((key, baseValue) in baseEntries) {
                val localeValue = localeEntries.getValue(key)
                assertEquals(
                    "Format specifier mismatch for '$key' in $localeName/strings.xml",
                    extractFormatSpecifiers(baseValue),
                    extractFormatSpecifiers(localeValue)
                )
            }
        }
    }
}
