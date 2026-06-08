# AGP & Gradle 9.x 升级代码修复示例

本文档提供具体的代码修复示例，作为升级计划的一部分实现参考。

## 1. ProjectTypeDetector.kt 修复

### 1.1 增强插件检测逻辑

```kotlin
// 文件: tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/ProjectTypeDetector.kt

package com.itsaky.androidide.tooling.impl

import java.io.File
import java.util.regex.Pattern

class ProjectTypeDetector {

    /**
     * 检测 AGP 版本（支持新旧语法）
     *
     * 支持的语法:
     * 1. 旧语法: id("com.android.application") version "8.2.0"
     * 2. 新语法: id("com.android.application") version = "8.2.0"
     * 3. buildSrc: plugins { id("com.android.application") }
     */
    fun detectAgpVersion(gradleProject: GradleProject): String? {
        val buildFile = findBuildFile(gradleProject) ?: return null
        val content = buildFile.readText()

        // 尝试新语法 (AGP 8.x+)
        val newSyntaxPattern = Pattern.compile(
            """id\s*\(\s*["']com\.android\.(application|library|feature|dynamic-feature)["']\s*\)\s*version\s*=\s*["']([^"']+)["']""",
            Pattern.MULTILINE
        )
        newSyntaxPattern.matcher(content).find()?.let {
            return it.group(2)
        }

        // 尝试旧语法 (AGP 7.x)
        val oldSyntaxPattern = Pattern.compile(
            """id\s*\(\s*["']com\.android\.(application|library|feature|dynamic-feature)["']\s*\)\s*version\s*["']([^"']+)["']""",
            Pattern.MULTILINE
        )
        oldSyntaxPattern.matcher(content).find()?.let {
            return it.group(2)
        }

        // 尝试 build.gradle 语法
        val gradleSyntaxPattern = Pattern.compile(
            """apply\s+plugin:\s*["']com\.android\.(application|library)["']"""
        )
        if (gradleSyntaxPattern.matcher(content).find()) {
            // 从 classpath 声明中提取版本
            val classpathPattern = Pattern.compile(
                """classpath\s+['"]com\.android\.tools\.build:gradle:([^"']+)['"]"""
            )
            classpathPattern.matcher(content).find()?.let {
                return it.group(1)
            }
        }

        return null
    }

    /**
     * 检测 AGP 9.x 新增的插件类型
     */
    fun detectNewPluginTypes(buildFile: File): Set<String> {
        val plugins = mutableSetOf<String>()
        val content = buildFile.readText()

        // 检测 namespace (AGP 9.x 必需)
        val namespacePattern = Pattern.compile(
            """android\s*\{[^}]*namespace\s*=\s*["']([^"']+)["']""",
            Pattern.DOTALL
        )
        if (namespacePattern.matcher(content).find()) {
            plugins.add("android.namespace")
        }

        // 检测 KMP 配置
        if (content.contains("kotlin {") && content.contains("androidTarget")) {
            plugins.add("android.kotlin.multiplatform")
        }

        // 检测 Jetpack Compose
        if (content.contains("composeOptions")) {
            plugins.add("android.compose")
        }

        // 检测 Non-Transitive R Classes
        val nonTransitivePattern = Pattern.compile(
            """android\s*\{[^}]*nonTransitiveRClass\s*=\s*(true|false)"""
        )
        if (nonTransitivePattern.matcher(content).find()) {
            plugins.add("android.nonTransitiveRClass")
        }

        return plugins
    }

    /**
     * 检测是否启用新的资源处理 (AGP 8.0+)
     */
    fun detectNewResourceProcessing(buildFile: File): Boolean {
        val content = buildFile.readText()
        val pattern = Pattern.compile(
            """android\s*\{[^}]*enableNewResourceShrinker\s*=\s*(true|false)"""
        )
        val matcher = pattern.matcher(content)
        return if (matcher.find()) {
            matcher.group(1).toBoolean()
        } else {
            false // 默认旧行为
        }
    }
}
```

### 1.2 Android 项目类型检测增强

```kotlin
/**
 * 检测 Android 项目类型（支持 AGP 9.x）
 */
fun detectAndroidProjectType(
    gradleProject: GradleProject,
    plugins: Set<String>
): ProjectTypeResult {
    val buildFile = findBuildFile(gradleProject)
    val newPlugins = buildFile?.let { detectNewPluginTypes(it) } ?: emptySet()

    // 确定项目类别
    val category = when {
        plugins.contains("com.android.application") -> ProjectCategory.ANDROID_APP
        plugins.contains("com.android.library") -> ProjectCategory.ANDROID_LIBRARY
        plugins.contains("com.android.feature") -> ProjectCategory.ANDROID_FEATURE
        plugins.contains("com.android.dynamic-feature") -> ProjectCategory.ANDROID_DYNAMIC_FEATURE
        newPlugins.contains("android.namespace") -> ProjectCategory.ANDROID_APP // 默认
        else -> return ProjectTypeResult.Unknown
    }

    // 检测 AGP 版本
    val agpVersion = detectAgpVersion(gradleProject)
    val isPreview = agpVersion?.let {
        it.contains("alpha", ignoreCase = true) ||
        it.contains("beta", ignoreCase = true) ||
        it.contains("rc", ignoreCase = true)
    } ?: false

    // 检测是否使用新版模型 API
    val usesV2Api = agpVersion?.let {
        val major = it.substringBefore(".").toIntOrNull() ?: 0
        major >= 9
    } ?: false

    // 检测构建特性
    val features = mutableSetOf<ProjectFeature>()
    if (newPlugins.contains("android.compose")) {
        features.add(ProjectFeature.Compose)
    }
    if (newPlugins.contains("android.kotlin.multiplatform")) {
        features.add(ProjectFeature.KotlinMultiplatform)
    }

    return ProjectTypeResult(
        category = category,
        agpVersion = agpVersion,
        isPreview = isPreview,
        usesV2Api = usesV2Api,
        features = features
    )
}

/**
 * 项目类型检测结果
 */
data class ProjectTypeResult(
    val category: ProjectCategory,
    val agpVersion: String?,
    val isPreview: Boolean,
    val usesV2Api: Boolean,
    val features: Set<ProjectFeature>
)

enum class ProjectFeature {
    Compose,
    KotlinMultiplatform,
    NewResourceProcessing,
    NonTransitiveRClass
}
```

## 2. AndroidProjectModelBuilder.kt 修复

### 2.1 v2 模型支持

```kotlin
// 文件: tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/sync/AndroidProjectModelBuilder.kt

package com.itsaky.androidide.tooling.impl.sync

import com.android.builder.model.v2.ide.AndroidProject as V2AndroidProject
import com.android.builder.model.v2.ide.ProjectInfo as V2ProjectInfo
import com.android.builder.model.AndroidProject as V1AndroidProject
import org.gradle.tooling.model.gradle.GradleBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 项目模型构建器 (支持 AGP 9.x v2 API)
 */
class AndroidProjectModelBuilder(
    private val projectConnection: ProjectConnection,
    private val agpVersion: String,
    private val logger: Logger
) {

    /**
     * 构建 Android 项目模型
     *
     * 优先级:
     * 1. AGP 9.x v2 模型
     * 2. AGP 8.x v1 模型
     * 3. 降级方案
     */
    suspend fun build(): AndroidProjectModel = withContext(Dispatchers.IO) {
        logger.info("Building Android project model with AGP $agpVersion")

        // 尝试使用 v2 API (AGP 9.x)
        if (isAgp9OrHigher()) {
            try {
                return@withContext buildV2Model()
            } catch (e: Exception) {
                logger.warn("Failed to build v2 model, falling back to v1", e)
            }
        }

        // 回退到 v1 API
        buildV1Model()
    }

    private fun isAgp9OrHigher(): Boolean {
        val major = agpVersion.substringBefore(".").toIntOrNull() ?: 0
        return major >= 9
    }

    /**
     * 使用 v2 API 构建模型 (AGP 9.x)
     */
    private fun buildV2Model(): AndroidProjectModel {
        // 获取 v2 项目信息
        val v2Project = projectConnection.getModel(V2AndroidProject::class.java)

        // 获取项目信息 (AGP 9.x 新增)
        val projectInfo = try {
            projectConnection.getModel(V2ProjectInfo::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to get ProjectInfo", e)
            null
        }

        // 获取构建变体
        val variants = fetchVariantsV2(v2Project)

        // 获取依赖
        val dependencies = fetchDependenciesV2(v2Project)

        // 构建模型
        return AndroidProjectModel(
            name = projectInfo?.projectName ?: v2Project.name,
            path = projectInfo?.projectPath ?: ":",
            namespace = projectInfo?.namespace ?: v2Project.namespace,
            description = projectInfo?.description,
            compileSdkVersion = v2Project.compileSdkVersion,
            compileSdkExtensionVersion = v2Project.sdkExtensionVersion,
            minSdkVersion = v2Project.minSdkVersion,
            targetSdkVersion = v2Project.targetSdkVersion,
            versionCode = extractVersionCode(v2Project),
            versionName = extractVersionName(v2Project),
            applicationId = extractApplicationId(v2Project),
            variants = variants,
            dependencies = dependencies,
            buildFeatures = buildFeaturesV2(v2Project),
            lintOptions = extractLintOptions(v2Project),
            javaCompileOptions = extractJavaCompileOptions(v2Project),
            isPreview = agpVersion.contains("alpha") || agpVersion.contains("beta"),
            isV2Model = true
        )
    }

    /**
     * 使用 v1 API 构建模型 (AGP 8.x)
     */
    private fun buildV1Model(): AndroidProjectModel {
        val v1Project = projectConnection.getModel(V1AndroidProject::class.java)

        val variants = fetchVariantsV1(v1Project)
        val dependencies = fetchDependenciesV1(v1Project)

        return AndroidProjectModel(
            name = v1Project.name,
            path = ":",
            namespace = v1Project.namespace,
            description = null,
            compileSdkVersion = v1Project.compileSdkVersion,
            compileSdkExtensionVersion = v1Project.sdkExtensionVersion,
            minSdkVersion = v1Project.minSdkVersion,
            targetSdkVersion = v1Project.targetSdkVersion,
            versionCode = null,
            versionName = null,
            applicationId = null,
            variants = variants,
            dependencies = dependencies,
            buildFeatures = buildFeaturesV1(v1Project),
            lintOptions = extractLintOptionsV1(v1Project),
            javaCompileOptions = v1Project.javaCompileOptions,
            isPreview = agpVersion.contains("alpha") || agpVersion.contains("beta"),
            isV2Model = false
        )
    }

    /**
     * 获取构建特性 (v2)
     */
    private fun buildFeaturesV2(project: V2AndroidProject): BuildFeatures {
        val flags = project.flags
        return BuildFeatures(
            buildConfig = flags.isBuildConfigEnabled,
            buildConfigFieldMap = extractBuildConfigFields(project),
            resValues = extractResValues(project),
            manifest = extractManifest(project),
            // AGP 9.x 新增特性
            enableResourceValidation = flags.isResourceValidationEnabled,
            enableParallelExecution = flags.isParallelExecutionEnabled,
            enableConfigurationCache = flags.isConfigurationCacheEnabled
        )
    }

    /**
     * 获取构建变体 (v2)
     */
    private fun fetchVariantsV2(project: V2AndroidProject): List<VariantInfo> {
        return try {
            val buildVariants = projectConnection.getModel(
                Class.forName("com.android.builder.model.v2.ModelsKt")
                        .getMethod("getVariants", V2AndroidProject::class.java)
                        .invoke(null, project) as List<*>
            )

            buildVariants.mapNotNull { variant ->
                buildVariantInfo(variant)
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch variants", e)
            emptyList()
        }
    }

    /**
     * 获取依赖 (v2)
     */
    private fun fetchDependenciesV2(project: V2AndroidProject): List<DependencyInfo> {
        return try {
            projectConnection.getModel(
                Class.forName("com.android.builder.model.v2.ide.Dependencies")
            ).let { deps ->
                if (deps is Iterable<*>) {
                    deps.mapNotNull { dep ->
                        buildDependencyInfo(dep)
                    }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch dependencies", e)
            emptyList()
        }
    }
}

/**
 * Android 项目模型 (内部使用)
 */
data class AndroidProjectModel(
    val name: String,
    val path: String,
    val namespace: String?,
    val description: String?,
    val compileSdkVersion: Int,
    val compileSdkExtensionVersion: Int,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val versionCode: Int?,
    val versionName: String?,
    val applicationId: String?,
    val variants: List<VariantInfo>,
    val dependencies: List<DependencyInfo>,
    val buildFeatures: BuildFeatures,
    val lintOptions: LintOptions?,
    val javaCompileOptions: Any?, // JavaCompileOptions
    val isPreview: Boolean,
    val isV2Model: Boolean
)

/**
 * 构建变体信息
 */
data class VariantInfo(
    val name: String,
    val type: VariantType,
    val applicationId: String?,
    val versionCode: Int?,
    val versionName: String?,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val sourceSets: Map<String, SourceSetInfo>,
    val artifacts: List<ArtifactInfo>,
    val signingConfig: SigningConfigInfo?
)

enum class VariantType {
    DEBUG,
    RELEASE,
    ANDROID_TEST,
    UNIT_TEST
}

/**
 * 构建特性
 */
data class BuildFeatures(
    val buildConfig: Boolean,
    val buildConfigFieldMap: Map<String, String>,
    val resValues: List<ResValueInfo>,
    val manifest: ManifestInfo?,
    val enableResourceValidation: Boolean = true,
    val enableParallelExecution: Boolean = true,
    val enableConfigurationCache: Boolean = false
)
```

### 2.2 版本兼容性处理

```kotlin
/**
 * AGP 版本兼容性助手
 */
object AgpCompatibilityHelper {

    /**
     * 检查 AGP 版本是否支持特定功能
     */
    fun supportsFeature(agpVersion: String, feature: AgpFeature): Boolean {
        val (minVersion, maxVersion) = feature.versionRange

        return isVersionInRange(agpVersion, minVersion, maxVersion)
    }

    /**
     * 获取给定 AGP 版本的建议 Gradle 版本
     */
    fun getRecommendedGradleVersion(agpVersion: String): String {
        return when {
            isVersionAtLeast(agpVersion, "9.3.0") -> "9.5"
            isVersionAtLeast(agpVersion, "9.0.0") -> "8.9"
            isVersionAtLeast(agpVersion, "8.7.0") -> "8.7"
            isVersionAtLeast(agpVersion, "8.5.0") -> "8.6"
            isVersionAtLeast(agpVersion, "8.3.0") -> "8.4"
            isVersionAtLeast(agpVersion, "8.2.0") -> "8.2"
            isVersionAtLeast(agpVersion, "8.1.0") -> "8.0"
            isVersionAtLeast(agpVersion, "8.0.0") -> "8.0"
            else -> "7.6" // AGP 7.x
        }
    }

    /**
     * 获取给定 Gradle 版本的最低 AGP 版本
     */
    fun getMinimumAgpVersion(gradleVersion: String): String {
        return when {
            isVersionAtLeast(gradleVersion, "9.0.0") -> "9.0.0"
            isVersionAtLeast(gradleVersion, "8.9.0") -> "8.7.0"
            isVersionAtLeast(gradleVersion, "8.7.0") -> "8.5.0"
            isVersionAtLeast(gradleVersion, "8.5.0") -> "8.3.0"
            isVersionAtLeast(gradleVersion, "8.4.0") -> "8.2.0"
            isVersionAtLeast(gradleVersion, "8.0.0") -> "8.0.0"
            else -> "7.4.0" // Gradle 7.x
        }
    }

    /**
     * 版本比较
     */
    fun isVersionAtLeast(version: String, minVersion: String): Boolean {
        val v1 = parseVersion(version)
        val v2 = parseVersion(minVersion)
        return compareVersions(v1, v2) >= 0
    }

    fun isVersionInRange(version: String, minVersion: String, maxVersion: String): Boolean {
        return isVersionAtLeast(version, minVersion) && !isVersionAtLeast(version, maxVersion)
    }

    private fun parseVersion(version: String): List<Int> {
        return version.split("-")[0] // 去掉预发布标签
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }

    private fun compareVersions(v1: List<Int>, v2: List<Int>): Int {
        val maxLen = maxOf(v1.size, v2.size)
        val paddedV1 = v1 + List(maxLen - v1.size) { 0 }
        val paddedV2 = v2 + List(maxLen - v2.size) { 0 }

        for (i in 0 until maxLen) {
            if (paddedV1[i] != paddedV2[i]) {
                return paddedV1[i].compareTo(paddedV2[i])
            }
        }
        return 0
    }
}

/**
 * AGP 功能特性
 */
enum class AgpFeature(
    val versionRange: Pair<String, String>
) {
    V2_MODELS("9.0.0", "10.0.0"),
    COMPOSE_MULTIPLATFORM("8.2.0", "10.0.0"),
    NAMESPACE_REQUIRED("8.0.0", "10.0.0"),
    KOTLIN_DSL("7.0.0", "10.0.0"),
    BUILD_CONFIG_CLASS("4.0.0", "10.0.0"),
    NEW_RESOURCE_SHRINKER("8.0.0", "10.0.0"),
    CONFIGURATION_CACHE("8.3.0", "10.0.0"),
    PARALLEL_EXECUTION("3.0.0", "10.0.0")
}
```

## 3. GradleBuildService.kt 修复

### 3.1 版本检测与验证

```kotlin
// 文件: core/app/src/main/java/com/itsaky/androidide/services/builder/GradleBuildService.kt

package com.itsaky.androidide.services.builder

import kotlinx.coroutines.*
import java.io.File
import java.util.Properties

class GradleBuildService : AutoCloseable {

    private var server: ToolingApiServerImpl? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentGradleVersion: String? = null
    private var currentAgpVersion: String? = null

    /**
     * 同步项目
     */
    suspend fun syncProject(
        projectDir: File,
        listener: SyncProgressListener
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            listener.onStart()

            // 1. 检测 Gradle 版本
            currentGradleVersion = detectGradleVersion(projectDir)
            listener.onProgress("Detected Gradle $currentGradleVersion")

            // 2. 检测 AGP 版本
            currentAgpVersion = detectAgpVersion(projectDir)
            listener.onProgress("Detected AGP $currentAgpVersion")

            // 3. 验证版本兼容性
            validateVersionCompatibility()

            // 4. 启动 Tooling Server
            listener.onProgress("Starting build service...")
            startToolingServer(projectDir)

            // 5. 执行同步
            listener.onProgress("Syncing project model...")
            val projectModel = server!!.fetchProjectModel()

            // 6. 转换模型
            val internalModel = convertToInternalModel(projectModel)

            listener.onComplete()
            SyncResult.Success(internalModel)

        } catch (e: VersionIncompatibleException) {
            listener.onError(e.message ?: "Version incompatibility")
            SyncResult.VersionError(e.message ?: "Unknown version error")

        } catch (e: SyncException) {
            handleSyncError(e, listener)
            SyncResult.Failure(e)

        } catch (e: Exception) {
            listener.onError("Unexpected error: ${e.message}")
            SyncResult.Failure(SyncException("Sync failed", e))
        }
    }

    /**
     * 检测 Gradle Wrapper 版本
     */
    private fun detectGradleVersion(projectDir: File): String {
        val wrapperProps = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperProps.exists()) {
            throw SyncException("gradle-wrapper.properties not found")
        }

        val content = wrapperProps.readText()
        val pattern = Regex("""distributionUrl=.*gradle-(\d+\.\d+(?:\.\d+)?).*""")
        val match = pattern.find(content)
            ?: throw SyncException("Cannot detect Gradle version from wrapper")

        return match.groupValues[1]
    }

    /**
     * 检测 AGP 版本
     */
    private fun detectAgpVersion(projectDir: File): String {
        // 优先从 settings.gradle.kts 检测
        val settingsFile = File(projectDir, "settings.gradle.kts")
        if (settingsFile.exists()) {
            val content = settingsFile.readText()

            // 尝试 pluginManagement
            val pluginManagementPattern = Regex(
                """pluginManagement\s*\{[^}]*plugins\s*\{[^}]*id\s*\(\s*"com\.android\.application"\s*\)[^}]*version\s*=\s*"([^"]+)"""",
                RegexOption.MULTILINE
            )
            pluginManagementPattern.find(content)?.let {
                return it.groupValues[1]
            }
        }

        // 从 build.gradle.kts 检测
        val buildFile = File(projectDir, "build.gradle.kts")
        if (buildFile.exists()) {
            val content = buildFile.readText()

            val pluginsPattern = Regex(
                """plugins\s*\{[^}]*id\s*\(\s*"com\.android\.application"\s*\)[^}]*version\s*=\s*"([^"]+)""",
                RegexOption.MULTILINE
            )
            pluginsPattern.find(content)?.let {
                return it.groupValues[1]
            }
        }

        // 从 build.gradle 检测 (Groovy)
        val groovyBuildFile = File(projectDir, "build.gradle")
        if (groovyBuildFile.exists()) {
            val content = groovyBuildFile.readText()

            val pluginPattern = Regex(
                """plugins\s*\{[^}]*id\s*\(\s*["']com\.android\.application["']\s*\)\s*version\s*["']([^"']+)["']"""
            )
            pluginPattern.find(content)?.let {
                return it.groupValues[1]
            }

            // 尝试 buildscript
            val classpathPattern = Regex(
                """classpath\s+['"]com\.android\.tools\.build:gradle:([^"']+)['"]"""
            )
            classpathPattern.find(content)?.let {
                return it.groupValues[1]
            }
        }

        // 默认值
        return "8.2.0"
    }

    /**
     * 验证版本兼容性
     */
    private fun validateVersionCompatibility() {
        val gradle = currentGradleVersion ?: return
        val agp = currentAgpVersion ?: return

        // Gradle 版本检查
        val minGradleForAgp9 = "8.7"
        val minGradleForAgp83 = "8.4"
        val minGradleForAgp8 = "8.0"

        // AGP 版本检查
        val minAgpForGradle9 = "9.0.0"
        val minAgpForGradle85 = "8.5.0"

        // 执行验证
        when {
            // AGP 9.x 需要 Gradle 8.7+
            AgpCompatibilityHelper.isVersionAtLeast(agp, "9.0.0") &&
            !AgpCompatibilityHelper.isVersionAtLeast(gradle, minGradleForAgp9) -> {
                throw VersionIncompatibleException(
                    "AGP $agp requires Gradle $minGradleForAgp9 or higher. " +
                    "Current Gradle version: $gradle"
                )
            }

            // Gradle 9.x 需要 AGP 9.0.0+
            AgpCompatibilityHelper.isVersionAtLeast(gradle, "9.0.0") &&
            !AgpCompatibilityHelper.isVersionAtLeast(agp, minAgpForGradle9) -> {
                throw VersionIncompatibleException(
                    "Gradle $gradle requires AGP $minAgpForGradle9 or higher. " +
                    "Current AGP version: $agp"
                )
            }

            // 其他版本检查...
            else -> {
                // 检查推荐版本
                val recommendedGradle = AgpCompatibilityHelper.getRecommendedGradleVersion(agp)
                if (!AgpCompatibilityHelper.isVersionAtLeast(gradle, recommendedGradle)) {
                    log.warn(
                        "AGP $agp works best with Gradle $recommendedGradle or higher. " +
                        "Current Gradle: $gradle"
                    )
                }
            }
        }
    }

    /**
     * 启动 Tooling Server
     */
    private fun startToolingServer(projectDir: File) {
        server?.close()

        val gradle = currentGradleVersion!!
        val agp = currentAgpVersion!!

        server = ToolingApiServerImpl().apply {
            initialize(
                projectDir = projectDir,
                gradleVersion = gradle,
                agpVersion = agp,
                // AGP 9.x 需要额外的配置
                extraOptions = buildServerOptions(gradle, agp)
            )
        }
    }

    /**
     * 构建服务器额外选项
     */
    private fun buildServerOptions(gradle: String, agp: String): Map<String, String> {
        val options = mutableMapOf<String, String>()

        // AGP 9.x 选项
        if (AgpCompatibilityHelper.isVersionAtLeast(agp, "9.0.0")) {
            options["android.enableResourceValidation"] = "false"
            options["android.useAndroidX"] = "true"
            options["android.enableJetifier"] = "false"
        }

        // Gradle 9.x 选项
        if (AgpCompatibilityHelper.isVersionAtLeast(gradle, "9.0.0")) {
            options["org.gradle.jvmargs"] = "-Xmx4g -XX:MaxMetaspaceSize=512m"
        }

        return options
    }

    override fun close() {
        scope.cancel()
        server?.close()
    }
}

/**
 * 版本不兼容异常
 */
class VersionIncompatibleException(message: String) : Exception(message)

/**
 * 同步异常
 */
class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 同步进度监听器
 */
interface SyncProgressListener {
    fun onStart()
    fun onProgress(message: String)
    fun onComplete()
    fun onError(message: String)
}

/**
 * 同步结果
 */
sealed class SyncResult {
    data class Success(val project: ProjectData) : SyncResult()
    data class Failure(val error: Throwable) : SyncResult()
    data class VersionError(val message: String) : SyncResult()
}
```

## 4. 错误处理增强

### 4.1 诊断信息收集

```kotlin
/**
 * 构建服务诊断助手
 */
object BuildDiagnostics {

    /**
     * 收集诊断信息
     */
    fun collectDiagnostics(projectDir: File): DiagnosticsReport {
        return DiagnosticsReport(
            gradleVersion = detectGradleVersion(projectDir),
            gradleWrapperPresent = checkGradleWrapper(projectDir),
            agpVersion = detectAgpVersion(projectDir),
            javaVersion = System.getProperty("java.version"),
            javaHome = System.getProperty("java.home"),
            osName = System.getProperty("os.name"),
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            maxMemory = Runtime.getRuntime().maxMemory(),
            gradleProperties = loadGradleProperties(projectDir),
            localProperties = loadLocalProperties(projectDir),
            commonIssues = detectCommonIssues(projectDir)
        )
    }

    /**
     * 检测常见问题
     */
    private fun detectCommonIssues(projectDir: File): List<DiagnosticIssue> {
        val issues = mutableListOf<DiagnosticIssue>()

        // 检查 Gradle Wrapper
        val wrapperDir = File(projectDir, "gradle/wrapper")
        if (!wrapperDir.exists()) {
            issues.add(
                DiagnosticIssue(
                    severity = Severity.ERROR,
                    category = "Configuration",
                    message = "gradle-wrapper.properties not found",
                    suggestion = "Run 'gradle wrapper' to generate the wrapper"
                )
            )
        }

        // 检查 Java 版本
        val javaVersion = System.getProperty("java.version")
        if (javaVersion.startsWith("1.")) {
            issues.add(
                DiagnosticIssue(
                    severity = Severity.WARNING,
                    category = "Java",
                    message = "Java 8 or lower detected",
                    suggestion = "Android Gradle Plugin 8.0+ requires Java 17+"
                )
            )
        }

        // 检查 AGP 版本与 Gradle 版本匹配
        val gradle = detectGradleVersion(projectDir)
        val agp = detectAgpVersion(projectDir)

        if (!AgpCompatibilityHelper.isVersionAtLeast(gradle, "8.7") &&
            AgpCompatibilityHelper.isVersionAtLeast(agp, "9.0.0")) {
            issues.add(
                DiagnosticIssue(
                    severity = Severity.ERROR,
                    category = "Version",
                    message = "AGP 9.x requires Gradle 8.7+",
                    suggestion = "Update Gradle to 8.7 or higher in gradle-wrapper.properties"
                )
            )
        }

        return issues
    }

    /**
     * 生成诊断报告
     */
    fun generateReport(projectDir: File): String {
        val report = collectDiagnostics(projectDir)

        return buildString {
            appendLine("=== AndroidIDE Build Diagnostics ===")
            appendLine()
            appendLine("## Environment")
            appendLine("- Java: ${report.javaVersion}")
            appendLine("- Java Home: ${report.javaHome}")
            appendLine("- OS: ${report.osName}")
            appendLine("- Processors: ${report.availableProcessors}")
            appendLine("- Max Memory: ${report.maxMemory / 1024 / 1024}MB")
            appendLine()
            appendLine("## Project")
            appendLine("- Gradle: ${report.gradleVersion}")
            appendLine("- AGP: ${report.agpVersion}")
            appendLine("- Gradle Wrapper: ${if (report.gradleWrapperPresent) "✓" else "✗"}")
            appendLine()

            if (report.commonIssues.isNotEmpty()) {
                appendLine("## Issues Detected")
                report.commonIssues.forEach { issue ->
                    appendLine("- [${issue.severity}] ${issue.category}: ${issue.message}")
                    appendLine("  Suggestion: ${issue.suggestion}")
                }
            }

            appendLine()
            appendLine("## Gradle Properties")
            report.gradleProperties.forEach { (k, v) ->
                appendLine("- $k: $v")
            }

            return@buildString toString()
        }
    }
}

data class DiagnosticsReport(
    val gradleVersion: String,
    val gradleWrapperPresent: Boolean,
    val agpVersion: String,
    val javaVersion: String,
    val javaHome: String,
    val osName: String,
    val availableProcessors: Int,
    val maxMemory: Long,
    val gradleProperties: Map<String, String>,
    val localProperties: Map<String, String>,
    val commonIssues: List<DiagnosticIssue>
)

data class DiagnosticIssue(
    val severity: Severity,
    val category: String,
    val message: String,
    val suggestion: String
)

enum class Severity {
    INFO,
    WARNING,
    ERROR
}
```

---

## 测试用例

```kotlin
// test/VersionCompatibilityTest.kt

class VersionCompatibilityTest {

    @Test
    fun `test AGP 9 requires Gradle 87`() {
        assertThat(AgpCompatibilityHelper.isVersionAtLeast("9.0.0", "8.7.0")).isTrue()
        assertThat(AgpCompatibilityHelper.isVersionAtLeast("9.3.0", "8.7.0")).isTrue()
    }

    @Test
    fun `test Gradle 9 requires AGP 90`() {
        assertThat(AgpCompatibilityHelper.getMinimumAgpVersion("9.0.0")).isEqualTo("9.0.0")
        assertThat(AgpCompatibilityHelper.getMinimumAgpVersion("9.5.0")).isEqualTo("9.0.0")
    }

    @Test
    fun `test recommended Gradle version for AGP`() {
        assertThat(AgpCompatibilityHelper.getRecommendedGradleVersion("9.3.0"))
            .isEqualTo("9.5")
        assertThat(AgpCompatibilityHelper.getRecommendedGradleVersion("8.7.0"))
            .isEqualTo("8.9")
        assertThat(AgpCompatibilityHelper.getRecommendedGradleVersion("8.2.0"))
            .isEqualTo("8.2")
    }

    @Test
    fun `test version comparison`() {
        assertThat(AgpCompatibilityHelper.isVersionAtLeast("9.0.0", "9.0.0")).isTrue()
        assertThat(AgpCompatibilityHelper.isVersionAtLeast("9.0.1", "9.0.0")).isTrue()
        assertThat(AgpCompatibilityHelper.isVersionAtLeast("9.1.0", "9.0.0")).isTrue()
        assertThat(AgpCompatibilityHelper.isVersionAtLeast("8.9.0", "9.0.0")).isFalse()
    }
}
```

---

**文档版本**: 1.0
**创建日期**: 2026-06-06
