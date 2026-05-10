package android.zero.studio.lsp.clangd

import android.os.Build
import com.itsaky.androidide.utils.Environment
import java.io.File

/** Resolves Android NDK clang toolchains from ANDROID_HOME/ANDROID_NDK_HOME. */
object NdkToolchainLocator {
  private const val PREBUILT_RELATIVE = "toolchains/llvm/prebuilt"

  data class NdkInstall(
      val version: String,
      val root: File,
      val usable: Boolean,
      val toolchains: List<File>,
      val problem: String? = null,
  )

  data class Toolchain(
      val ndkVersion: String,
      val ndkRoot: File,
      val prebuiltDir: File,
      val binDir: File,
      val clangd: File,
      val clang: File,
      val clangxx: File,
      val clangFormat: File,
      val clangTidy: File,
      val llvmAr: File,
      val selectedBy: SelectionSource,
  )

  enum class SelectionSource { GRADLE_NDK_VERSION, USER_SETTING, FIRST_USABLE, FIRST_DETECTED }

  fun ndkHome(): File = Environment.ANDROID_NDK_HOME ?: File(Environment.ANDROID_HOME, "ndk")

  fun installedNdks(): List<NdkInstall> {
    val root = ndkHome()
    if (!root.isDirectory) return emptyList()
    return root.listFiles { file -> file.isDirectory }
        ?.map { dir ->
          val toolchains = File(dir, PREBUILT_RELATIVE)
              .listFiles { file -> file.isDirectory && file.name.startsWith("linux-") }
              ?.sortedWith(prebuiltComparator())
              .orEmpty()
          val usableToolchains = toolchains.filter { File(it, "bin/clangd").isFile }
          NdkInstall(
              version = dir.name,
              root = dir,
              usable = usableToolchains.isNotEmpty(),
              toolchains = toolchains,
              problem = when {
                toolchains.isEmpty() -> "missing $PREBUILT_RELATIVE/linux-*"
                usableToolchains.isEmpty() -> "missing bin/clangd"
                else -> null
              },
          )
        }
        ?.sortedWith(compareByDescending<NdkInstall> { VersionKey.parse(it.version) }.thenBy { it.version })
        .orEmpty()
  }

  @JvmStatic fun getAvailableNdkVersions(): List<String> = installedNdks().map { it.version }

  fun resolve(projectDir: File, settings: ClangdServerSettings = ClangdServerSettings()): Toolchain {
    val gradleVersion = GradleNdkVersionParser.findNdkVersion(projectDir)
    val userVersion = settings.targetNdkVersion?.takeIf { it.isNotBlank() }
    val requested = mutableListOf<Pair<String, SelectionSource>>()
    gradleVersion?.let { requested += it to SelectionSource.GRADLE_NDK_VERSION }
    userVersion?.let { requested += it to SelectionSource.USER_SETTING }
    return resolveRequests(requested)
  }

  fun resolve(preferredVersions: List<String> = emptyList(), preferredSource: SelectionSource = SelectionSource.USER_SETTING): Toolchain =
      resolveRequests(preferredVersions.map { it to preferredSource })

  private fun resolveRequests(preferredVersions: List<Pair<String, SelectionSource>>): Toolchain {
    val installs = installedNdks()
    if (installs.isEmpty()) throw IllegalStateException("No Android NDK versions found in ${ndkHome().absolutePath}")

    val seen = mutableSetOf<String>()
    for ((version, source) in preferredVersions) {
      if (!seen.add(version)) continue
      val install = installs.firstOrNull { it.version == version } ?: continue
      if (install.usable) return resolveInstall(install, source)
    }

    installs.firstOrNull { it.usable }?.let { return resolveInstall(it, SelectionSource.FIRST_USABLE) }
    return resolveInstall(installs.first(), SelectionSource.FIRST_DETECTED)
  }

  private fun resolveInstall(install: NdkInstall, source: SelectionSource): Toolchain {
    val prebuiltRoot = File(install.root, PREBUILT_RELATIVE)
    val prebuilt = selectPrebuilt(prebuiltRoot)
    val bin = File(prebuilt, "bin")
    val clangd = executable(bin, "clangd", required = true)
    return Toolchain(
        ndkVersion = install.version,
        ndkRoot = install.root,
        prebuiltDir = prebuilt,
        binDir = bin,
        clangd = clangd,
        clang = executable(bin, "clang", required = false),
        clangxx = executable(bin, "clang++", required = false),
        clangFormat = executable(bin, "clang-format", required = false),
        clangTidy = executable(bin, "clang-tidy", required = false),
        llvmAr = executable(bin, "llvm-ar", required = false),
        selectedBy = source,
    )
  }

  private fun selectPrebuilt(prebuiltRoot: File): File {
    if (!prebuiltRoot.isDirectory) throw IllegalStateException("LLVM prebuilt directory not found: ${prebuiltRoot.absolutePath}")
    val candidates = prebuiltRoot.listFiles { file -> file.isDirectory && file.name.startsWith("linux-") }.orEmpty()
    if (candidates.isEmpty()) throw IllegalStateException("No linux-{cpu abi} toolchain directory found in ${prebuiltRoot.absolutePath}")
    val sorted = candidates.sortedWith(prebuiltComparator())
    return sorted.firstOrNull { File(it, "bin/clangd").isFile } ?: sorted.first()
  }

  private fun prebuiltComparator(): Comparator<File> {
    val order = preferredLinuxPrebuilts()
    return compareBy<File> { file -> order.indexOf(file.name).let { if (it >= 0) it else Int.MAX_VALUE } }.thenBy { it.name }
  }

  private fun preferredLinuxPrebuilts(): List<String> {
    val mapped = Build.SUPPORTED_ABIS.mapNotNull { abi ->
      when (abi) {
        "arm64-v8a" -> "linux-aarch64"
        "x86_64" -> "linux-x86_64"
        "armeabi-v7a", "armeabi" -> "linux-arm"
        "x86" -> "linux-x86"
        else -> null
      }
    }
    return (mapped + listOf("linux-aarch64", "linux-x86_64", "linux-arm", "linux-x86")).distinct()
  }

  private fun executable(bin: File, name: String, required: Boolean): File {
    val file = File(bin, name)
    if (required && !file.isFile) throw IllegalStateException("Required clang tool missing: ${file.absolutePath}")
    if (file.isFile && !file.canExecute()) file.setExecutable(true)
    return file
  }

  private data class VersionKey(val numbers: List<Int>) : Comparable<VersionKey> {
    override fun compareTo(other: VersionKey): Int {
      val max = maxOf(numbers.size, other.numbers.size)
      for (i in 0 until max) {
        val diff = numbers.getOrElse(i) { 0 } - other.numbers.getOrElse(i) { 0 }
        if (diff != 0) return diff
      }
      return 0
    }
    companion object { fun parse(text: String) = VersionKey(Regex("\\d+").findAll(text).map { it.value.toIntOrNull() ?: 0 }.toList()) }
  }
}
