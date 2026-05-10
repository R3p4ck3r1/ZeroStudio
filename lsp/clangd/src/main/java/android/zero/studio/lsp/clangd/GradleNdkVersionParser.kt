package android.zero.studio.lsp.clangd

import java.io.File

/** Extracts android.ndkVersion from Groovy DSL and Kotlin DSL Gradle build files. */
object GradleNdkVersionParser {
  private val files = listOf("build.gradle.kts", "build.gradle")
  private val patterns = listOf(
      Regex("""\bndkVersion\s*=\s*[\"']([^\"']+)[\"']"""),
      Regex("""\bndkVersion\s+?[\(]?\s*[\"']([^\"']+)[\"']\s*[\)]?"""),
  )

  fun findNdkVersion(projectDir: File): String? {
    if (!projectDir.isDirectory) return null
    return projectDir.walkTopDown()
        .onEnter { it.name != ".git" && it.name != "build" && it.name != ".gradle" }
        .filter { it.isFile && it.name in files }
        .mapNotNull(::parse)
        .firstOrNull()
  }

  fun parse(file: File): String? = runCatching { parseText(file.readText()) }.getOrNull()

  fun parseText(text: String): String? {
    val withoutBlockComments = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
    val withoutLineComments = withoutBlockComments.lineSequence().joinToString("\n") { line ->
      val index = line.indexOf("//")
      if (index >= 0) line.substring(0, index) else line
    }
    return patterns.firstNotNullOfOrNull { it.find(withoutLineComments)?.groupValues?.getOrNull(1) }
  }
}
