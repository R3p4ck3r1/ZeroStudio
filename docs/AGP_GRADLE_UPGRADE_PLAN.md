# AGP & Gradle 9.x 升级支持详细计划

## 📋 执行摘要

本文档详细描述了将 AndroidIDE 构建服务从当前版本升级以全面支持 **Android Gradle Plugin (AGP) 9.3.0** 和 **Gradle 9.x** 的完整计划。

---

## 1. 当前状态分析

### 1.1 版本配置现状

| 组件 | 当前版本 | 目标版本 | 说明 |
|------|---------|---------|------|
| **AGP (项目构建)** | 8.13.2 | 9.3.0 | 项目本身的构建插件 |
| **AGP Tooling SDK** | 9.3.0-alpha06 | 9.3.0+ | 构建服务的 SDK 依赖 |
| **Gradle Tooling API** | 9.5.1 | 9.5.1+ | Gradle Tooling API |
| **Gradle Wrapper** | 8.x | 9.0.0+ | Gradle 运行版本 |

### 1.2 核心问题识别

#### 🔴 严重问题 (P0)

1. **API 模型不兼容**
   - AGP 9.x 引入了全新的 **v2 模型 API** (`com.android.builder.model.v2`)
   - 现有代码主要使用旧版 v1 API，导致模型获取失败
   - 症状：`DefaultVersions` 引用错误、模型类型不匹配

2. **项目结构识别失败**
   - AGP 9.x 改变了项目结构组织方式
   - `ProjectTypeDetector` 无法正确识别新项目类型
   - 症状：同步失败、项目类型检测错误

3. **构建服务初始化失败**
   - `ToolingApiServerImpl` 与新版 Gradle/AGP 不兼容
   - 缺少必要的初始化参数和回调处理
   - 症状：服务启动失败、连接超时

#### 🟠 重要问题 (P1)

4. **插件检测逻辑过时**
   - AGP 9.x 使用新的插件声明语法
   - 现有正则表达式无法匹配新格式
   - 症状：AGP 版本检测失败

5. **构建变体 (Variant) 模型变化**
   - AGP 9.x 重构了 `Variant`、`AndroidArtifact` 等核心类
   - 现有代码无法正确解析新变体结构
   - 症状：变体列表为空、构建配置丢失

#### 🟡 改进项 (P2)

6. **新特性支持缺失**
   - AGP 9.x 支持新的编译选项和构建标志
   - 项目无法利用新版本的优势
   - 需要更新 `AndroidGradlePluginProjectFlags` 支持

7. **错误处理和诊断不足**
   - 缺少对新版本错误码的识别和处理
   - 调试信息不够详细
   - 需要增强错误报告机制

---

## 2. 技术架构分析

### 2.1 构建服务架构

```
┌─────────────────────────────────────────────────────────────┐
│                    AndroidIDE App (core/app)               │
├─────────────────────────────────────────────────────────────┤
│  GradleBuildService        │  ToolingServerRunner          │
│  (构建服务客户端)            │  (服务进程管理)                │
├─────────────────────────────────────────────────────────────┤
│           ↕ IPC 通信 (gRPC/自定义协议)                      │
├─────────────────────────────────────────────────────────────┤
│              ToolingApiServerImpl (tooling/impl)           │
│              (Gradle Tooling API 服务端)                    │
├─────────────────────────────────────────────────────────────┤
│  ProjectTypeDetector │ AndroidProjectModelBuilder │ Root... │
│  (模型检测与构建)                                           │
├─────────────────────────────────────────────────────────────┤
│     Gradle Tooling API 9.5.1  │  AGP SDK 9.3.0-alpha06    │
│     (com.android.tools.build:builder:builder-model)        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 关键文件清单

#### Tooling 模块 (tooling/impl)

| 文件路径 | 优先级 | 说明 |
|---------|-------|------|
| `ProjectTypeDetector.kt` | P0 | 项目类型检测，需要更新插件检测逻辑 |
| `AndroidProjectModelBuilder.kt` | P0 | Android 项目模型构建，需要适配 v2 API |
| `RootModelBuilder.kt` | P0 | 根项目模型构建，需要支持新结构 |
| `ToolingApiServerImpl.kt` | P0 | Tooling API 服务实现 |
| `AbstractModelBuilder.kt` | P1 | 抽象模型构建器，需要增强版本兼容性 |

#### Core/Projects 模块

| 文件路径 | 优先级 | 说明 |
|---------|-------|------|
| `WorkspaceModelBuilder.kt` | P0 | 工作空间模型构建器 |
| `ProjectManagerImpl.kt` | P1 | 项目管理器实现 |
| `WorkspaceImpl.kt` | P1 | 工作空间实现 |

#### Core/App 模块

| 文件路径 | 优先级 | 说明 |
|---------|-------|------|
| `GradleBuildService.kt` | P0 | Gradle 构建服务 |
| `ToolingServerRunner.kt` | P1 | 服务进程管理 |
| `BuildServiceUtils.kt` | P2 | 构建工具类 |

---

## 3. 详细升级方案

### 3.1 阶段一：版本依赖更新 (1-2 周)

#### 1.1 更新 gradle/libs.versions.toml

```toml
[versions]
# 当前配置
agp = "8.13.2"
agp-tooling = "9.3.0-alpha06"
gradle-tooling = "9.5.1"

# 建议升级配置
agp = "9.3.0"  # 或使用稳定版 9.2.0
agp-tooling = "9.3.0"
gradle-tooling = "9.5.1"
```

#### 1.2 同步更新 AGP SDK 源码

```bash
# 下载最新的 AGP SDK 源码
./gradlew downloadAgpSources

# 或手动更新 gradle/libs/com.android.tools.build/ 目录
```

#### 1.3 更新 Gradle Wrapper

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
```

### 3.2 阶段二：API 适配 (3-4 周)

#### 2.1 更新 ProjectTypeDetector.kt

**文件**: `/workspace/tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/ProjectTypeDetector.kt`

**当前问题**:
```kotlin
// 旧版本正则无法匹配 AGP 9.x 语法
val pluginsPattern = Regex(
  """id\(["']com\.android\.(application|library)["']\)\\s*version\\s*["'](.+?)["']"""
)
```

**修复方案**:
```kotlin
// 支持新旧两种语法
val oldSyntaxPattern = Regex(
  """id\(["']com\.android\.(application|library)["']\)\\s*version\\s*["'](.+?)["']"""
)

val newSyntaxPattern = Regex(
  """plugins\s*\{[^}]*id\(["']com\.android\.(application|library)["']\)[^}]*version\s*=\s*["'](.+?)["']"""
)

// 添加对 buildSrc 和 convention 插件的支持
val buildSrcPattern = Regex(
  """android\s*\{[^}]*namespace\s*=\s*["'](.+?)["']"""
)
```

**新增功能**:
```kotlin
/**
 * 检测 AGP 9.x 新增的插件类型
 */
fun detectNewPluginTypes(buildFile: File): Set<String> {
    val plugins = mutableSetOf<String>()
    val content = buildFile.readText()

    // 检测 namespace（AGP 9.x 必需）
    if (buildSrcPattern.containsMatchIn(content)) {
        plugins.add("android.namespace")
    }

    // 检测 KMP 配置
    if (content.contains("kotlin {") && content.contains("androidTarget")) {
        plugins.add("android.kotlin.multiplatform")
    }

    return plugins
}
```

#### 2.2 更新 AndroidProjectModelBuilder.kt

**文件**: `/workspace/tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/sync/AndroidProjectModelBuilder.kt`

**当前问题**:
```kotlin
// 缺少对 v2 API 的支持
val versions = try {
    model.getModel(Versions::class.java)
} catch (e: Exception) {
    // 无法获取 Versions 模型
    null
}
```

**修复方案**:
```kotlin
/**
 * 支持 AGP 9.x v2 模型 API
 */
class AndroidProjectModelBuilderV2(
    private val connection: ProjectConnection,
    private val agpVersion: String
) : AndroidProjectModelBuilder {

    override suspend fun build(): AndroidProject {
        // 使用 v2 模型获取项目信息
        val v2Project = connection.getModel(AndroidProject::class.java)

        // 处理 AGP 9.x 新增的 ProjectInfo
        val projectInfo = try {
            connection.getModel(ProjectInfo::class.java)
        } catch (e: Exception) {
            null
        }

        // 构建兼容层
        return buildCompatibleModel(v2Project, projectInfo)
    }

    private fun buildCompatibleModel(
        v2Project: AndroidProject,
        projectInfo: ProjectInfo?
    ): AndroidProject {
        // 将 v2 模型转换为内部使用的模型
        return AndroidProjectImpl(
            name = projectInfo?.projectName ?: v2Project.name,
            path = projectInfo?.projectPath ?: v2Project.name,
            description = projectInfo?.description,
            // ... 其他字段映射
            compileSdkVersion = v2Project.compileSdkVersion,
            // AGP 9.x 新增字段
            namespace = projectInfo?.namespace ?: v2Project.namespace,
            androidExtensionEnabled = v2Project.isAndroidExtensionEnabled,
            flags = buildProjectFlags(v2Project)
        )
    }
}
```

**新增的模型适配器**:

```kotlin
/**
 * AGP 9.x ProjectInfo 适配器
 */
data class ProjectInfoAdapter(
    val projectName: String,
    val projectPath: String,
    val namespace: String?,
    val description: String?,
    val workingDirectory: File,
    val buildDirectory: File
)

/**
 * AGP 9.x AndroidProject v2 模型适配器
 */
data class AndroidProjectV2Adapter(
    val name: String,
    val projectType: Int,
    val compileSdkVersion: Int,
    val compileSdkExtensionVersion: Int,
    val namespace: String?,
    val isAndroidExtensionEnabled: Boolean,
    val isBuildConfigGenerationEnabled: Boolean,
    val resourcePrefix: String?,
    val pluginGeneration: Int,
    val defaultConfig: DefaultConfig,
    val flavors: List<ProductFlavor>,
    val buildTypes: List<BuildType>,
    val signingConfigs: Map<String, SigningConfig>,
    val lintOptions: LintOptions?,
    val javaCompileOptions: JavaCompileOptions,
    val aaptOptions: AaptOptions,
    val productOptions: ProductOptions,
    val dynamicFeatures: List<String>
)
```

#### 2.3 更新 RootModelBuilder.kt

**文件**: `/workspace/tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/sync/RootModelBuilder.kt`

**当前问题**:
```kotlin
// 缺少对 Gradle 9.x ProjectGraph 的支持
val androidProject = try {
    model.getModel(AndroidProject::class.java)
} catch (e: Exception) {
    null
}
```

**修复方案**:

```kotlin
/**
 * 支持 Gradle 9.x ProjectGraph
 */
class RootModelBuilderV9(
    private val connection: ProjectConnection,
    private val gradleVersion: String
) : RootModelBuilder {

    override suspend fun build(): ProjectData {
        // 检查是否支持 ProjectGraph (Gradle 9.x)
        val projectGraph = if (isGradle9OrHigher()) {
            try {
                connection.getModel(ProjectGraph::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        // 回退到传统方式
        if (projectGraph == null) {
            return buildLegacyModel()
        }

        // 使用 ProjectGraph 构建模型
        return buildFromProjectGraph(projectGraph)
    }

    private fun isGradle9OrHigher(): Boolean {
        val majorVersion = gradleVersion.substringBefore(".").toIntOrNull() ?: 0
        return majorVersion >= 9
    }

    private fun buildFromProjectGraph(graph: ProjectGraph): ProjectData {
        val projects = graph.projects.map { projectInfo ->
            ProjectInfo(
                name = projectInfo.name,
                path = projectInfo.projectPath,
                description = projectInfo.description,
                buildFile = projectInfo.buildFile,
                projectDirectory = projectInfo.projectDirectory,
                children = projectInfo.children.map { it.name }
            )
        }

        return ProjectData(
            rootProject = projects.firstOrNull { it.path == ":" },
            allProjects = projects
        )
    }
}
```

### 3.3 阶段三：构建服务集成 (2-3 周)

#### 3.1 更新 ToolingApiServerImpl.kt

**文件**: `/workspace/tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/ToolingApiServerImpl.kt`

**关键更新**:

```kotlin
class ToolingApiServerImpl : AutoCloseable {

    private lateinit var gradleServices: GradleBuildServices

    override fun initialize(gradleUserHome: File, projectDir: File) {
        // 支持 AGP 9.x 初始化参数
        val connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .useGradleVersion(gradleVersion)
            .withArguments(
                "--init-script", createInitScript(),
                "-Dorg.gradle.jvmargs=-Xmx4g"
            )

        // AGP 9.x 需要额外的参数
        if (isAgp9OrHigher()) {
            connector.withArguments(
                "--no-configuration-cache",
                "-Pandroid.useAndroidX=true"
            )
        }

        connection = connector.connect()
        initializeServices()
    }

    private fun createInitScript(): File {
        val initScript = File.createTempFile("androidide-init", ".gradle")
        initScript.writeText("""
            // AGP 9.x 兼容性配置
            allprojects {
                plugins.withId("com.android.application") {
                    android {
                        // 启用新的资源处理
                        experimentalProperties["android.enableResourceValidation"] = false
                    }
                }
            }
        """)
        return initScript
    }

    private fun isAgp9OrHigher(): Boolean {
        val agpVersion = detectAgpVersion()
        return agpVersion?.let {
            val major = it.substringBefore(".").toIntOrNull() ?: 0
            major >= 9
        } ?: false
    }
}
```

#### 3.2 更新 GradleBuildService.kt

**文件**: `/workspace/core/app/src/main/java/com/itsaky/androidide/services/builder/GradleBuildService.kt`

**关键更新**:

```kotlin
class GradleBuildService : BuildService, Closeable {

    private var toolingServer: ToolingApiServerImpl? = null
    private var currentGradleVersion: String? = null
    private var currentAgpVersion: String? = null

    suspend fun syncProject(
        projectDir: File,
        onProgress: (SyncProgress) -> Unit
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // 检测 Gradle 版本
                val gradleVersion = detectGradleVersion(projectDir)
                currentGradleVersion = gradleVersion

                // 检测 AGP 版本
                val agpVersion = detectAgpVersion(projectDir)
                currentAgpVersion = agpVersion

                // 检查兼容性
                validateCompatibility(gradleVersion, agpVersion)

                // 启动 Tooling Server
                startToolingServer(projectDir, gradleVersion)

                // 执行同步
                val projectModel = toolingServer!!.fetchProjectModel()

                // 转换为内部模型
                val internalModel = convertToInternalModel(projectModel)

                onProgress(SyncProgress.Completed(internalModel))
                SyncResult.Success(internalModel)

            } catch (e: VersionIncompatibleException) {
                onProgress(SyncProgress.Error(e.message))
                SyncResult.Failure(e)
            } catch (e: Exception) {
                handleSyncError(e, projectDir)
            }
        }
    }

    private fun validateCompatibility(gradle: String, agp: String) {
        // AGP 9.x 要求 Gradle 8.7+
        val minGradleForAgp9 = "8.7"
        if (isAgp9OrHigher(agp) && !isVersionAtLeast(gradle, minGradleForAgp9)) {
            throw VersionIncompatibleException(
                "AGP $agp requires Gradle $minGradleForAgp9 or higher, but found $gradle"
            )
        }

        // Gradle 9.x 需要特殊处理
        if (isGradle9OrHigher(gradle)) {
            logger.warn("Using Gradle 9.x with AGP $agp - some features may be unstable")
        }
    }

    private fun detectGradleVersion(projectDir: File): String {
        val wrapper = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        val content = wrapper.readText()
        val match = Regex("""distributionUrl=.*gradle-(\d+\.\d+(\.\d+)?).*""")
            .find(content)
        return match?.groupValues?.get(1) ?: "8.9" // 默认值
    }

    private fun detectAgpVersion(projectDir: File): String {
        val buildFile = File(projectDir, "build.gradle.kts")
        val pluginsBlock = buildFile.readText()

        val version = Regex("""id\s*\(\s*"com\.android\.application"\s*\)\s*version\s*=\s*"(.+?)"""")
            .find(pluginsBlock)
            ?.groupValues
            ?.get(1)

        return version ?: "8.2" // 默认值
    }
}
```

### 3.4 阶段四：测试与验证 (2-3 周)

#### 4.1 单元测试覆盖

```kotlin
// test/AndroidProjectModelBuilderTest.kt
class AndroidProjectModelBuilderTest {

    @Test
    fun `test v2 API model building`() {
        // 测试 AGP 9.x v2 模型
        val builder = AndroidProjectModelBuilderV2(mockConnection, "9.3.0")
        val result = runBlocking { builder.build() }

        assertThat(result.namespace).isEqualTo("com.example.app")
        assertThat(result.compileSdkVersion).isEqualTo(35)
    }

    @Test
    fun `test ProjectGraph support`() {
        // 测试 Gradle 9.x ProjectGraph
        val builder = RootModelBuilderV9(mockConnection, "9.5.1")
        val result = runBlocking { builder.build() }

        assertThat(result.allProjects).isNotEmpty()
        assertThat(result.rootProject).isNotNull()
    }

    @Test
    fun `test version compatibility validation`() {
        // 测试版本兼容性检查
        assertThatThrownBy {
            validateCompatibility("8.5", "9.3.0")
        }.isInstanceOf(VersionIncompatibleException::class.java)
            .hasMessageContaining("requires Gradle")
    }
}
```

#### 4.2 集成测试场景

1. **AGP 8.2 + Gradle 8.5** (最低支持版本)
2. **AGP 8.5 + Gradle 8.7** (推荐版本)
3. **AGP 9.0 + Gradle 8.9** (AGP 9 最低要求)
4. **AGP 9.3 + Gradle 9.5** (目标版本)

---

## 4. 依赖关系图

### 4.1 内部模块依赖

```
┌────────────────────────────────────────┐
│          core/app (Android App)        │
├────────────────────────────────────────┤
│  GradleBuildService                     │
│  ToolingServerRunner                    │
└───────────┬────────────────────────────┘
            │
            ↓
┌────────────────────────────────────────┐
│        core/projects (项目模块)          │
├────────────────────────────────────────┤
│  WorkspaceModelBuilder                  │
│  ProjectManagerImpl                     │
└───────────┬────────────────────────────┘
            │
            ↓
┌────────────────────────────────────────┐
│        tooling/impl (Tooling 实现)       │
├────────────────────────────────────────┤
│  ToolingApiServerImpl                   │
│  ├─ ProjectTypeDetector                │
│  ├─ AndroidProjectModelBuilder         │
│  ├─ RootModelBuilder                   │
│  └─ AbstractModelBuilder               │
└───────────┬────────────────────────────┘
            │
            ↓
┌────────────────────────────────────────┐
│     外部 SDK 依赖                        │
├────────────────────────────────────────┤
│  com.android.tools.build:builder       │
│  com.android.tools.build:builder-model  │
│  org.gradle:gradle-tooling-api         │
└────────────────────────────────────────┘
```

### 4.2 AGP 版本与 API 映射

| AGP 版本 | 模型 API | 主要变化 |
|---------|---------|---------|
| 8.0-8.2 | v1 (`AndroidProject`) | 基础模型 |
| 8.3-8.5 | v1 + 部分 v2 | 引入 namespace |
| 8.7-9.0 | v1/v2 混合 | 大量 v2 API |
| 9.0+ | v2 优先 | 废弃 v1，ProjectGraph |

---

## 5. 风险评估与缓解

### 5.1 高风险项

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| AGP 9.x API 不稳定 | 项目可能无法构建 | 高 | 使用 alpha/beta 版本，充分测试 |
| Gradle 9.x 破坏性变更 | 服务启动失败 | 中 | 保持 Gradle 8.x 兼容模式 |
| 第三方插件不兼容 | 功能缺失 | 中 | 识别关键插件，测试兼容性 |
| 性能回退 | 构建变慢 | 低 | 性能测试，对比优化 |

### 5.2 缓解策略

1. **渐进式升级**: 先支持 AGP 9.x，保持 Gradle 8.x
2. **功能开关**: 实现版本检测，自动选择合适的实现
3. **详细日志**: 增加调试信息，快速定位问题
4. **回退机制**: 支持降级到稳定版本

---

## 6. 测试计划

### 6.1 单元测试

- ✅ `ProjectTypeDetectorTest` - 插件检测逻辑
- ✅ `AndroidProjectModelBuilderTest` - 模型构建
- ✅ `VersionCompatibilityTest` - 版本兼容性
- ✅ `ModelConversionTest` - 模型转换

### 6.2 集成测试

- ✅ `ProjectSyncIntegrationTest` - 项目同步
- ✅ `BuildServiceIntegrationTest` - 构建服务
- ✅ `MultiVersionTest` - 多版本支持

### 6.3 端到端测试

- ✅ 创建新 Android 项目
- ✅ 同步现有项目
- ✅ 执行构建任务
- ✅ 清理和重新同步

---

## 7. 实施时间表

### 阶段一: 依赖更新 (1-2 周)

- [ ] 更新 `gradle/libs.versions.toml` 中的版本
- [ ] 下载并集成 AGP SDK 源码
- [ ] 更新 Gradle Wrapper
- [ ] 验证构建通过

### 阶段二: API 适配 (3-4 周)

- [ ] 更新 `ProjectTypeDetector.kt`
- [ ] 实现 v2 模型适配器
- [ ] 更新 `AndroidProjectModelBuilder.kt`
- [ ] 更新 `RootModelBuilder.kt`
- [ ] 添加版本兼容性检查

### 阶段三: 服务集成 (2-3 周)

- [ ] 更新 `ToolingApiServerImpl.kt`
- [ ] 更新 `GradleBuildService.kt`
- [ ] 实现错误处理增强
- [ ] 集成测试

### 阶段四: 测试验证 (2-3 周)

- [ ] 编写单元测试
- [ ] 编写集成测试
- [ ] 端到端测试
- [ ] 性能测试
- [ ] 文档更新

### 总工期: 8-12 周

---

## 8. 成功标准

### 8.1 功能标准

- ✅ 支持 AGP 8.2 - 9.3 所有版本
- ✅ 支持 Gradle 8.5 - 9.5 所有版本
- ✅ 项目同步成功率达到 95%+
- ✅ 构建任务执行成功率达到 98%+

### 8.2 性能标准

- ✅ 小型项目 (< 10 模块) 同步 < 10s
- ✅ 中型项目 (10-50 模块) 同步 < 30s
- ✅ 服务启动时间 < 5s

### 8.3 质量标准

- ✅ 所有新增代码有单元测试
- ✅ 集成测试覆盖率 > 80%
- ✅ 关键路径 100% 覆盖

---

## 9. 后续维护

### 9.1 版本跟踪

- 订阅 AGP 发布说明
- 订阅 Gradle 发布说明
- 定期更新依赖版本

### 9.2 监控指标

- 构建成功率
- 同步成功率
- 用户反馈问题
- 性能指标

### 9.3 技术债务

- 记录所有临时解决方案
- 规划技术债务偿还
- 定期代码审查

---

## 10. 参考资料

### 官方文档

- [AGP Release Notes](https://developer.android.com/build/releases/gradle-plugin)
- [Gradle Release Notes](https://docs.gradle.org/releases/)
- [Android Gradle Plugin API Reference](https://developer.android.com/reference/tools/gradle-api)

### 相关文件

- 当前 AGP SDK: `gradle/libs/com.android.tools.build/`
- Gradle Tooling API: `gradle/libs/android/zero/studio/gradle/gradle-tooling-api/`
- Tooling 实现: `tooling/impl/src/main/java/`

---

**文档版本**: 1.0
**创建日期**: 2026-06-06
**最后更新**: 2026-06-06
**维护者**: AndroidIDE Team
