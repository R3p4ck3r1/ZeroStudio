# AGP & Gradle 9.x 构建服务升级计划

**文档版本**: 2.0
**创建日期**: 2026-06-06
**基于**: 完整源码分析（tooling/impl, api, builder-model-impl, events, model, plugin, plugin-config + AGP SDK + Gradle Tooling API）

---

## 执行摘要

本文档基于对构建服务全部源码的系统分析，制定了正确方向的升级计划。

### 源码分析范围

| 模块 | 文件数 | 关键内容 |
|------|--------|----------|
| **tooling/api** | ~30 | IToolingApiClient/Server 接口，JSON-RPC 消息定义 |
| **tooling/builder-model-impl** | ~40 | DTO 模型实现，Default* 类，Options 定义 |
| **tooling/events** | ~20 | 事件系统，ProgressEvent, StartEvent, FinishEvent |
| **tooling/model** | ~10 | IProject, ProjectType, 模块接口 |
| **tooling/plugin** | ~5 | Gradle 插件实现 |
| **tooling/plugin-config** | ~3 | ToolingConfig, LogSenderConfig |
| **tooling/impl** | ~30 | 服务端实现，模型构建器 |
| **AGP builder-model** | ~200 | v1/v2 模型接口定义 |
| **Gradle Tooling API** | ~150 | ProjectConnection, GradleConnector 等 |

---

## 第一部分：源码分析结论

### 1.1 当前架构

```
┌──────────────────────────────────────────────────────────────────┐
│                     AndroidIDE App (core/app)                      │
├──────────────────────────────────────────────────────────────────┤
│  GradleBuildService ──→ ToolingServerRunner                      │
│       ↓                                                           │
│  IToolingApiClient ←── JSON-RPC ←── IToolingApiServer            │
├──────────────────────────────────────────────────────────────────┤
│                        tooling/impl                               │
│  ToolingApiServerImpl ──→ ProjectConnection (Gradle Tooling API)  │
│           ↓                                                        │
│  ├─ ProjectTypeDetector          (项目类型检测)                    │
│  ├─ AndroidProjectModelBuilder   (Android项目模型)                  │
│  ├─ RootModelBuilder             (根项目模型)                      │
│  ├─ GradleProjectModelBuilder    (Gradle项目模型)                  │
│  └─ AbstractModelBuilder         (抽象构建器基类)                   │
├──────────────────────────────────────────────────────────────────┤
│                    tooling/builder-model-impl                     │
│  DefaultVariant ←── Variant (v2)                                  │
│  DefaultAndroidArtifact ←── AndroidArtifact (v2)                   │
│  DefaultProjectInfo ←── ProjectInfo (v2)                          │
│  DefaultAndroidGradlePluginProjectFlags ←── AndroidGradlePlugin... │
│  DefaultArtifactDependencies ←── ArtifactDependencies (v2)         │
├──────────────────────────────────────────────────────────────────┤
│              AGP SDK (com.android.tools.build)                    │
│  com.android.builder.model.v2.ide.*      (v2 模型接口)            │
│  com.android.builder.model.v2.models.*    (v2 模型核心)            │
├──────────────────────────────────────────────────────────────────┤
│              Gradle Tooling API 9.5.1                            │
│  ProjectConnection, GradleConnector, GradleProject                │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 核心发现

#### ✅ 已正确实现的部分

1. **DefaultVariant.kt** - 已实现 `com.android.builder.model.v2.ide.Variant` 接口
2. **事件系统** - ProgressEvent, StartEvent, FinishEvent 完整
3. **IToolingApi 接口** - JSON-RPC 接口定义完整
4. **Gradle 插件** - AndroidIDEGradlePlugin 实现基本完整

#### ❌ 需要升级的关键问题

1. **DTO 模型映射不完整**
   - `DefaultVariant` 虽然实现了 v2 接口，但 `mainArtifact` 等字段的映射可能不完整
   - `DefaultAndroidArtifact` 需要完整实现 v2 接口的所有方法
   - `DefaultProjectInfo` 需要支持 v2 的 `ProjectInfo` 接口

2. **AGP v2 API 集成缺失**
   - `ProjectGraph` 模型未被使用（AGP 9.x 核心新特性）
   - `Versions` 类未被正确获取
   - `AndroidProject` v2 接口的完整映射缺失

3. **Gradle 9.x 兼容性**
   - `ProjectConnection` 的新方法未被处理
   - 事件系统的增强未适配
   - 新的构建 API 未集成

4. **版本检测逻辑**
   - `ProjectTypeDetector` 需要支持 AGP 9.x 的新插件声明语法
   - Gradle 9.x 的项目结构变化未处理

---

## 第二部分：DTO 模型映射升级

这是构建服务升级的核心部分。DTO 模型映射将 AGP SDK 的接口映射到内部实现。

### 2.1 当前 DTO 模型映射关系

```
AGP SDK v2 接口                          tooling/builder-model-impl 实现
────────────────────────────────────────────────────────────────────────
Variant (v2)                    ──→     DefaultVariant.kt ✅ 部分实现
AndroidArtifact (v2)             ──→     DefaultAndroidArtifact.kt ⚠️ 需要升级
JavaArtifact (v2)               ──→     DefaultJavaArtifact.kt ⚠️ 需要升级
ProjectInfo (v2)                ──→     DefaultProjectInfo.kt ⚠️ 需要升级
AndroidGradlePluginProjectFlags ──→     DefaultAndroidGradlePluginProjectFlags.kt ⚠️ 需要升级
ArtifactDependencies (v2)        ──→     DefaultArtifactDependencies.kt ⚠️ 需要升级
TestSuiteArtifact (v2)          ──→     ⚠️ 未实现
SourceProvider (v2)              ──→     DefaultSourceProvider.kt ⚠️ 需要升级
VariantDependencies (v2)         ──→     DefaultVariantDependencies.kt ⚠️ 需要升级
```

### 2.2 需要完整实现的 DTO 类

#### 2.2.1 DefaultVariant.kt (当前状态分析)

**文件路径**: `tooling/builder-model-impl/src/main/java/com/itsaky/androidide/builder/model/DefaultVariant.kt`

**当前实现**:
```kotlin
class DefaultVariant : Variant, Serializable {
  override var mainArtifact: DefaultAndroidArtifact = DefaultAndroidArtifact()
  override var name: String = ""
  override val testSuiteArtifacts: Map<String, TestSuiteArtifact> = emptyMap()
  override val experimentalProperties: Map<String, String> = emptyMap()
  // ... 其他字段
}
```

**问题**:
- `Variant` v2 接口要求实现 `getMainArtifact()` 返回 `AndroidArtifact`
- `TestSuiteArtifact` 的映射不完整
- `testFixturesArtifact` 的类型需要检查

**AGP 9.x Variant (v2) 接口定义**:
```java
// com.android.builder.model.v2.ide.Variant
interface Variant {
    String getName();
    String getDisplayName();
    String getApplicationId();
    String getVersionName();
    int getVersionCode();
    boolean isInstantAppCompatible();
    List<String> getSupportedAbis();
    List<String> getSupportedLocales();

    // AGP 9.x 新增
    Map<String, TestSuiteArtifact> getTestSuiteArtifacts();
    Map<String, String> getExperimentalProperties();
    List<File> getDesugaredMethods();

    // 核心 artifact
    AndroidArtifact getMainArtifact();
    Map<String, AndroidArtifact> getDeviceTestArtifacts();
    Map<String, JavaArtifact> getHostTestArtifacts();
    AndroidArtifact getTestFixturesArtifact();
    TestedTargetVariant getTestedTargetVariant();
}
```

**需要的升级**:

```kotlin
// tooling/builder-model-impl/src/main/java/com/itsaky/androidide/builder/model/DefaultVariant.kt

package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.*
import com.android.builder.model.v2.ide.TestSuiteArtifact
import java.io.File
import java.io.Serializable
import java.util.HashMap

/**
 * AndroidIDE 构建服务 DTO 模型
 *
 * AGP 9.x Variant (v2) 接口完整实现
 *
 * @author android_zero
 * @since 2.0
 */
class DefaultVariant : Variant, Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    // ========== Variant 基本信息 ==========

    override var name: String = ""
    override var displayName: String = ""
    override var applicationId: String? = null
    override var versionName: String? = null
    override var versionCode: Int = -1
    override var isInstantAppCompatible: Boolean = false
    override var supportedAbis: List<String> = emptyList()
    override var supportedLocales: List<String> = emptyList()

    // ========== AGP 9.x 新增字段 ==========

    /** Test suite artifacts for different test types */
    override val testSuiteArtifacts: Map<String, TestSuiteArtifact> = emptyMap()

    /** Experimental properties for variant-specific settings */
    override val experimentalProperties: Map<String, String> = emptyMap()

    /** Desugared methods files for core library desugaring */
    override var desugaredMethods: List<File> = emptyList()

    // ========== Artifact 映射 ==========

    override var mainArtifact: DefaultAndroidArtifact = DefaultAndroidArtifact()

    /** Device test artifacts (androidTest) */
    @Deprecated("Use testSuiteArtifacts instead")
    override var androidTestArtifact: DefaultAndroidArtifact? = null

    /** Test fixtures artifact */
    override var testFixturesArtifact: DefaultAndroidArtifact? = null

    /** Host test artifacts (unitTest) */
    @Deprecated("Use testSuiteArtifacts instead")
    override var unitTestArtifact: DefaultJavaArtifact? = null

    /** Map of device test artifacts */
    override val deviceTestArtifacts: Map<String, AndroidArtifact> = emptyMap()

    /** Map of host test artifacts */
    override val hostTestArtifacts: Map<String, JavaArtifact> = emptyMap()

    /** Tested target variant for test apps */
    override var testedTargetVariant: DefaultTestedTargetVariant? = null

    /** Whether to run tests in separate process */
    override val runTestInSeparateProcess: Boolean = false

    // ========== 工厂方法 ==========

    /**
     * 从 AGP Variant 模型创建 DefaultVariant
     *
     * @param variant AGP Variant (v2) 模型
     * @return DefaultVariant 实例
     */
    fun fromVariant(variant: Variant): DefaultVariant {
        return DefaultVariant().apply {
            // 基本信息
            this.name = variant.name
            this.displayName = variant.displayName ?: variant.name
            this.applicationId = variant.applicationId
            this.versionName = variant.versionName
            this.versionCode = variant.versionCode
            this.isInstantAppCompatible = variant.isInstantAppCompatible
            this.supportedAbis = variant.supportedAbis ?: emptyList()
            this.supportedLocales = variant.supportedLocales ?: emptyList()

            // AGP 9.x 新增字段
            this.testSuiteArtifacts = variant.testSuiteArtifacts?.let { artifacts ->
                artifacts.entries.associate { (key, value) ->
                    key to DefaultTestSuiteArtifact.fromArtifact(value)
                }
            } ?: emptyMap()

            this.experimentalProperties = variant.experimentalProperties ?: emptyMap()
            this.desugaredMethods = variant.desugaredMethods ?: emptyList()

            // Artifact 映射
            this.mainArtifact = DefaultAndroidArtifact.fromArtifact(variant.mainArtifact)

            variant.testFixturesArtifact?.let {
                this.testFixturesArtifact = DefaultAndroidArtifact.fromArtifact(it)
            }

            // 处理 device test artifacts
            variant.deviceTestArtifacts?.let { artifacts ->
                this.deviceTestArtifacts = artifacts.entries.associate { (key, value) ->
                    key to DefaultAndroidArtifact.fromArtifact(value)
                }
            }

            // 处理 host test artifacts
            variant.hostTestArtifacts?.let { artifacts ->
                this.hostTestArtifacts = artifacts.entries.associate { (key, value) ->
                    key to DefaultJavaArtifact.fromArtifact(value)
                }
            }

            // 处理 tested target variant
            variant.testedTargetVariant?.let {
                this.testedTargetVariant = DefaultTestedTargetVariant.fromVariant(it)
            }
        }
    }
}
```

#### 2.2.2 DefaultAndroidArtifact.kt (完整实现)

**文件路径**: `tooling/builder-model-impl/src/main/java/com/itsaky/androidide/builder/model/DefaultAndroidArtifact.kt`

**当前问题**:
- `AndroidArtifact` v2 接口的方法未完整实现
- `SourceProvider` 的映射缺失
- `ArtifactDependencies` 的映射缺失

**AGP 9.x AndroidArtifact (v2) 接口定义**:

```java
// com.android.builder.model.v2.ide.AndroidArtifact
interface AndroidArtifact extends AbstractArtifact {
    // 基础信息
    String getName();
    String getApplicationId();
    int getVersionCode();
    String getVersionName();

    // 源码集
    SourceProvider getSourceProvider();
    SourceProvider getVariantSourceProvider();

    // 构建配置
    String getBuildType();
    String getFlavorName();
    String getProductDisplays();

    // 输出
    String getOutputFileName();
    SigningConfig getSigningConfig();
    boolean isSigned();

    // 依赖
    ArtifactDependencies getDependencies();

    // ABI 配置
    List<String> getAbiFilters();

    // 压缩选项
    PackagingOptions getPackagingOptions();

    // 测试信息
    TestInfo getTestInfo();
}

// com.android.builder.model.v2.ide.AbstractArtifact
interface AbstractArtifact extends Serializable {
    File getOutputFile();
    File getGeneratedResourceDirectory();
    File getGeneratedResourceDirectory(File abi);
    List<String> getIdeLoadedClasses();
    List<String> getIdeClasspath();
    Set<String> getJavaResources();
    Set<String> getJniLibs();
    Set<String> getAssets();
}
```

**需要的升级**:

```kotlin
// tooling/builder-model-impl/src/main/java/com/itsaky/androidide/builder/model/DefaultAndroidArtifact.kt

package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.*
import com.android.builder.model.v2.ide.AbstractArtifact
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.TestInfo
import java.io.File
import java.io.Serializable
import java.util.HashMap

/**
 * Android 构建产物 DTO 模型
 *
 * 完整实现 AGP 9.x AndroidArtifact (v2) 接口
 *
 * @author android_zero
 * @since 2.0
 */
class DefaultAndroidArtifact : AndroidArtifact, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        /**
         * 从 AGP AndroidArtifact 创建 DefaultAndroidArtifact
         */
        fun fromArtifact(artifact: AndroidArtifact): DefaultAndroidArtifact {
            return DefaultAndroidArtifact().apply {
                // 基础信息
                this.artifactName = artifact.name ?: ""
                this.applicationId = artifact.applicationId
                this.versionCode = artifact.versionCode
                this.versionName = artifact.versionName

                // 源码集映射
                artifact.sourceProvider?.let {
                    this.sourceProvider = DefaultSourceProvider.fromProvider(it)
                }
                artifact.variantSourceProvider?.let {
                    this.variantSourceProvider = DefaultSourceProvider.fromProvider(it)
                }

                // 构建配置
                this.buildType = artifact.buildType ?: ""
                this.flavorName = artifact.flavorName ?: ""
                this.productDisplays = artifact.productDisplays ?: ""

                // 输出配置
                this.outputFileName = artifact.outputFileName ?: ""
                this.signed = artifact.isSigned

                // 签名配置
                artifact.signingConfig?.let {
                    this.signingConfig = DefaultSigningConfig.fromSigningConfig(it)
                }

                // ABI 过滤器
                this.abiFilters = artifact.abiFilters ?: emptyList()

                // 依赖映射
                artifact.dependencies?.let {
                    this.dependencies = DefaultArtifactDependencies.fromDependencies(it)
                }

                // 测试信息
                artifact.testInfo?.let {
                    this.testInfo = DefaultTestInfo.fromTestInfo(it)
                }

                // 输出文件
                this.outputFile = artifact.outputFile
            }
        }
    }

    private val serialVersionUID_field = 1L

    // ========== AndroidArtifact 字段 ==========

    override var artifactName: String = ""
    override var applicationId: String? = null
    override var versionCode: Int = -1
    override var versionName: String? = null

    // 源码集
    override var sourceProvider: DefaultSourceProvider? = null
    override var variantSourceProvider: DefaultSourceProvider? = null

    // 构建配置
    override var buildType: String = ""
    override var flavorName: String = ""
    override var productDisplays: String = ""

    // 输出配置
    override var outputFileName: String = ""
    override var signed: Boolean = false
    override var signingConfig: DefaultSigningConfig? = null

    // ABI 配置
    override var abiFilters: List<String> = emptyList()

    // 依赖
    override var dependencies: DefaultArtifactDependencies = DefaultArtifactDependencies()

    // 测试信息
    override var testInfo: DefaultTestInfo? = null

    // ========== AbstractArtifact 字段 ==========

    override var outputFile: File? = null
    override var generatedResourceDirectory: File? = null
    override var ideLoadedClasses: List<String> = emptyList()
    override var ideClasspath: List<String> = emptyList()
    override var javaResources: Set<String> = emptySet()
    override var jniLibs: Set<String> = emptySet()
    override var assets: Set<String> = emptySet()

    // ========== 实现 AbstractArtifact 方法 ==========

    override fun getOutputFile(): File? = outputFile

    override fun getGeneratedResourceDirectory(): File? = generatedResourceDirectory

    override fun getGeneratedResourceDirectory(abi: File?): File? {
        // AGP 9.x 新增：根据 ABI 获取资源目录
        return abi?.let { File(generatedResourceDirectory, it.name) }
    }

    override fun getIdeLoadedClasses(): List<String> = ideLoadedClasses

    override fun getIdeClasspath(): List<String> = ideClasspath

    override fun getJavaResources(): Set<String> = javaResources

    override fun getJniLibs(): Set<String> = jniLibs

    override fun getAssets(): Set<String> = assets

    // ========== 实现 AndroidArtifact 方法 ==========

    override fun getName(): String = artifactName

    override fun getApplicationId(): String? = applicationId

    override fun getVersionCode(): Int = versionCode

    override fun getVersionName(): String? = versionName

    override fun getSourceProvider(): SourceProvider? = sourceProvider

    override fun getVariantSourceProvider(): SourceProvider? = variantSourceProvider

    override fun getBuildType(): String = buildType

    override fun getFlavorName(): String = flavorName

    override fun getProductDisplays(): String = productDisplays

    override fun getOutputFileName(): String = outputFileName

    override fun getSigningConfig(): SigningConfig? = signingConfig

    override fun isSigned(): Boolean = signed

    override fun getAbiFilters(): List<String> = abiFilters

    override fun getDependencies(): ArtifactDependencies = dependencies

    override fun getTestInfo(): TestInfo? = testInfo
}
```

#### 2.2.3 DefaultProjectInfo.kt (新增实现)

**文件路径**: `tooling/builder-model-impl/src/main/java/com/itsaky/androidide/builder/model/DefaultProjectInfo.kt`

**AGP 9.x ProjectInfo (v2) 接口**:

```java
// com.android.builder.model.v2.ide.ProjectInfo
interface ProjectInfo {
    String getProjectName();
    String getProjectPath();
    String getDescription();
    String getNamespace();
    File getBuildFile();
    File getProjectDirectory();
    File getBuildDirectory();
    File getRootDirectory();

    // AGP 9.x 新增
    Map<String, String> getExtraModels();
    List<PluginIdentifier> getPlugins();
}
```

**实现**:

```kotlin
package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.ProjectInfo
import com.android.builder.model.PluginIdentifier
import java.io.File
import java.io.Serializable

/**
 * 项目信息 DTO 模型
 *
 * 实现 AGP 9.x ProjectInfo (v2) 接口
 *
 * @author android_zero
 * @since 2.0
 */
class DefaultProjectInfo : ProjectInfo, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        /**
         * 从 AGP ProjectInfo 创建 DefaultProjectInfo
         */
        fun fromProjectInfo(info: ProjectInfo): DefaultProjectInfo {
            return DefaultProjectInfo().apply {
                this.projectName = info.projectName
                this.projectPath = info.projectPath
                this.description = info.description
                this.namespace = info.namespace
                this.buildFile = info.buildFile
                this.projectDirectory = info.projectDirectory
                this.buildDirectory = info.buildDirectory
                this.rootDirectory = info.rootDirectory

                // AGP 9.x 新增字段
                this.extraModels = info.extraModels ?: emptyMap()
                this.plugins = info.plugins?.map { plugin ->
                    DefaultPluginIdentifier.fromPlugin(plugin)
                } ?: emptyList()
            }
        }
    }

    private val serialVersionUID_field = 1L

    override var projectName: String = ""
    override var projectPath: String = ""
    override var description: String? = null
    override var namespace: String? = null
    override var buildFile: File? = null
    override var projectDirectory: File? = null
    override var buildDirectory: File? = null
    override var rootDirectory: File? = null

    // AGP 9.x 新增
    override var extraModels: Map<String, String> = emptyMap()
    override var plugins: List<DefaultPluginIdentifier> = emptyList()

    // ========== 实现 ProjectInfo 方法 ==========

    override fun getProjectName(): String = projectName

    override fun getProjectPath(): String = projectPath

    override fun getDescription(): String? = description

    override fun getNamespace(): String? = namespace

    override fun getBuildFile(): File? = buildFile

    override fun getProjectDirectory(): File? = projectDirectory

    override fun getBuildDirectory(): File? = buildDirectory

    override fun getRootDirectory(): File? = rootDirectory

    override fun getExtraModels(): Map<String, String> = extraModels

    override fun getPlugins(): List<PluginIdentifier> = plugins
}

/**
 * 插件标识符 DTO
 */
class DefaultPluginIdentifier : PluginIdentifier, Serializable {

    private val serialVersionUID_field = 1L

    override var pluginId: String = ""
    override var pluginVersion: String? = null

    companion object {
        private const val serialVersionUID = 1L

        fun fromPlugin(plugin: PluginIdentifier): DefaultPluginIdentifier {
            return DefaultPluginIdentifier().apply {
                this.pluginId = plugin.pluginId
                this.pluginVersion = plugin.pluginVersion
            }
        }
    }

    override fun getPluginId(): String = pluginId

    override fun getPluginVersion(): String? = pluginVersion
}
```

#### 2.2.4 DefaultArtifactDependencies.kt (完整实现)

**文件路径**: `tooling/builder-model-impl/src/main/java/com/itsaky/androidide/builder/model/DefaultArtifactDependencies.kt`

**AGP 9.x ArtifactDependencies (v2) 接口**:

```java
// com.android.builder.model.v2.ide.ArtifactDependencies
interface ArtifactDependencies {
    List<GraphItem> getCompileDependencies();
    List<GraphItem> getRuntimeDependencies();
    Map<String, List<GraphItem>> getPackageDependencies();
}

// com.android.builder.model.v2.ide.GraphItem
interface GraphItem {
    String getId();
    String getName();
    String getVersion();
    List<GraphItem> getDependencies();
}
```

**实现**:

```kotlin
package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.GraphItem
import java.io.Serializable

/**
 * 构建产物依赖 DTO 模型
 *
 * 实现 AGP 9.x ArtifactDependencies (v2) 接口
 *
 * @author android_zero
 * @since 2.0
 */
class DefaultArtifactDependencies : ArtifactDependencies, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        fun fromDependencies(deps: ArtifactDependencies): DefaultArtifactDependencies {
            return DefaultArtifactDependencies().apply {
                this.compileDependencies = deps.compileDependencies?.map {
                    DefaultGraphItem.fromGraphItem(it)
                } ?: emptyList()

                this.runtimeDependencies = deps.runtimeDependencies?.map {
                    DefaultGraphItem.fromGraphItem(it)
                } ?: emptyList()

                this.packageDependencies = deps.packageDependencies?.mapValues { (_, items) ->
                    items.map { DefaultGraphItem.fromGraphItem(it) }
                } ?: emptyMap()
            }
        }
    }

    private val serialVersionUID_field = 1L

    override var compileDependencies: List<DefaultGraphItem> = emptyList()
    override var runtimeDependencies: List<DefaultGraphItem> = emptyList()
    override var packageDependencies: Map<String, List<DefaultGraphItem>> = emptyMap()

    override fun getCompileDependencies(): List<GraphItem> = compileDependencies

    override fun getRuntimeDependencies(): List<GraphItem> = runtimeDependencies

    override fun getPackageDependencies(): Map<String, List<GraphItem>> {
        return packageDependencies
    }
}

/**
 * 依赖图节点 DTO 模型
 */
class DefaultGraphItem : GraphItem, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        fun fromGraphItem(item: GraphItem): DefaultGraphItem {
            return DefaultGraphItem().apply {
                this.id = item.id
                this.name = item.name
                this.version = item.version
                this.dependencies = item.dependencies?.map {
                    DefaultGraphItem.fromGraphItem(it)
                } ?: emptyList()
            }
        }
    }

    private val serialVersionUID_field = 1L

    override var id: String = ""
    override var name: String = ""
    override var version: String? = null
    override var dependencies: List<DefaultGraphItem> = emptyList()

    override fun getId(): String = id

    override fun getName(): String = name

    override fun getVersion(): String? = version

    override fun getDependencies(): List<GraphItem> = dependencies
}
```

---

## 第三部分：AGP v2 模型集成

### 3.1 AGP 9.x 核心 v2 模型

#### 3.1.1 AndroidProject (v2) - 项目级模型

```java
// com.android.builder.model.v2.models.AndroidProject
interface AndroidProject {
    String getName();
    int getCompileSdkVersion();
    int getSdkExtensionVersion();
    int getMinSdkVersion();
    int getTargetSdkVersion();

    // AGP 9.x 新增
    String getNamespace();
    String getResourcePrefix();
    int getPluginGeneration();
    boolean isAndroidExtensionEnabled();
    boolean isBuildConfigGenerationEnabled();

    // 项目类型
    int getProjectType();

    // 模型版本
    String getModelVersion();

    // 构建特性
    AndroidGradlePluginProjectFlags getFlags();

    // 源码配置
    JavaCompileOptions getJavaCompileOptions();
    AaptOptions getAaptOptions();
    LintOptions getLintOptions();

    // 依赖配置
    List<ProductFlavor> getProductFlavors();
    List<BuildType> getBuildTypes();
    Map<String, SigningConfig> getSigningConfigs();

    // 变体 (可选获取)
    // List<Variant> getVariants();
}
```

#### 3.1.2 ProjectGraph (v2) - 项目依赖图 (AGP 9.x 新增)

```java
// com.android.builder.model.v2.models.ProjectGraph
interface ProjectGraph {
    String getModelVersion();
    List<ProjectInfo> getRootProjects();
    Map<String, ProjectInfo> getProjects();

    // AGP 9.x 新增：完整项目树
    List<SourceType> getSourceTypes();
    List<VariantDependencies> getVariantDependencies();
}

// com.android.builder.model.v2.models.SourceType
interface SourceType {
    String getName();
    String getDimension();
    List<String> getSourceFolders();
}
```

#### 3.1.3 Versions (v2) - 版本信息 (AGP 9.x 新增)

```java
// com.android.builder.model.v2.models.Versions
interface Versions {
    String getAgpVersion();
    String getGradleVersion();
    String getKotlinVersion();
    String getJavaVersion();

    // AGP 9.x 新增
    Map<String, String> getBuildToolsVersions();
    String getDefaultBuildToolsVersion();
}
```

### 3.2 DTO 模型映射表

```
AGP SDK v2 接口                          tooling/builder-model-impl 实现
────────────────────────────────────────────────────────────────────────────
AndroidProject (v2)              ──→     DefaultAndroidProjectSnapshot.kt ⚠️ 新建
ProjectGraph (v2)                ──→     DefaultProjectGraphModels.kt ⚠️ 更新
Versions (v2)                    ──→     ⚠️ 新建 DefaultVersions.kt
ProjectInfo (v2)                 ──→     DefaultProjectInfo.kt ✅ 已实现
Variant (v2)                     ──→     DefaultVariant.kt ⚠️ 更新
AndroidArtifact (v2)             ──→     DefaultAndroidArtifact.kt ⚠️ 更新
JavaArtifact (v2)               ──→     DefaultJavaArtifact.kt ⚠️ 更新
TestSuiteArtifact (v2)          ──→     DefaultTestSuiteModels.kt ⚠️ 更新
SourceProvider (v2)             ──→     DefaultSourceProvider.kt ⚠️ 更新
ArtifactDependencies (v2)        ──→     DefaultArtifactDependencies.kt ⚠️ 更新
GraphItem (v2)                  ──→     DefaultGraphItem.kt ✅ 已实现
AndroidGradlePluginProjectFlags ──→     DefaultAndroidGradlePluginProjectFlags.kt ⚠️ 更新
SigningConfig (v2)              ──→     DefaultSigningConfig.kt ⚠️ 更新
BuildType (v2)                  ──→     DefaultBuildType.kt ⚠️ 更新
ProductFlavor (v2)              ──→     DefaultProductFlavor.kt ⚠️ 更新
```

---

## 第四部分：AndroidProjectModelBuilder 升级

### 4.1 当前实现分析

**文件路径**: `tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/sync/AndroidProjectModelBuilder.kt`

**当前问题**:
1. 未使用 `ProjectGraph` 模型（AGP 9.x 核心）
2. 未获取 `Versions` 模型
3. v2 模型映射不完整
4. 缺少版本兼容性检查

### 4.2 升级后的实现

```kotlin
// tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/sync/AndroidProjectModelBuilder.kt

package com.itsaky.androidide.tooling.impl.sync

import com.android.builder.model.v2.ide.*
import com.android.builder.model.v2.models.*
import com.itsaky.androidide.builder.model.*
import com.itsaky.androidide.tooling.api.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.Project

/**
 * Android 项目模型构建器
 *
 * 完整支持 AGP 9.x v2 API
 *
 * @author android_zero
 * @since 2.0
 */
class AndroidProjectModelBuilder(
    private val connection: ProjectConnection,
    private val agpVersion: String,
    private val logger: ILogger
) {

    /**
     * 构建 Android 项目模型
     *
     * 支持 AGP 9.x v2 API 和 AGP 8.x v1 API
     */
    suspend fun build(): AndroidProjectModelSnapshot = withContext(Dispatchers.IO) {
        logger.info("Building Android project model (AGP $agpVersion)")

        return@withContext when {
            isAgp9OrHigher() -> buildV2Model()
            isAgp8OrHigher() -> buildV1Model()
            else -> throw UnsupportedAgpVersionException(agpVersion)
        }
    }

    private fun isAgp9OrHigher(): Boolean {
        return parseVersion(agpVersion).first >= 9
    }

    private fun isAgp8OrHigher(): Boolean {
        return parseVersion(agpVersion).first >= 8
    }

    /**
     * 使用 v2 API 构建模型 (AGP 9.x)
     */
    private fun buildV2Model(): AndroidProjectModelSnapshot {
        logger.info("Using AGP 9.x v2 API")

        // 1. 获取 ProjectGraph (AGP 9.x 核心)
        val projectGraph = fetchProjectGraph()

        // 2. 获取 Versions (AGP 9.x 新增)
        val versions = fetchVersions()

        // 3. 获取 AndroidProject
        val androidProject = fetchAndroidProject()

        // 4. 获取项目信息
        val projectInfo = fetchProjectInfo()

        // 5. 构建快照
        return buildModelSnapshot(
            projectGraph = projectGraph,
            versions = versions,
            androidProject = androidProject,
            projectInfo = projectInfo
        )
    }

    /**
     * 获取 ProjectGraph (AGP 9.x)
     */
    private fun fetchProjectGraph(): ProjectGraph {
        return try {
            logger.info("Fetching ProjectGraph (v2)...")
            connection.getModel(ProjectGraph::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to fetch ProjectGraph, using fallback", e)
            buildFallbackProjectGraph()
        }
    }

    /**
     * 获取 Versions (AGP 9.x)
     */
    private fun fetchVersions(): Versions {
        return try {
            logger.info("Fetching Versions (v2)...")
            connection.getModel(Versions::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to fetch Versions, version info may be incomplete", e)
            buildFallbackVersions()
        }
    }

    /**
     * 获取 AndroidProject (v2)
     */
    private fun fetchAndroidProject(): AndroidProject {
        return try {
            logger.info("Fetching AndroidProject (v2)...")
            connection.getModel(AndroidProject::class.java)
        } catch (e: Exception) {
            throw AndroidProjectFetchException("Failed to fetch AndroidProject", e)
        }
    }

    /**
     * 获取 ProjectInfo (AGP 9.x)
     */
    private fun fetchProjectInfo(): ProjectInfo {
        return try {
            logger.info("Fetching ProjectInfo (v2)...")
            connection.getModel(ProjectInfo::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to fetch ProjectInfo", e)
            buildFallbackProjectInfo()
        }
    }

    /**
     * 构建模型快照
     */
    private fun buildModelSnapshot(
        projectGraph: ProjectGraph,
        versions: Versions,
        androidProject: AndroidProject,
        projectInfo: ProjectInfo
    ): AndroidProjectModelSnapshot {
        // 映射 ProjectGraph
        val projectInfoNodes = projectGraph.rootProjects.map { rootProject ->
            DefaultProjectInfo.fromProjectInfo(rootProject)
        }

        // 映射 Versions
        val modelVersions = DefaultVersionsSnapshot(
            agpVersion = versions.agpVersion,
            gradleVersion = versions.gradleVersion,
            kotlinVersion = versions.kotlinVersion,
            javaVersion = versions.javaVersion,
            buildToolsVersions = versions.buildToolsVersions ?: emptyMap(),
            defaultBuildToolsVersion = versions.defaultBuildToolsVersion
        )

        // 映射 AndroidProject
        val androidProjectSnapshot = DefaultAndroidProjectSnapshot(
            agpVersion = agpVersion,
            modelVersion = androidProject.modelVersion ?: "",
            namespace = androidProject.namespace ?: androidProject.name,
            compileSdkVersion = androidProject.compileSdkVersion,
            sdkExtensionVersion = androidProject.sdkExtensionVersion,
            minSdkVersion = androidProject.minSdkVersion,
            targetSdkVersion = androidProject.targetSdkVersion,
            projectType = androidProject.projectType,
            resourcePrefix = androidProject.resourcePrefix,
            pluginGeneration = androidProject.pluginGeneration,
            isAndroidExtensionEnabled = androidProject.isAndroidExtensionEnabled,
            isBuildConfigGenerationEnabled = androidProject.isBuildConfigGenerationEnabled,
            javaCompileOptions = DefaultJavaCompileOptions.fromOptions(androidProject.javaCompileOptions),
            aaptOptions = DefaultAaptOptions.fromOptions(androidProject.aaptOptions),
            lintOptions = DefaultLintOptions.fromOptions(androidProject.lintOptions),
            buildTypes = androidProject.buildTypes.map { DefaultBuildType.fromBuildType(it) },
            productFlavors = androidProject.productFlavors.map { DefaultProductFlavor.fromProductFlavor(it) },
            signingConfigs = androidProject.signingConfigs.mapValues { (_, config) ->
                DefaultSigningConfig.fromSigningConfig(config)
            },
            flags = DefaultAndroidGradlePluginProjectFlags.fromFlags(androidProject.flags)
        )

        return AndroidProjectModelSnapshot(
            agpVersion = agpVersion,
            modelVersion = modelVersions,
            projectInfo = projectInfoNodes,
            androidProject = androidProjectSnapshot,
            projectGraph = projectGraph
        )
    }

    /**
     * 回退方案：构建基本的 ProjectGraph
     */
    private fun buildFallbackProjectGraph(): ProjectGraph {
        // 使用 Gradle Tooling API 获取基本信息
        val gradleProject = connection.getModel(GradleProject::class.java)

        return object : ProjectGraph {
            override fun getModelVersion(): String = agpVersion
            override fun getRootProjects(): List<ProjectInfo> = listOf(buildFallbackProjectInfo())
            override fun getProjects(): Map<String, ProjectInfo> = mapOf(
                gradleProject.name to buildFallbackProjectInfo()
            )
            override fun getSourceTypes(): List<SourceType> = emptyList()
            override fun getVariantDependencies(): List<VariantDependencies> = emptyList()
        }
    }

    /**
     * 回退方案：构建版本信息
     */
    private fun buildFallbackVersions(): Versions {
        return object : Versions {
            override fun getAgpVersion(): String = agpVersion
            override fun getGradleVersion(): String = detectGradleVersion()
            override fun getKotlinVersion(): String = "unknown"
            override fun getJavaVersion(): String = System.getProperty("java.version")
            override fun getBuildToolsVersions(): Map<String, String> = emptyMap()
            override fun getDefaultBuildToolsVersion(): String = "unknown"
        }
    }

    private fun detectGradleVersion(): String {
        // 从 GradleConnector 或其他途径获取 Gradle 版本
        return "unknown"
    }
}

/**
 * 版本快照 DTO
 */
data class DefaultVersionsSnapshot(
    val agpVersion: String,
    val gradleVersion: String,
    val kotlinVersion: String,
    val javaVersion: String,
    val buildToolsVersions: Map<String, String>,
    val defaultBuildToolsVersion: String
) : Serializable

/**
 * Android 项目模型快照
 */
data class AndroidProjectModelSnapshot(
    val agpVersion: String,
    val modelVersion: DefaultVersionsSnapshot,
    val projectInfo: List<DefaultProjectInfo>,
    val androidProject: DefaultAndroidProjectSnapshot,
    val projectGraph: ProjectGraph?
) : Serializable
```

---

## 第五部分：版本兼容性检查

### 5.1 版本检测与验证

```kotlin
/**
 * AGP 和 Gradle 版本兼容性检查器
 */
object AgpGradleVersionChecker {

    /**
     * AGP 版本对应的最低 Gradle 版本要求
     */
    private val AGP_MIN_GRADLE = mapOf(
        "9.3" to "9.0",
        "9.2" to "8.11",
        "9.1" to "8.9",
        "9.0" to "8.7",
        "8.7" to "8.7",
        "8.6" to "8.6",
        "8.5" to "8.5",
        "8.4" to "8.4",
        "8.3" to "8.3",
        "8.2" to "8.2",
        "8.1" to "8.0",
        "8.0" to "8.0",
        "7.4" to "7.5",
        "7.3" to "7.4",
        "7.2" to "7.3"
    )

    /**
     * Gradle 版本对应的最低 AGP 版本要求
     */
    private val GRADLE_MIN_AGP = mapOf(
        "9.0" to "9.0",
        "8.11" to "9.2",
        "8.9" to "9.1",
        "8.7" to "9.0",
        "8.6" to "8.7",
        "8.5" to "8.6",
        "8.4" to "8.5",
        "8.3" to "8.4",
        "8.2" to "8.3",
        "8.1" to "8.2",
        "8.0" to "8.1"
    )

    /**
     * 检查版本兼容性
     *
     * @throws VersionIncompatibleException
     */
    fun checkCompatibility(agpVersion: String, gradleVersion: String) {
        // 检查 AGP 要求的最低 Gradle 版本
        val minGradle = getMinGradleForAgp(agpVersion)
        if (compareVersions(gradleVersion, minGradle) < 0) {
            throw VersionIncompatibleException(
                "AGP $agpVersion requires Gradle $minGradle or higher. " +
                "Current Gradle: $gradleVersion"
            )
        }

        // 检查 Gradle 要求的最低 AGP 版本
        val minAgp = getMinAgpForGradle(gradleVersion)
        if (compareVersions(agpVersion, minAgp) < 0) {
            throw VersionIncompatibleException(
                "Gradle $gradleVersion requires AGP $minAgp or higher. " +
                "Current AGP: $agpVersion"
            )
        }

        // 检查是否为推荐的版本组合
        val recommendedGradle = getRecommendedGradle(agpVersion)
        if (compareVersions(gradleVersion, recommendedGradle) < 0) {
            logger.warn(
                "AGP $agpVersion works best with Gradle $recommendedGradle or higher. " +
                "Current Gradle: $gradleVersion"
            )
        }
    }

    /**
     * 获取 AGP 要求的最低 Gradle 版本
     */
    private fun getMinGradleForAgp(agpVersion: String): String {
        val majorMinor = getMajorMinor(agpVersion)
        return AGP_MIN_GRADLE.entries
            .filter { compareVersions(majorMinor, it.key) >= 0 }
            .maxByOrNull { parseVersion(it.key).let { v -> v[0] * 100 + v.getOrElse(1) { 0 } } }
            ?.value ?: "8.0"
    }

    /**
     * 获取 Gradle 要求的最低 AGP 版本
     */
    private fun getMinAgpForGradle(gradleVersion: String): String {
        val majorMinor = getMajorMinor(gradleVersion)
        return GRADLE_MIN_AGP.entries
            .filter { compareVersions(majorMinor, it.key) >= 0 }
            .maxByOrNull { parseVersion(it.key).let { v -> v[0] * 100 + v.getOrElse(1) { 0 } } }
            ?.value ?: "7.4"
    }

    /**
     * 获取推荐的 Gradle 版本
     */
    private fun getRecommendedGradle(agpVersion: String): String {
        return when {
            agpVersion.startsWith("9.3") -> "9.5"
            agpVersion.startsWith("9.2") -> "9.3"
            agpVersion.startsWith("9.1") -> "9.1"
            agpVersion.startsWith("9.0") -> "9.0"
            agpVersion.startsWith("8.7") -> "8.9"
            agpVersion.startsWith("8.6") -> "8.8"
            agpVersion.startsWith("8.5") -> "8.7"
            agpVersion.startsWith("8.4") -> "8.6"
            agpVersion.startsWith("8.3") -> "8.4"
            agpVersion.startsWith("8.2") -> "8.2"
            else -> "8.0"
        }
    }

    private fun getMajorMinor(version: String): String {
        val parts = version.split(".")
        return "${parts[0]}.${parts.getOrElse(1) { "0" }}"
    }

    private fun parseVersion(version: String): List<Int> {
        return version.split("-")[0]
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = parseVersion(v1)
        val p2 = parseVersion(v2)
        val maxLen = maxOf(p1.size, p2.size)
        val padded1 = p1 + List(maxLen - p1.size) { 0 }
        val padded2 = p2 + List(maxLen - p2.size) { 0 }
        for (i in 0 until maxLen) {
            if (padded1[i] != padded2[i]) {
                return padded1[i].compareTo(padded2[i])
            }
        }
        return 0
    }
}

class VersionIncompatibleException(message: String) : Exception(message)
```

---

## 第六部分：升级实施计划

### 6.1 第一阶段：DTO 模型映射完善 (2-3 周)

#### 任务 1.1：完善 DefaultVariant
- [ ] 更新 `DefaultVariant.kt` 实现完整的 v2 Variant 接口
- [ ] 实现 `testSuiteArtifacts` 映射
- [ ] 实现 `experimentalProperties` 映射
- [ ] 添加工厂方法 `fromVariant()`

#### 任务 1.2：完善 DefaultAndroidArtifact
- [ ] 更新 `DefaultAndroidArtifact.kt` 实现完整的 v2 AndroidArtifact 接口
- [ ] 实现 `AbstractArtifact` 的所有方法
- [ ] 实现 `SourceProvider` 映射
- [ ] 实现 `ArtifactDependencies` 映射

#### 任务 1.3：实现 DefaultProjectInfo
- [ ] 创建 `DefaultProjectInfo.kt`
- [ ] 实现完整的 v2 ProjectInfo 接口
- [ ] 实现 `DefaultPluginIdentifier` 辅助类

#### 任务 1.4：完善 DefaultArtifactDependencies
- [ ] 更新 `DefaultArtifactDependencies.kt` 实现完整的 v2 接口
- [ ] 实现 `GraphItem` 映射
- [ ] 实现 `ArtifactDependenciesAdjacencyList` 映射

#### 任务 1.5：其他 DTO 类
- [ ] 创建 `DefaultVersions.kt` 实现 Versions 接口
- [ ] 创建 `DefaultTestSuiteArtifact.kt`
- [ ] 更新 `DefaultSourceProvider.kt`
- [ ] 更新 `DefaultSigningConfig.kt`

### 6.2 第二阶段：AGP v2 API 集成 (2-3 周)

#### 任务 2.1：更新 AndroidProjectModelBuilder
- [ ] 添加 `ProjectGraph` 获取逻辑
- [ ] 添加 `Versions` 获取逻辑
- [ ] 实现 v2 模型映射
- [ ] 实现回退方案

#### 任务 2.2：更新 RootModelBuilder
- [ ] 支持 `ProjectGraph` 遍历
- [ ] 更新项目依赖解析

#### 任务 2.3：更新 AbstractModelBuilder
- [ ] 添加工具方法
- [ ] 统一版本检测逻辑

### 6.3 第三阶段：版本兼容性 (1-2 周)

#### 任务 3.1：版本检查器
- [ ] 实现 `AgpGradleVersionChecker`
- [ ] 集成到构建流程
- [ ] 提供友好的错误提示

#### 任务 3.2：插件检测
- [ ] 更新 `ProjectTypeDetector`
- [ ] 支持 AGP 9.x 插件语法
- [ ] 支持 Gradle 9.x 项目结构

### 6.4 第四阶段：测试与验证 (2-3 周)

#### 任务 4.1：单元测试
- [ ] DTO 映射测试
- [ ] 版本检查测试
- [ ] 插件检测测试

#### 任务 4.2：集成测试
- [ ] AGP 9.x 项目同步测试
- [ ] AGP 8.x 向后兼容测试
- [ ] Gradle 9.x 兼容性测试

#### 任务 4.3：端到端测试
- [ ] 新项目创建测试
- [ ] 现有项目同步测试
- [ ] 构建执行测试

---

## 第七部分：测试矩阵

| AGP 版本 | Gradle 版本 | 预期结果 | 优先级 |
|---------|------------|---------|--------|
| 9.3.0 | 9.5 | ✅ 完整支持 v2 API | P0 |
| 9.2.0 | 9.3 | ✅ 完整支持 v2 API | P0 |
| 9.1.0 | 9.1 | ✅ 完整支持 v2 API | P0 |
| 9.0.0 | 9.0 | ✅ 完整支持 v2 API | P0 |
| 8.7.0 | 8.9 | ✅ 完整支持 v1 API | P1 |
| 8.5.0 | 8.7 | ✅ 完整支持 v1 API | P1 |
| 8.3.0 | 8.4 | ✅ 完整支持 v1 API | P2 |
| 8.2.0 | 8.2 | ✅ 完整支持 v1 API | P2 |

---

## 第八部分：风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| AGP v2 API 不稳定 | 项目同步失败 | 中 | 保持 v1 API 回退方案 |
| DTO 映射不完整 | 数据丢失 | 高 | 完善测试覆盖 |
| 版本检测错误 | 兼容性问题 | 中 | 详细版本检测日志 |
| Gradle 9.x 破坏性变更 | 服务启动失败 | 高 | 保持 Gradle 8.x 兼容模式 |

---

## 附录 A：文件清单

### DTO 模型文件

```
tooling/builder-model-impl/src/main/java/com/itsaky/androidide/builder/model/
├── DefaultVariant.kt                  ← 需要升级
├── DefaultAndroidArtifact.kt         ← 需要升级
├── DefaultProjectInfo.kt            ← 需要创建
├── DefaultArtifactDependencies.kt    ← 需要升级
├── DefaultGraphItem.kt              ← 已有，需检查
├── DefaultVersions.kt               ← 需要创建
├── DefaultTestSuiteArtifact.kt       ← 需要创建
├── DefaultPluginIdentifier.kt        ← 需要创建
├── DefaultSourceProvider.kt        ← 需要升级
├── DefaultSigningConfig.kt          ← 需要升级
├── DefaultBuildType.kt              ← 需要升级
├── DefaultProductFlavor.kt          ← 需要升级
└── DefaultAndroidGradlePluginProjectFlags.kt ← 需要升级
```

### 模型构建器文件

```
tooling/impl/src/main/java/com/itsaky/androidide/tooling/impl/sync/
├── AndroidProjectModelBuilder.kt    ← 需要升级
├── RootModelBuilder.kt              ← 需要升级
├── AbstractModelBuilder.kt         ← 需要升级
└── GradleProjectModelBuilder.kt    ← 需要检查
```

---

**文档版本**: 2.0
**创建日期**: 2026-06-06
**维护者**: AndroidIDE Team
