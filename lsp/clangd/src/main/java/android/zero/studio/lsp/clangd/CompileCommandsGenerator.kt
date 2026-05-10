package android.zero.studio.lsp.clangd

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object CompileCommandsGenerator {
  private val sourceExtensions = setOf("c", "cc", "cpp", "cxx", "m", "mm")

  fun generate(projectDir: File, toolchain: NdkToolchainLocator.Toolchain): File {
    val outputDir = File(projectDir, ".androidide/clangd")
    outputDir.mkdirs()
    val output = File(outputDir, "compile_commands.json")
    val commands = JSONArray()
    projectDir.walkTopDown()
        .onEnter { it.name !in setOf(".git", ".gradle", "build", ".androidide") }
        .filter { it.isFile && it.extension.lowercase() in sourceExtensions }
        .forEach { source -> commands.put(command(projectDir, source, toolchain)) }
    output.writeText(commands.toString(2))
    return output
  }

  private fun command(projectDir: File, source: File, toolchain: NdkToolchainLocator.Toolchain): JSONObject {
    val compiler = if (source.extension.lowercase() == "c") toolchain.clang else toolchain.clangxx
    val args = listOf(
        compiler.absolutePath,
        "--target=${targetTriple()}",
        "--sysroot=${File(toolchain.ndkRoot, "toolchains/llvm/prebuilt/${toolchain.prebuiltDir.name}/sysroot").absolutePath}",
        "-D__ANDROID__",
        "-I${File(projectDir, "src/main/cpp").absolutePath}",
        "-I${File(projectDir, "src/main/jni").absolutePath}",
        "-c",
        source.absolutePath,
    )
    return JSONObject().put("directory", projectDir.absolutePath).put("file", source.absolutePath).put("arguments", JSONArray(args))
  }

  private fun targetTriple(): String = when (android.os.Build.SUPPORTED_ABIS.firstOrNull()) {
    "arm64-v8a" -> "aarch64-linux-android21"
    "armeabi-v7a", "armeabi" -> "armv7a-linux-androideabi21"
    "x86" -> "i686-linux-android21"
    "x86_64" -> "x86_64-linux-android21"
    else -> "aarch64-linux-android21"
  }
}
